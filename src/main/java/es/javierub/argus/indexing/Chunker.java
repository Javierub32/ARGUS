package es.javierub.argus.indexing;

import es.javierub.argus.dto.CodeChunk;
import es.javierub.argus.dto.IndexedFile;

import java.util.List;

public interface Chunker {
    List<CodeChunk> chunk(IndexedFile file, String content);
}
