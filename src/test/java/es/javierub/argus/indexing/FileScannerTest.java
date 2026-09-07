package es.javierub.argus.indexing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests project-file traversal, filtering, and ordering.
 */
class FileScannerTest {

    /** Scanner configured with a text-content detector. */
    private final FileScanner scanner = new FileScanner(
            new FileContentDetector(10 * 1024 * 1024)
    );

    /** Verifies nested traversal and deterministic relative ordering. */
    @Test
    void scansNestedReadableFilesInSortedOrder(@TempDir Path temporaryDirectory)
            throws IOException {
        Path zFile = temporaryDirectory.resolve("z.txt");
        Path aFile = temporaryDirectory.resolve("src").resolve("a.java");
        Files.createDirectories(aFile.getParent());
        Files.writeString(zFile, "z", StandardCharsets.UTF_8);
        Files.writeString(aFile, "class A {}", StandardCharsets.UTF_8);

        List<Path> files = scanner.scan(temporaryDirectory);

        assertEquals(
                List.of("src/a.java", "z.txt"),
                files.stream()
                        .map(file -> temporaryDirectory.relativize(file)
                                .toString()
                                .replace('\\', '/'))
                        .toList()
        );
    }

    /** Verifies that configured directories and files are skipped. */
    @Test
    void ignoresConfiguredDirectoriesAndRepomixFile(@TempDir Path temporaryDirectory)
            throws IOException {
        Files.writeString(
                temporaryDirectory.resolve("included.txt"),
                "included",
                StandardCharsets.UTF_8
        );
        Files.writeString(
                temporaryDirectory.resolve("repomix-output.xml"),
                "ignored",
                StandardCharsets.UTF_8
        );

        for (String ignoredDirectory : List.of(
                ".git", "target", "node_modules", "build", "dist", ".idea", "tmp", "memory"
        )) {
            Path ignoredFile = temporaryDirectory
                    .resolve(ignoredDirectory)
                    .resolve("ignored.txt");
            Files.createDirectories(ignoredFile.getParent());
            Files.writeString(ignoredFile, "ignored", StandardCharsets.UTF_8);
        }

        List<Path> files = scanner.scan(temporaryDirectory);

        assertEquals(1, files.size());
        assertEquals("included.txt", temporaryDirectory.relativize(files.getFirst())
                .toString()
                .replace('\\', '/'));
    }

    /** Verifies that binary files and invalid UTF-8 text are skipped. */
    @Test
    void ignoresBinaryAndInvalidUtf8Files(@TempDir Path temporaryDirectory)
            throws IOException {
        Files.write(
                temporaryDirectory.resolve("image.png"),
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}
        );
        Files.write(
                temporaryDirectory.resolve("invalid.txt"),
                new byte[]{0x48, 0x69, (byte) 0xC3, 0x28}
        );

        assertEquals(List.of(), scanner.scan(temporaryDirectory));
    }

    /** Verifies that empty files are considered indexable. */
    @Test
    void acceptsEmptyFiles(@TempDir Path temporaryDirectory) throws IOException {
        Path emptyFile = temporaryDirectory.resolve("empty.txt");
        Files.createFile(emptyFile);

        assertEquals(List.of(emptyFile), scanner.scan(temporaryDirectory));
    }

}
