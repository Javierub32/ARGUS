package es.javierub.argus.indexing;

import es.javierub.argus.dto.CodeChunk;
import es.javierub.argus.entity.IndexedFileEntity;
import io.github.treesitter.jtreesitter.*;
import lombok.AllArgsConstructor;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Strategy reserved for splitting code using Tree-sitter.
 *
 * <p>The current implementation is an extension point and does not yet create
 * chunks; it always returns an empty list.</p>
 */
@Component
@AllArgsConstructor
public class TreeSitterChunker implements Chunker {
    private final TreeSitterLanguageConfigs treeSitterLanguageConfigs;
    private final TreeSitterNativeLibraryLoader treeSitterNativeLibraryLoader;
    private final EmbeddingModel embeddingModel;


    @Override
    public List<CodeChunk> chunk(IndexedFileEntity file, String content) {
        TreeSitterLanguageConfig config = treeSitterLanguageConfigs.findByPath(file.getRelativePath()).orElse(null);

        if (config == null) {
            return List.of();
        }

        Language language = treeSitterNativeLibraryLoader.load(config);

        try (Parser parser = new Parser(language)) {
            try (Tree tree = parser.parse(content, InputEncoding.UTF_8).orElse(null)) {
                if (tree == null) {
                    return null;
                }

                Node root = tree.getRootNode();

                List<ChunkDraft> drafts = extractChunks(file, content, root, config);

                return toCodeChunk(file, drafts, config);

            }
        }

    }

    private List<CodeChunk> toCodeChunk (IndexedFileEntity file, List<ChunkDraft> drafts, TreeSitterLanguageConfig config) {
        List<CodeChunk> chunks = new ArrayList<>();

        for (int i = 0; i < drafts.size(); i++) {
            ChunkDraft draft = drafts.get(i);
            CodeChunk chunk = new CodeChunk();

            chunk.setChunkId(draft.chunkId());
            chunk.setProjectId(file.getProjectId());
            chunk.setFileId(file.getFileId());
            chunk.setRelativePath(file.getRelativePath());
            chunk.setStartLine(draft.startLine());
            chunk.setEndLine(draft.endLine());
            chunk.setChunkIndex(i);
            chunk.setContent(draft.content());
            chunk.setLanguage(config.languageId());
            chunk.setFileSha256(file.getSha256());
            chunk.setEmbedding(embeddingModel.embed(draft.content()));

            chunks.add(chunk);
        }

        return chunks;
    }

    private List<ChunkDraft> extractChunks(IndexedFileEntity file, String content, Node root, TreeSitterLanguageConfig config) {
        // Tree-sitter works with bytes instead of positions
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);

        List<ChunkDraft> drafts = new ArrayList<>();

        visitNode(file, contentBytes, root, drafts, config, "0");

        return drafts;
    }

    private void visitNode(
            IndexedFileEntity file,
            byte[] contentBytes,
            Node node,
            List<ChunkDraft> drafts,
            TreeSitterLanguageConfig config,
            String currentChunkId
    ) {
        boolean generatesChunk = config.chunkNodeTypes().contains(node.getType());

        // Meter esto dentro de un if(generatesChunk) a futuro
        String symbolName = extractSymbolName(node);

        ChunkDraft draft = new ChunkDraft(
                node.getType(),
                symbolName,
                currentChunkId,
                node.getStartByte(),
                node.getEndByte(),
                startLine(node),
                endLine(node),
                sourceSlice(node, contentBytes),
                node.hasError()
        );

        drafts.add(draft);

        for (Node child: node.getNamedChildren()) {
            int counter = 0;
            visitNode(file, contentBytes, child, drafts, config, currentChunkId + counter);
            counter++;
        }
    }

    private String extractSymbolName(Node node) {
        String symbolName = node.getChildByFieldName("name")
                .map(Node::getText)
                .orElse(null);

        if (symbolName == null || symbolName.isBlank()) {
            return node.getType();
        }

        if (node.getType().equals("method_declaration")
                || node.getType().equals("constructor_declaration")) {

            String parameters = node
                    .getChildByFieldName("parameters")
                    .map(Node::getText)
                    .orElse("()");

            return symbolName + parameters;
        }

        return symbolName;
    }

    private int startLine(Node node) {
        return node.getStartPoint().row();
    }

    private int endLine(Node node) {
        var endPoint = node.getEndPoint();

        int result = endPoint.row();

        if (endPoint.column() > 0) {
            result++;
        }

        return Math.max(
                startLine(node) + 1,
                result
        );
    }

    private String sourceSlice(
            Node node,
            byte[] sourceBytes
    ) {
        int start = node.getStartByte();
        int end = node.getEndByte();

        if (start < 0
                || end < start
                || end > sourceBytes.length) {
            throw new IllegalArgumentException(
                    "Rango inválido del nodo"
            );
        }

        return new String(
                sourceBytes,
                start,
                end - start,
                StandardCharsets.UTF_8
        );
    }
}
