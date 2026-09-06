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

@Service
@AllArgsConstructor
public class ProjectIndexService {
    private final FileScanner fileScanner;
    private final IndexedFileRepository indexedFileRepository;
    private final WholeFileChunker wholeFileChunker;
    private final ChunkRepository chunkRepository;

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

            // If file don't exist, it's new, it's indexed
            if (prevFile.isEmpty()) {
                newFiles++;
                reIndexFiles.add(file);
                currentFiles.add(file);
            // If file exist and it's hash has change, it's indexed again
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
