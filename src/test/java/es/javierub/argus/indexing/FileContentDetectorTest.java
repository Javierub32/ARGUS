package es.javierub.argus.indexing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests classification of text, binary, and invalid files.
 */
class FileContentDetectorTest {

    /** Detector configured with a ten-mebibyte limit. */
    private final FileContentDetector detector = new FileContentDetector(10 * 1024 * 1024);

    /** Verifies that UTF-8 text with an unknown extension is accepted. */
    @Test
    void acceptsTextFileWithUnknownExtension(@TempDir Path temporaryDirectory) throws IOException {
        Path sourceFile = temporaryDirectory.resolve("main.rs");
        Files.writeString(sourceFile, "fn main() { println!(\"hola\"); }\n", StandardCharsets.UTF_8);

        assertTrue(detector.isIndexable(sourceFile));
    }

    /** Verifies that UTF-8 text without an extension is accepted. */
    @Test
    void acceptsExtensionlessTextFile(@TempDir Path temporaryDirectory) throws IOException {
        Path makefile = temporaryDirectory.resolve("Makefile");
        Files.writeString(makefile, "build:\n\t./mvnw package\n", StandardCharsets.UTF_8);

        assertTrue(detector.isIndexable(makefile));
    }

    /** Verifies that a PNG signature is rejected even when the file ends in {@code .txt}. */
    @Test
    void rejectsBinaryFileRenamedAsText(@TempDir Path temporaryDirectory) throws IOException {
        Path binaryFile = temporaryDirectory.resolve("image.txt");
        Files.write(binaryFile, new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        });

        assertFalse(detector.isIndexable(binaryFile));
    }

    /** Verifies that byte sequences that are not valid UTF-8 are rejected. */
    @Test
    void rejectsInvalidUtf8(@TempDir Path temporaryDirectory) throws IOException {
        Path invalidText = temporaryDirectory.resolve("invalid.txt");
        Files.write(invalidText, new byte[]{0x48, 0x6F, (byte) 0xC3, 0x28});

        assertFalse(detector.isIndexable(invalidText));
    }

    /** Verifies that a null byte identifies content as non-indexable. */
    @Test
    void rejectsFileContainingNullByte(@TempDir Path temporaryDirectory) throws IOException {
        Path binaryFile = temporaryDirectory.resolve("data.txt");
        Files.write(binaryFile, new byte[]{0x68, 0x69, 0x00, 0x21});

        assertFalse(detector.isIndexable(binaryFile));
    }

    /** Verifies that files exceeding the configured limit are rejected. */
    @Test
    void rejectsFileLargerThanConfiguredLimit(@TempDir Path temporaryDirectory) throws IOException {
        FileContentDetector smallLimitDetector = new FileContentDetector(4);
        Path largeTextFile = temporaryDirectory.resolve("large.txt");
        Files.writeString(largeTextFile, "12345", StandardCharsets.UTF_8);

        assertFalse(smallLimitDetector.isIndexable(largeTextFile));
    }

    /** Verifies that a directory is not considered an indexable file. */
    @Test
    void rejectsDirectories(@TempDir Path temporaryDirectory) throws IOException {
        Path directory = Files.createDirectory(temporaryDirectory.resolve("directory"));

        assertFalse(detector.isIndexable(directory));
    }

    /** Verifies that symbolic links are rejected without following them. */
    @Test
    void rejectsSymbolicLinksWithoutFollowingThem(@TempDir Path temporaryDirectory) throws IOException {
        Path target = temporaryDirectory.resolve("target.txt");
        Files.writeString(target, "texto", StandardCharsets.UTF_8);
        Path link = temporaryDirectory.resolve("link.txt");

        try {
            Files.createSymbolicLink(link, target.getFileName());
        } catch (UnsupportedOperationException | SecurityException exception) {
            return;
        }

        assertFalse(detector.isIndexable(link));
    }

    /** Verifies that the detector requires a positive size limit. */
    @Test
    void rejectsNonPositiveMaximumFileSize() {
        assertThrows(IllegalArgumentException.class, () -> new FileContentDetector(0));
        assertThrows(IllegalArgumentException.class, () -> new FileContentDetector(-1));
    }
}
