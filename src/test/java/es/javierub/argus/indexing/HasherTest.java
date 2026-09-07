package es.javierub.argus.indexing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests SHA-256 calculation for text and files.
 */
class HasherTest {

    /** Known SHA-256 hash for {@code hello}. */
    private static final String HELLO_SHA256 =
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";

    /** Verifies the SHA-256 hash of text. */
    @Test
    void hashesTextWithSha256() throws Exception {
        assertEquals(HELLO_SHA256, Hasher.sha256("hello"));
    }

    /** Verifies the behavior for null and empty text. */
    @Test
    void returnsNullForNullOrEmptyText() throws Exception {
        assertNull(Hasher.sha256((String) null));
        assertNull(Hasher.sha256(""));
    }

    /** Verifies the SHA-256 hash of file content. */
    @Test
    void hashesFileContents(@TempDir Path temporaryDirectory) throws Exception {
        Path file = temporaryDirectory.resolve("hello.txt");
        Files.writeString(file, "hello", StandardCharsets.UTF_8);

        assertEquals(HELLO_SHA256, Hasher.sha256(file));
    }

    /** Verifies that a missing-file error is propagated. */
    @Test
    void propagatesMissingFileErrors(@TempDir Path temporaryDirectory) {
        Path missingFile = temporaryDirectory.resolve("missing.txt");

        assertThrows(IOException.class, () -> Hasher.sha256(missingFile));
    }
}
