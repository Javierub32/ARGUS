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
import lombok.RequiredArgsConstructor;
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

        List<Path> paths = fileScanner.scan(root);
        List<IndexedFile> indexedFiles = new ArrayList<>();

        for (Path path: paths) {
            List<CodeChunk> fileChunks;

            IndexedFile file = new IndexedFile(
                projectId,
                Hasher.sha256(path.toString()),
                Hasher.sha256(path),
                root.relativize(path).toString().replace('\\', '/'),
                0,
                Files.size(path),
                Files.getLastModifiedTime(path).toInstant(),
                Instant.now(),
                0
            );

            String fileContent = Files.readString(path, StandardCharsets.UTF_8);
            long initChunking = System.nanoTime();

            fileChunks = wholeFileChunker.chunk(file, fileContent);
            chunkRepository.addOrReplaceFileChunks(projectId, Hasher.sha256(path.toString()), fileChunks);
            long chunkingMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - initChunking);

            System.out.println(fileChunks);

            file.setChunkCount(fileChunks.size());
            file.setIndexationMs(chunkingMs);
            indexedFiles.add(file);
        }

        indexedFileRepository.replaceProject(projectId, indexedFiles);

        long indexTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - initIndexing);
        long embeddingTime = indexedFiles.stream()
                .map(indexedFile -> indexedFile.getIndexationMs())
                .reduce(0L, (sum, acc) -> sum + acc);

        return new IndexReport(
                root.toString(),
                0,
                0,
                0,
                0,
                indexedFiles.size(),
                indexedFiles.size(),
                indexTime,
                embeddingTime
        );
    }
}
