package es.javierub.argus.service;

import es.javierub.argus.dto.IndexReport;
import es.javierub.argus.indexing.FileScanner;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Service
@AllArgsConstructor
public class ProjectIndexService {
    private final FileScanner fileScanner;

    public IndexReport index(String projectRoot) throws IOException {
        Path root = Paths.get(projectRoot);
        List<Path> files = fileScanner.scan(root);

        for (Path file: files) {
            System.out.println(root.relativize(file));
        }
        return new IndexReport(
                root.toString(),
                0,
                0,
                0,
                0,
                files.size(),
                files.size(),
                0
        );
    }
}
