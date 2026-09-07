package es.javierub.argus.dao;

import es.javierub.argus.dto.CodeChunk;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests persistence and replacement of chunks through Lucene.
 */
class ChunkRepositoryTest {

    /** Temporary directory available to repository tests. */
    @TempDir
    Path temporaryDirectory;

    /** Verifies that chunk fields are stored in Lucene. */
    @Test
    void storesChunkFieldsInLucene(@TempDir Path indexRoot) throws IOException {
        ChunkRepository repository = new ChunkRepository(indexRoot.toString());
        CodeChunk chunk = chunk("chunk-1", "file-1", "content");

        repository.addOrReplaceFileChunks("project-1", "file-1", List.of(chunk));

        List<Document> documents = readDocuments(indexRoot.resolve("project-1"));
        assertEquals(1, documents.size());
        assertEquals("chunk-1", documents.getFirst().get("chunkId"));
        assertEquals("project-1", documents.getFirst().get("projectId"));
        assertEquals("file-1", documents.getFirst().get("fileId"));
        assertEquals("src/Main.java", documents.getFirst().get("relativePath"));
        assertEquals("content", documents.getFirst().get("content"));
        assertEquals("java", documents.getFirst().get("language"));
    }

    /** Verifies that an update replaces all chunks for the file. */
    @Test
    void replacesAllChunksForTheSameFile(@TempDir Path indexRoot) throws IOException {
        ChunkRepository repository = new ChunkRepository(indexRoot.toString());

        repository.addOrReplaceFileChunks(
                "project-1",
                "file-1",
                List.of(chunk("old", "file-1", "old content"))
        );
        repository.addOrReplaceFileChunks(
                "project-1",
                "file-1",
                List.of(
                        chunk("new-1", "file-1", "new content 1"),
                        chunk("new-2", "file-1", "new content 2")
                )
        );

        assertEquals(
                Set.of("new-1", "new-2"),
                new HashSet<>(readDocuments(indexRoot.resolve("project-1"))
                        .stream()
                        .map(document -> document.get("chunkId"))
                        .toList())
        );
    }

    /** Verifies that all chunks for a file can be deleted. */
    @Test
    void deletesChunksForAFile(@TempDir Path indexRoot) throws IOException {
        ChunkRepository repository = new ChunkRepository(indexRoot.toString());
        repository.addOrReplaceFileChunks(
                "project-1",
                "file-1",
                List.of(chunk("chunk-1", "file-1", "content"))
        );

        repository.deleteFileChunks("project-1", "file-1");

        assertEquals(List.of(), readDocuments(indexRoot.resolve("project-1")));
    }

    /** Verifies that chunks without an embedding vector are rejected. */
    @Test
    void rejectsChunksWithoutEmbeddings(@TempDir Path indexRoot) {
        ChunkRepository repository = new ChunkRepository(indexRoot.toString());
        CodeChunk chunk = chunk("chunk-1", "file-1", "content");
        chunk.setEmbedding(null);

        assertThrows(IllegalArgumentException.class, () -> repository
                .addOrReplaceFileChunks("project-1", "file-1", List.of(chunk)));
    }

    /** Verifies that project IDs cannot be used for path traversal. */
    @Test
    void rejectsInvalidProjectIds(@TempDir Path indexRoot) {
        ChunkRepository repository = new ChunkRepository(indexRoot.toString());

        assertThrows(IllegalArgumentException.class, () -> repository
                .addOrReplaceFileChunks("../outside", "file-1", List.of()));
        assertThrows(IllegalArgumentException.class, () -> repository
                .deleteFileChunks("project/1", "file-1"));
    }

    /**
     * Reads all documents stored in a Lucene index.
     *
     * @param indexPath index path to query
     * @return documents found
     * @throws IOException if the index cannot be opened or read
     */
    private List<Document> readDocuments(Path indexPath) throws IOException {
        try (Directory directory = FSDirectory.open(indexPath);
             DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            TopDocs topDocs = searcher.search(new MatchAllDocsQuery(), 100);
            StoredFields storedFields = reader.storedFields();

            return java.util.Arrays.stream(topDocs.scoreDocs)
                    .map(scoreDoc -> {
                        try {
                            return storedFields.document(scoreDoc.doc);
                        } catch (IOException exception) {
                            throw new RuntimeException(exception);
                        }
                    })
                    .toList();
        }
    }

    /**
     * Creates a test chunk with valid metadata and an embedding.
     *
     * @param chunkId chunk identifier
     * @param fileId file identifier
     * @param content chunk content
     * @return chunk ready for persistence
     */
    private CodeChunk chunk(String chunkId, String fileId, String content) {
        return new CodeChunk(
                chunkId,
                "project-1",
                fileId,
                "src/Main.java",
                1,
                2,
                0,
                content,
                "java",
                "file-sha",
                new float[]{0.1f, 0.2f, 0.3f}
        );
    }
}
