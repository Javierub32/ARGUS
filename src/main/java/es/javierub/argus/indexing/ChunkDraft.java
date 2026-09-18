package es.javierub.argus.indexing;


public record ChunkDraft(
        String astNodeType,
        String symbolName,
        String chunkId,
        int startByte,
        int endByte,
        int startLine,
        int endLine,
        String content,
        boolean syntaxError
) {
}
