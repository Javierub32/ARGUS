package es.javierub.argus.indexing;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Traverses a project and returns its indexable text files.
 *
 * <p>The traversal ignores configured directories and files, does not follow
 * symbolic links, and returns paths in deterministic relative order.</p>
 */
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

    /**
     * Recursively scans a project.
     *
     * @param projectRoot project root to traverse
     * @return indexable file paths, ordered by relative path
     * @throws IOException if a directory or file cannot be read
     */
    public List<Path> scan(Path projectRoot) throws IOException {
        Path root = projectRoot.toAbsolutePath().normalize();

        List<Path> files = new ArrayList<>();

        scanDirectory(root, root, files);

        files.sort(Comparator.comparing(file -> root.relativize(file).toString()));

        return List.copyOf(files);
    }

    /**
     * Traverses a directory and adds its indexable files to the collection.
     *
     * @param directory directory currently being traversed
     * @param projectRoot project root, which is never ignored
     * @param files collection that accumulates discovered files
     * @throws IOException if the directory cannot be opened or read
     */
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

    /**
     * Indicates whether a directory is included in the exclusion list.
     *
     * @param directory directory to check
     * @return {@code true} if it should be skipped
     */
    private boolean shouldIgnoreDirectory(Path directory) {
        Path directoryName = directory.getFileName();

        if (directoryName == null) {
            return false;
        }

        String normalizedName = directoryName.toString().toLowerCase(Locale.ROOT);

        return IGNORED_DIRECTORIES.contains(normalizedName);
    }

    /**
     * Indicates whether a file is included in the exclusion list.
     *
     * @param file file to check
     * @return {@code true} if it should be skipped
     */
    private boolean shouldIgnoreFile(Path file) {
        String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);

        return IGNORED_FILES.contains(fileName);
    }

}
