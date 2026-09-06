package es.javierub.argus.indexing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileContentDetectorTest {

    private final FileContentDetector detector = new FileContentDetector(10 * 1024 * 1024);

    @Test
    void acceptsTextFileWithUnknownExtension(@TempDir Path temporaryDirectory) throws IOException {
        Path sourceFile = temporaryDirectory.resolve("main.rs");
        Files.writeString(sourceFile, "fn main() { println!(\"hola\"); }\n", StandardCharsets.UTF_8);

        assertTrue(detector.isIndexable(sourceFile));
    }

    @Test
    void acceptsExtensionlessTextFile(@TempDir Path temporaryDirectory) throws IOException {
        Path makefile = temporaryDirectory.resolve("Makefile");
        Files.writeString(makefile, "build:\n\t./mvnw package\n", StandardCharsets.UTF_8);

        assertTrue(detector.isIndexable(makefile));
    }

    @Test
    void rejectsBinaryFileRenamedAsText(@TempDir Path temporaryDirectory) throws IOException {
        Path binaryFile = temporaryDirectory.resolve("image.txt");
        Files.write(binaryFile, new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        });

        assertFalse(detector.isIndexable(binaryFile));
    }

    @Test
    void rejectsInvalidUtf8(@TempDir Path temporaryDirectory) throws IOException {
        Path invalidText = temporaryDirectory.resolve("invalid.txt");
        Files.write(invalidText, new byte[]{0x48, 0x6F, (byte) 0xC3, 0x28});

        assertFalse(detector.isIndexable(invalidText));
    }

    @Test
    void rejectsFileContainingNullByte(@TempDir Path temporaryDirectory) throws IOException {
        Path binaryFile = temporaryDirectory.resolve("data.txt");
        Files.write(binaryFile, new byte[]{0x68, 0x69, 0x00, 0x21});

        assertFalse(detector.isIndexable(binaryFile));
    }

    @Test
    void rejectsFileLargerThanConfiguredLimit(@TempDir Path temporaryDirectory) throws IOException {
        FileContentDetector smallLimitDetector = new FileContentDetector(4);
        Path largeTextFile = temporaryDirectory.resolve("large.txt");
        Files.writeString(largeTextFile, "12345", StandardCharsets.UTF_8);

        assertFalse(smallLimitDetector.isIndexable(largeTextFile));
    }
}
