package es.javierub.argus.indexing;

import es.javierub.argus.dto.CodeChunk;
import es.javierub.argus.entity.IndexedFileEntity;
import io.github.treesitter.jtreesitter.*;
import lombok.AllArgsConstructor;

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

            }
        }

        return List.of();
    }

    private List<ChunkDraft> extractChunks(IndexedFileEntity file, String content, Node root, TreeSitterLanguageConfig config) {
        // Tree-sitter works with bytes instead of positions
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);

        List<ChunkDraft> drafts = new ArrayList<>();

        return drafts;
    }

    private void visitNode(
            IndexedFileEntity file,
            byte[] contentBytes,
            Node node,
            List<ChunkDraft> drafts,
            TreeSitterLanguageConfig config,
            String parentChunkId,
            String completeSymbolName
    ) {
        boolean generatesChunk = config.chunkNodeTypes().contains(node.getType());

        // Meter todo esto dentro de un if a futuro

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
}
