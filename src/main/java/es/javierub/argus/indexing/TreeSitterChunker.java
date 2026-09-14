package es.javierub.argus.indexing;

import es.javierub.argus.dto.CodeChunk;
import es.javierub.argus.dto.IndexedFile;
import es.javierub.argus.entity.IndexedFileEntity;

import java.util.List;

/**
 * Strategy reserved for splitting code using Tree-sitter.
 *
 * <p>The current implementation is an extension point and does not yet create
 * chunks; it always returns an empty list.</p>
 */
public class TreeSitterChunker implements Chunker {
    /**
     * Returns the content's syntax-aware chunks.
     *
     * @param file source file metadata
     * @param content file's textual content
     * @return an empty list until the strategy is implemented
     */
    @Override
    public List<CodeChunk> chunk(IndexedFileEntity file, String content) {
        return List.of();
    }
}
