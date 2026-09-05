package es.javierub.argus.dao;

import es.javierub.argus.dto.CodeChunk;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
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
import org.apache.lucene.store.FSDirectory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

@Repository
public class ChunkRepository {
    private final Path rootPath;

    // We don't use Lombok because we need a constructor to use the global value
    public ChunkRepository(@Value("${app.chunks-root}") String rootPath) {
        this.rootPath = Path.of(rootPath).toAbsolutePath().normalize();
    }

    public void addOrReplaceFileChunks(String projectId, String fileId, Collection<CodeChunk> chunks) throws IOException {
        Path path = indexPath(projectId);
        Files.createDirectories(path);

        try (Directory directory = FSDirectory.open(path)) {
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

    public void deleteFileChunks(String projectId, String fileId) throws IOException {
        Path path = indexPath(projectId);
        Files.createDirectories(path);

        try (Directory directory = FSDirectory.open(path)) {
            IndexWriterConfig config = new IndexWriterConfig();

            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);

            try (IndexWriter writer = new IndexWriter(directory, config)) {
                writer.deleteDocuments(new Term("fileId", fileId));
                writer.commit();
            }
        }
    }

    private Path indexPath(String projectId) {
        if (!projectId.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("projectId no válido");
        }

        return rootPath.resolve(projectId);
    }

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