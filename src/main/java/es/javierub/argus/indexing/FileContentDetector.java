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

@Component
public class FileContentDetector {

    private static final int SAMPLE_SIZE = 64 * 1024;
    private final long maxFileSizeBytes;

    public FileContentDetector(@Value("${app.indexing.max-file-size-bytes:10485760}") long maxFileSizeBytes) {
        if (maxFileSizeBytes <= 0) {
            throw new IllegalArgumentException("El tamaño máximo debe ser mayor que cero");
        }

        this.maxFileSizeBytes = maxFileSizeBytes;
    }

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

    private byte[] readSample(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            return input.readNBytes(SAMPLE_SIZE);
        }
    }

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

    private boolean isKnownRiffBinary(byte[] bytes) {
        if (!startsWith(bytes, 0x52, 0x49, 0x46, 0x46) || bytes.length < 12) {
            return false;
        }

        return startsWithAt(bytes, 8, 0x57, 0x41, 0x56, 0x45)    // WAV
                || startsWithAt(bytes, 8, 0x57, 0x45, 0x42, 0x50) // WEBP
                || startsWithAt(bytes, 8, 0x41, 0x56, 0x49, 0x20); // AVI
    }

    private boolean containsNullByte(byte[] bytes) {
        for (byte value : bytes) {
            if (value == 0) {
                return true;
            }
        }

        return false;
    }

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


    private boolean startsWith(byte[] bytes, int... signature) {
        return startsWithAt(bytes, 0, signature);
    }

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
