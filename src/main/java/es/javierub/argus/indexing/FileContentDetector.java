package es.javierub.argus.indexing;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/**
 * Determines whether a file can be treated as indexable text content.
 *
 * <p>The detection combines file-system checks, a size limit, known binary
 * signatures, null-byte detection, and UTF-8 validation on a content sample.</p>
 */
@Component
public class FileContentDetector {

    private static final int SAMPLE_SIZE = 64 * 1024;
    private final long maxFileSizeBytes;

    /**
     * Creates a detector with the configured maximum size.
     *
     * @param maxFileSizeBytes maximum allowed size, in bytes
     * @throws IllegalArgumentException if the size is not positive
     */
    public FileContentDetector(@Value("${app.indexing.max-file-size-bytes:10485760}") long maxFileSizeBytes) {
        if (maxFileSizeBytes <= 0) {
            throw new IllegalArgumentException("El tamaño máximo debe ser mayor que cero");
        }

        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    /**
     * Checks whether a file is regular, small enough, and contains recognizable
     * UTF-8 text.
     *
     * @param file file to inspect
     * @return {@code true} if the file can be indexed; {@code false} if it is a
     *         directory, symbolic link, binary file, or exceeds the limit
     * @throws IOException if the file cannot be inspected or read
     */
    public boolean isIndexable(Path file) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }

        if (Files.size(file) > maxFileSizeBytes) {
            return false;
        }

        byte[] sample = readSample(file);

        if (sample.length == 0) {
            return true;
        }

        return !hasKnownBinarySignature(sample)
                && !containsNullByte(sample)
                && isValidUtf8(sample);
    }

    /**
     * Reads at most {@link #SAMPLE_SIZE} bytes from the beginning of the file.
     *
     * @param file file from which the sample will be read
     * @return bytes read
     * @throws IOException if the file cannot be opened or read
     */
    private byte[] readSample(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            return input.readNBytes(SAMPLE_SIZE);
        }
    }

    /**
     * Checks for common binary signatures at the beginning of the sample.
     *
     * @param bytes file byte sample
     * @return {@code true} if a binary signature is recognized
     */
    private boolean hasKnownBinarySignature(byte[] bytes) {
        return startsWith(bytes, 0x89, 0x50, 0x4E, 0x47)       // PNG
                || startsWith(bytes, 0xFF, 0xD8, 0xFF)         // JPEG
                || startsWith(bytes, 0x47, 0x49, 0x46, 0x38)   // GIF
                || startsWith(bytes, 0x25, 0x50, 0x44, 0x46)   // PDF
                || startsWith(bytes, 0x50, 0x4B, 0x03, 0x04)   // ZIP/JAR
                || startsWith(bytes, 0x50, 0x4B, 0x05, 0x06)   // Empty ZIP
                || startsWith(bytes, 0x50, 0x4B, 0x07, 0x08)   // Spanned ZIP
                || startsWith(bytes, 0xCA, 0xFE, 0xBA, 0xBE)   // Java class
                || startsWith(bytes, 0x7F, 0x45, 0x4C, 0x46)   // ELF
                || startsWith(bytes, 0x4D, 0x5A)                // Windows executable
                || startsWith(bytes, 0x1F, 0x8B)                // GZIP
                || startsWith(bytes, 0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C) // 7z
                || startsWith(bytes, 0x52, 0x61, 0x72, 0x21, 0x1A, 0x07) // RAR
                || startsWith(bytes, 0x53, 0x51, 0x4C, 0x69, 0x74, 0x65, 0x20, 0x66) // SQLite
                || isKnownRiffBinary(bytes);
    }

    /**
     * Checks binary RIFF formats by identifying their subtype.
     *
     * @param bytes file byte sample
     * @return {@code true} if the sample represents WAV, WEBP, or AVI
     */
    private boolean isKnownRiffBinary(byte[] bytes) {
        if (!startsWith(bytes, 0x52, 0x49, 0x46, 0x46) || bytes.length < 12) {
            return false;
        }

        return startsWithAt(bytes, 8, 0x57, 0x41, 0x56, 0x45)    // WAV
                || startsWithAt(bytes, 8, 0x57, 0x45, 0x42, 0x50) // WEBP
                || startsWithAt(bytes, 8, 0x41, 0x56, 0x49, 0x20); // AVI
    }

    /**
     * Looks for null bytes, which are common in binary content.
     *
     * @param bytes sample to inspect
     * @return {@code true} if the sample contains at least one null byte
     */
    private boolean containsNullByte(byte[] bytes) {
        for (byte value : bytes) {
            if (value == 0) {
                return true;
            }
        }

        return false;
    }

    /**
     * Validates the sample as strict UTF-8.
     *
     * <p>When the sample reaches its maximum size, its final three bytes are
     * omitted so a multibyte character truncated at the sample boundary is not
     * rejected.</p>
     *
     * @param bytes sample to validate
     * @return {@code true} if the sample is valid UTF-8
     */
    private boolean isValidUtf8(byte[] bytes) {
        int lengthToValidate = bytes.length == SAMPLE_SIZE
                ? Math.max(0, bytes.length - 3)
                : bytes.length;

        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, 0, lengthToValidate));
            return true;
        } catch (CharacterCodingException exception) {
            return false;
        }
    }


    /**
     * Checks whether a sample starts with a specific signature.
     *
     * @param bytes bytes to inspect
     * @param signature unsigned values of the expected signature
     * @return {@code true} if the signature starts at position zero
     */
    private boolean startsWith(byte[] bytes, int... signature) {
        return startsWithAt(bytes, 0, signature);
    }

    /**
     * Checks whether a sample contains a signature at a specific position.
     *
     * @param bytes bytes to inspect
     * @param offset signature start position
     * @param signature unsigned values of the expected signature
     * @return {@code true} if the signature fits and matches from {@code offset}
     */
    private boolean startsWithAt(byte[] bytes, int offset, int... signature) {
        if (offset < 0 || bytes.length - offset < signature.length) {
            return false;
        }

        for (int i = 0; i < signature.length; i++) {
            if (Byte.toUnsignedInt(bytes[offset + i]) != signature[i]) {
                return false;
            }
        }

        return true;
    }
}
