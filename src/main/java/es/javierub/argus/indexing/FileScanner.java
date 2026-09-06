package es.javierub.argus.indexing;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Component
@AllArgsConstructor
public class FileScanner {
    private final FileContentDetector fileContentDetector;

    private static final Set<String> IGNORED_DIRECTORIES = Set.of(
            ".git",
            "target",
            "node_modules",
            "build",
            "dist",
            ".idea",
            "tmp",
            "memory"
    );

    private static final Set<String> IGNORED_FILES = Set.of(
            "repomix-output.xml"
    );

    public List<Path> scan(Path projectRoot) throws IOException {
        Path root = projectRoot.toAbsolutePath().normalize();

        List<Path> files = new ArrayList<>();

        scanDirectory(root, root, files);

        files.sort(Comparator.comparing(file -> root.relativize(file).toString()));

        return List.copyOf(files);
    }

    private void scanDirectory(Path directory, Path projectRoot, List<Path> files)
            throws IOException {

        if (!directory.equals(projectRoot) && shouldIgnoreDirectory(directory)) {
            return;
        }

        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {

            for (Path entry : entries) {

                if (Files.isSymbolicLink(entry)) {
                    continue;
                }

                if (Files.isDirectory(entry)) {
                    scanDirectory(entry, projectRoot, files);
                    continue;
                }

                if (Files.isRegularFile(entry)
                        && !shouldIgnoreFile(entry)
                        && fileContentDetector.isIndexable(entry)) {
                    files.add(entry);
                }
            }
        }
    }

    private boolean shouldIgnoreDirectory(Path directory) {
        Path directoryName = directory.getFileName();

        if (directoryName == null) {
            return false;
        }

        String normalizedName = directoryName.toString().toLowerCase(Locale.ROOT);

        return IGNORED_DIRECTORIES.contains(normalizedName);
    }

    private boolean shouldIgnoreFile(Path file) {
        String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);

        return IGNORED_FILES.contains(fileName);
    }

}
