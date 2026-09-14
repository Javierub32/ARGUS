package es.javierub.argus.indexing;

import es.javierub.argus.dto.CodeChunk;
import es.javierub.argus.dto.IndexedFile;
import es.javierub.argus.entity.IndexedFileEntity;

import java.util.List;

/**
 * Strategy for splitting file content into indexable chunks.
 */
public interface Chunker {
    /**
     * Splits a file's content.
     *
     * @param file source file metadata
     * @param content complete textual content of the file
     * @return chunks generated for the file
     */
    List<CodeChunk> chunk(IndexedFileEntity file, String content);
}
