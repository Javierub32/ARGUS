package es.javierub.argus.service;

import es.javierub.argus.dao.ChunkRepository;
import es.javierub.argus.dao.IndexedFileRepository;
import es.javierub.argus.dto.CodeChunk;
import es.javierub.argus.dto.IndexReport;
import es.javierub.argus.dto.IndexedFile;
import es.javierub.argus.indexing.FileScanner;
import es.javierub.argus.indexing.Hasher;
import es.javierub.argus.indexing.WholeFileChunker;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Coordinates the incremental indexing of a project.
 *
 * <p>The service scans project files, compares their identifiers and hashes
 * with metadata from the previous indexing operation, and only processes new
 * or modified files. Deleted files are also removed from the chunk index.</p>
 *
 * <p>For each file that must be processed, {@link WholeFileChunker} generates
 * its chunks and embeddings. The chunks are stored through
 * {@link ChunkRepository}, and file metadata is updated through
 * {@link IndexedFileRepository}.</p>
 *
 * @see FileScanner
 * @see IndexReport
 */
@Service
@AllArgsConstructor
public class ProjectIndexService {
    private final FileScanner fileScanner;
    private final IndexedFileRepository indexedFileRepository;
    private final WholeFileChunker wholeFileChunker;
    private final ChunkRepository chunkRepository;

    /**
     * Indexes a project incrementally.
     *
     * <p>The first execution processes all files accepted by
     * {@link FileScanner}. On subsequent executions, each file's content is
     * compared using SHA-256 to avoid reprocessing unchanged files. New and
     * modified files are split into chunks, their embeddings are generated, and
     * the chunks are stored in the index. Files that no longer exist are
     * removed from the index.</p>
     *
     * @param projectRoot path to the root directory of the project to index
     * @return report containing detected files, processed chunks, and indexing
     *         durations
     * @throws IOException if the project cannot be scanned, a file cannot be
     *         read, or the index cannot be updated
     * @throws NoSuchAlgorithmException if SHA-256 is not available in the JVM
     */
    public IndexReport index(String projectRoot) throws IOException, NoSuchAlgorithmException {
        Path root = Paths.get(projectRoot).toAbsolutePath().normalize();
        String projectId = Hasher.sha256(root.toString().replace('\\', '/'));

        long initIndexing = System.nanoTime();

        int newFiles = 0;
        int editedFiles = 0;

        List<Path> paths = fileScanner.scan(root);
        List<IndexedFile> prevFiles = indexedFileRepository.findByProjectId(projectId);

        List<IndexedFile> reIndexFiles = new ArrayList<>();
        List<IndexedFile> currentFiles = new ArrayList<>();
        List<IndexedFile> deletedFiles;

        for (Path path: paths) {
            IndexedFile file = createIndexedFile(root, projectId, path);

            Optional<IndexedFile> prevFile = prevFiles.stream()
                    .filter(f -> f.getFileId().equals(file.getFileId())).findFirst();

            // If the file does not exist, it is new and must be indexed.
            if (prevFile.isEmpty()) {
                newFiles++;
                reIndexFiles.add(file);
                currentFiles.add(file);
            // If the file exists but its hash has changed, index it again.
            } else if (prevFile.isPresent() && !prevFile.get().getSha256().equals(file.getSha256())){
                editedFiles++;
                reIndexFiles.add(file);
                currentFiles.add(file);
            } else {
                currentFiles.add(prevFile.get());
            }
        }

        List<String> currentFilesIds = currentFiles.stream().map(file -> file.getFileId()).toList();
        deletedFiles = prevFiles.stream().filter(file -> !currentFilesIds.contains(file.getFileId())).toList();

        for (IndexedFile file: reIndexFiles) {
            List<CodeChunk> fileChunks;
            String fileContent = Files.readString(file.getPath(), StandardCharsets.UTF_8);
            long initChunking = System.nanoTime();

            fileChunks = wholeFileChunker.chunk(file, fileContent);
            chunkRepository.addOrReplaceFileChunks(projectId, file.getFileId(), fileChunks);
            long chunkingMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - initChunking);

            file.setChunkCount(fileChunks.size());
            file.setIndexationMs(chunkingMs);
        }

        for (IndexedFile file: deletedFiles) {
            chunkRepository.deleteFileChunks(projectId, file.getFileId());
        }

        indexedFileRepository.replaceProject(projectId, currentFiles);

        long indexTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - initIndexing);
        long embeddingTime = reIndexFiles.stream()
                .map(file -> file.getIndexationMs())
                .reduce(0L, (acc, time) ->  acc + time);

        int processedChunks = reIndexFiles.stream()
                .map(file -> file.getChunkCount())
                .reduce(0, (acc, chunks) -> acc + chunks);

        return new IndexReport(
                root.toString(),
                newFiles,
                editedFiles,
                deletedFiles.size(),
                currentFiles.size() - newFiles - editedFiles,
                newFiles + editedFiles,
                processedChunks,
                indexTime,
                embeddingTime
        );
    }

    /**
     * Creates an {@link IndexedFile} instance for a project file.
     *
     * <p>The file identifier is derived from the hash of its path, while the
     * SHA-256 hash is calculated from its content.</p>
     *
     * @param root normalized project root
     * @param projectId identifier of the project to which the file belongs
     * @param path absolute file path
     * @return file metadata ready for indexing
     * @throws IOException if the file or its metadata cannot be read
     * @throws NoSuchAlgorithmException if SHA-256 is not available in the JVM
     */
    private IndexedFile createIndexedFile(Path root, String projectId, Path path) throws IOException, NoSuchAlgorithmException {
        String fileId = Hasher.sha256(path.toString());
        String fileHash256 = Hasher.sha256(path);
        String relativeRoute = root.relativize(path).toString().replace('\\', '/');
        long fileSize = Files.size(path);
        Instant modifiedAt = Files.getLastModifiedTime(path).toInstant();
        Instant indexedAt = Instant.now();

        return new IndexedFile(
                projectId,
                fileId,
                fileHash256,
                path,
                relativeRoute,
                0,
                fileSize,
                modifiedAt,
                indexedAt,
                0
        );

    }
}
