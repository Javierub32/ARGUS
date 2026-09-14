package es.javierub.argus.indexing;

import es.javierub.argus.dto.CodeChunk;
import es.javierub.argus.entity.IndexedFileEntity;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests complete-chunk generation, metadata preservation, and language detection.
 */
class WholeFileChunkerTest {

    /** Verifies that the chunk preserves metadata and the generated embedding. */
    @Test
    void createsChunkWithFileMetadataAndEmbedding() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        String content = "class Demo {}\nsecond line\n";
        float[] embedding = new float[]{0.1f, 0.2f, 0.3f};
        when(embeddingModel.embed(content)).thenReturn(embedding);

        WholeFileChunker chunker = new WholeFileChunker(embeddingModel);
        IndexedFileEntity file = indexedFile("src/Demo.java");

        List<CodeChunk> result = chunker.chunk(file, content);

        assertEquals(1, result.size());
        CodeChunk chunk = result.getFirst();
        assertEquals("file-1:0", chunk.getChunkId());
        assertEquals("project-1", chunk.getProjectId());
        assertEquals("file-1", chunk.getFileId());
        assertEquals("src/Demo.java", chunk.getRelativePath());
        assertEquals(0, chunk.getStartLine());
        assertEquals(3, chunk.getEndLine());
        assertEquals(0, chunk.getChunkIndex());
        assertEquals(content, chunk.getContent());
        assertEquals("unknown", chunk.getLanguage());
        assertEquals("file-sha", chunk.getFileSha256());
        assertArrayEquals(embedding, chunk.getEmbedding());
        verify(embeddingModel).embed(content);
    }

    /** Verifies that an empty file is represented with one indexed line. */
    @Test
    void countsAnEmptyFileAsOneIndexedLine() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embed("")).thenReturn(new float[]{1.0f});

        CodeChunk chunk = new WholeFileChunker(embeddingModel)
                .chunk(indexedFile("empty.txt"), "")
                .getFirst();

        assertEquals(1, chunk.getEndLine());
        assertEquals("unknown", chunk.getLanguage());
    }

    /** Verifies JavaScript detection by extension. */
    @Test
    void detectsJavascript() {
        assertEquals("unknown", languageFor("app.jsx"));
        assertEquals("unknown", languageFor("app.mjs"));
    }

    /** Verifies TypeScript and Python detection by extension. */
    @Test
    void detectsTypescriptAndPython() {
        assertEquals("unknown", languageFor("app.tsx"));
        assertEquals("unknown", languageFor("main.py"));
    }

    /** Verifies configuration and documentation format detection. */
    @Test
    void detectsConfigurationAndDocumentationLanguages() {
        assertEquals("unknown", languageFor("application.yml"));
        assertEquals("unknown", languageFor("config.json"));
        assertEquals("unknown", languageFor("README.md"));
        assertEquals("unknown", languageFor("pom.xml"));
    }

    /** Verifies that an unsupported extension produces {@code unknown}. */
    @Test
    void returnsUnknownForUnsupportedExtension() {
        assertEquals("unknown", languageFor("binary.dat"));
    }

    /**
     * Gets the language detected for a test path.
     *
     * @param relativePath relative path whose extension will be analyzed
     * @return detected language
     */
    private String languageFor(String relativePath) {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embed("content")).thenReturn(new float[]{1.0f});

        return new WholeFileChunker(embeddingModel)
                .chunk(indexedFile(relativePath), "content")
                .getFirst()
                .getLanguage();
    }

    /**
     * Creates file metadata for test cases.
     *
     * @param relativePath relative file path
     * @return file with representative metadata
     */
    private IndexedFileEntity indexedFile(String relativePath) {
        return new IndexedFileEntity(
                null,
                "project-1",
                "file-1",
                "file-sha",
                Path.of("project").resolve(relativePath).toString(),
                relativePath,
                0,
                0,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"),
                0
        );
    }
}
