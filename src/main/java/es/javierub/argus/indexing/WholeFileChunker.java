package es.javierub.argus.indexing;

import es.javierub.argus.dto.CodeChunk;
import es.javierub.argus.dto.IndexedFile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class WholeFileChunker implements Chunker {
    @Override
    public List<CodeChunk> chunk(IndexedFile file, String content) {
        CodeChunk chunk = new CodeChunk(
                createChunkId(file),
                file.getProjectId(),
                file.getFileId(),
                file.getRelativePath(),
                0,
                Math.max(1, lineCount(content)),
                0,
                content,
                detectLanguage(file.getRelativePath()),
                file.getSha256()
        );
        return List.of(chunk);
    }

    private String createChunkId(IndexedFile file) {
        return file.getFileId() + ":0";
    }

    private int lineCount(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }

        int lines = 1;

        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                lines++;
            }
        }

        return lines;
    }

    public String detectLanguage(String relativePath) {
        String path = relativePath.toLowerCase(Locale.ROOT);

        if (path.endsWith(".java")) return "java";
        if (path.endsWith(".js")) return "javascript";
        if (path.endsWith(".ts")) return "typescript";
        if (path.endsWith(".py")) return "python";
        if (path.endsWith(".c")) return "c";
        if (path.endsWith(".xml")) return "xml";
        if (path.endsWith(".json")) return "json";
        if (path.endsWith(".yml") || path.endsWith(".yaml")) return "yaml";
        if (path.endsWith(".md")) return "markdown";

        return "unknow";
    }
}
