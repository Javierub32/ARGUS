package es.javierub.argus.indexing;

import java.util.List;

public record TreeSitterLanguageConfig(
        String languageId,
        String libraryName,
        String symbolName,
        List<String> extensions,
        List<String> chunkNodeTypes,
        String grammarVersion,
        int abiVersion
) {
}
