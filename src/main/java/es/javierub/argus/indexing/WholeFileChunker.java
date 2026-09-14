package es.javierub.argus.indexing;

import es.javierub.argus.dto.CodeChunk;
import es.javierub.argus.dto.IndexedFile;
import es.javierub.argus.entity.IndexedFileEntity;
import lombok.AllArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Generates a single chunk containing the complete file.
 *
 * <p>This strategy preserves the file metadata, detects a language from its
 * extension, and requests an embedding vector for the complete content.</p>
 */
@Component
@AllArgsConstructor
public class WholeFileChunker implements Chunker {
    /** Model used to generate the content embedding. */
    private final EmbeddingModel embeddingModel;

    /**
     * Converts a complete file into a single indexable chunk.
     *
     * @param file source file metadata
     * @param content complete textual content of the file
     * @return list containing one chunk and its embedding
     */
    @Override
    public List<CodeChunk> chunk(IndexedFileEntity file, String content) {
        CodeChunk chunk = new CodeChunk(
                createChunkId(file),
                file.getProjectId(),
                file.getFileId(),
                file.getRelativePath(),
                0,
                Math.max(1, lineCount(content)),
                0,
                content,
                detectLanguage(file.getRelativePath()),
                file.getSha256(),
                embeddingModel.embed(content)
        );

        return List.of(chunk);
    }

    /**
     * Creates the identifier for the file's only chunk.
     *
     * @param file source file
     * @return identifier composed of the file ID and zero index
     */
    private String createChunkId(IndexedFileEntity file) {
        return file.getFileId() + ":0";
    }

    /**
     * Counts lines separated by line breaks.
     *
     * @param content content to count
     * @return zero for null or empty content; otherwise, one more than the
     *         number of line breaks
     */
    private int lineCount(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }

        int lines = 1;

        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                lines++;
            }
        }

        return lines;
    }

    /**
     * Detects the language from the extension of a relative path.
     *
     * @param relativePath file path
     * @return normalized language identifier, or {@code unknown} if the
     *         extension is not recognized
     */
    private String detectLanguage(String relativePath) {
        String path = relativePath.toLowerCase(Locale.ROOT);

        // To-do with Tree-sitter
        return "unknown";
    }
}
