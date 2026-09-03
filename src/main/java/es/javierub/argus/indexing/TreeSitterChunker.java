package es.javierub.argus.indexing;

import es.javierub.argus.dto.CodeChunk;
import es.javierub.argus.dto.IndexedFile;

import java.util.List;

public class TreeSitterChunker implements Chunker {
    @Override
    public List<CodeChunk> chunk(IndexedFile file, String content) {
        return List.of();
    }
}
