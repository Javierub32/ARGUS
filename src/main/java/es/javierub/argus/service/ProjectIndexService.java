package es.javierub.argus.service;

import es.javierub.argus.dao.IndexedFileRepository;
import es.javierub.argus.dto.IndexReport;
import es.javierub.argus.dto.IndexedFile;
import es.javierub.argus.indexing.FileScanner;
import es.javierub.argus.indexing.Hasher;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class ProjectIndexService {
    private final FileScanner fileScanner;
    private final IndexedFileRepository indexedFileRepository;

    public IndexReport index(String projectRoot) throws IOException, NoSuchAlgorithmException {
        Path root = Paths.get(projectRoot).toAbsolutePath().normalize();
        String projectId = Hasher.sha256(root.toString().replace('\\', '/'));

        List<Path> files = fileScanner.scan(root);
        List<IndexedFile> indexedFiles = new ArrayList<>();

        for (Path file: files) {
            IndexedFile f = new IndexedFile(
                projectId,
                Hasher.sha256(file.toString()),
                Hasher.sha256(file),
                root.relativize(file).toString().replace('\\', '/'),
                0,
                Files.size(file),
                Files.getLastModifiedTime(file).toInstant(),
                Instant.now(),
                0
            );
            indexedFiles.add(f);
        }

        indexedFileRepository.replaceProject(projectId, indexedFiles);

        return new IndexReport(
                root.toString(),
                0,
                0,
                0,
                0,
                indexedFiles.size(),
                files.size(),
                0
        );
    }
}
