package es.javierub.argus.dao;

import es.javierub.argus.dto.CodeChunk;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NIOFSDirectory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

/**
 * Persists code chunks in Apache Lucene vector indexes.
 *
 * <p>Each project has an independent index under the path configured by
 * {@code app.chunks-root}. When a file is updated, its previous chunks are
 * removed first to prevent stale documents.</p>
 */
@Repository
public class ChunkRepository {
    private final Path rootPath;

    /**
     * Creates the repository using the root path configured by Spring.
     *
     * @param rootPath root path where indexes will be stored
     */
    public ChunkRepository(@Value("${app.chunks-root}") String rootPath) {
        this.rootPath = Path.of(rootPath).toAbsolutePath().normalize();
    }

    /**
     * Replaces all chunks for a file in the project's index.
     *
     * @param projectId safe project identifier
     * @param fileId identifier of the file whose chunks will be replaced
     * @param chunks new chunks to store
     * @throws IOException if Lucene cannot open, update, or close the index
     */
    public void addOrReplaceFileChunks(String projectId, String fileId, Collection<CodeChunk> chunks) throws IOException {
        Path path = indexPath(projectId);
        Files.createDirectories(path);

        try (Directory directory = new NIOFSDirectory(path)) {
            IndexWriterConfig config = new IndexWriterConfig();

            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);

            try (IndexWriter writer = new IndexWriter(directory, config)) {

                writer.deleteDocuments(new Term("fileId", fileId));

                for (CodeChunk chunk : chunks) {
                    writer.updateDocument(new Term("chunkId", chunk.getChunkId()), toDocument(chunk));
                }

                writer.commit();
            }
        }
    }

    /**
     * Deletes all chunks belonging to a file from the index.
     *
     * @param projectId safe project identifier
     * @param fileId identifier of the file to remove
     * @throws IOException if Lucene cannot open or update the index
     */
    public void deleteFileChunks(String projectId, String fileId) throws IOException {
        Path path = indexPath(projectId);
        Files.createDirectories(path);

        try (Directory directory = new NIOFSDirectory(path)) {
            IndexWriterConfig config = new IndexWriterConfig();

            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);

            try (IndexWriter writer = new IndexWriter(directory, config)) {
                writer.deleteDocuments(new Term("fileId", fileId));
                writer.commit();
            }
        }
    }

    /**
     * Resolves a project's index directory after validating its ID.
     *
     * @param projectId project identifier
     * @return path to the project's Lucene index
     * @throws IllegalArgumentException if the ID could escape the repository's
     *                                  root directory
     */
    private Path indexPath(String projectId) {
        if (!projectId.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("projectId no válido");
        }

        return rootPath.resolve(projectId);
    }

    /**
     * Converts a domain chunk into a document stored by Lucene.
     *
     * @param chunk chunk to serialize
     * @return document with stored metadata and its embedding vector
     * @throws IllegalArgumentException if the chunk does not contain an embedding
     */
    private Document toDocument(CodeChunk chunk) {
        if (chunk.getEmbedding() == null ||
                chunk.getEmbedding().length == 0) {
            throw new IllegalArgumentException(
                    "El chunk no tiene embedding"
            );
        }

        Document document = new Document();

        document.add(new StringField(
                "chunkId",
                chunk.getChunkId(),
                Field.Store.YES
        ));

        document.add(new StringField(
                "projectId",
                chunk.getProjectId(),
                Field.Store.YES
        ));

        document.add(new StringField(
                "fileId",
                chunk.getFileId(),
                Field.Store.YES
        ));

        document.add(new StoredField(
                "relativePath",
                chunk.getRelativePath()
        ));

        document.add(new StoredField(
                "startLine",
                chunk.getStartLine()
        ));

        document.add(new StoredField(
                "endLine",
                chunk.getEndLine()
        ));

        document.add(new StoredField(
                "chunkIndex",
                chunk.getChunkIndex()
        ));

        document.add(new StoredField(
                "content",
                chunk.getContent()
        ));

        document.add(new StoredField(
                "language",
                chunk.getLanguage()
        ));

        document.add(new StoredField(
                "fileSha256",
                chunk.getFileSha256()
        ));

        document.add(new KnnFloatVectorField(
                "embedding",
                chunk.getEmbedding(),
                VectorSimilarityFunction.COSINE
        ));

        return document;
    }


}