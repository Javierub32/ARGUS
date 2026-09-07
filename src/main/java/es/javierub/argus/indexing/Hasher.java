package es.javierub.argus.indexing;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Utility for calculating SHA-256 hashes of files and text.
 *
 * <p>Results are returned as lowercase hexadecimal strings.</p>
 */
public class Hasher {
    /**
     * Calculates the SHA-256 hash of a file's content using block-based reading.
     *
     * @param file file to read
     * @return SHA-256 hash in hexadecimal format
     * @throws IOException if the file cannot be opened or read
     * @throws NoSuchAlgorithmException if SHA-256 is not available in the JVM
     */
    public static String sha256(Path file)
            throws IOException, NoSuchAlgorithmException {

        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int leidos;

            while ((leidos = input.read(buffer)) != -1) {
                digest.update(buffer, 0, leidos);
            }
        }

        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Calculates the SHA-256 hash of text encoded as UTF-8.
     *
     * @param text text to hash
     * @return SHA-256 hash in hexadecimal format, or {@code null} for null or
     *         empty text
     * @throws NoSuchAlgorithmException if SHA-256 is not available in the JVM
     */
    public static String sha256(String text) throws NoSuchAlgorithmException {
        if (text == null || text.isEmpty()) {
            return null;
        }

        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);

        return HexFormat.of().formatHex(digest.digest(bytes));
    }
}
