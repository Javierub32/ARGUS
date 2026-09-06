package es.javierub.argus.indexing;

import es.javierub.argus.dto.CodeChunk;
import es.javierub.argus.dto.IndexedFile;
import lombok.AllArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
@AllArgsConstructor
public class WholeFileChunker implements Chunker {
    private final EmbeddingModel embeddingModel;

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
                file.getSha256(),
                embeddingModel.embed(content)
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

    private String detectLanguage(String relativePath) {
        String path = relativePath.toLowerCase(Locale.ROOT);

        // Java
        if (path.endsWith(".java")) return "java";

        // JavaScript / TypeScript
        if (path.endsWith(".js")
                || path.endsWith(".jsx")
                || path.endsWith(".mjs")
                || path.endsWith(".cjs")) {
            return "javascript";
        }

        if (path.endsWith(".ts")
                || path.endsWith(".tsx")) {
            return "typescript";
        }

        // Python
        if (path.endsWith(".py")
                || path.endsWith(".pyi")) {
            return "python";
        }

        // Web
        if (path.endsWith(".html")
                || path.endsWith(".htm")) {
            return "html";
        }

        if (path.endsWith(".css")
                || path.endsWith(".scss")
                || path.endsWith(".sass")
                || path.endsWith(".less")) {
            return "css";
        }

        // Configuración
        if (path.endsWith(".json")
                || path.endsWith(".jsonc")) {
            return "json";
        }

        if (path.endsWith(".yml")
                || path.endsWith(".yaml")) {
            return "yaml";
        }

        if (path.endsWith(".xml")) return "xml";
        if (path.endsWith(".toml")) return "toml";

        if (path.endsWith(".properties")
                || path.endsWith(".ini")
                || path.endsWith(".cfg")
                || path.endsWith(".conf")) {
            return "config";
        }

        // Documentación
        if (path.endsWith(".md")
                || path.endsWith(".mdx")) {
            return "markdown";
        }

        if (path.endsWith(".rst")
                || path.endsWith(".adoc")) {
            return "documentation";
        }

        if (path.endsWith(".txt")) return "text";

        // SQL y APIs
        if (path.endsWith(".sql")) return "sql";

        if (path.endsWith(".graphql")
                || path.endsWith(".gql")) {
            return "graphql";
        }

        if (path.endsWith(".proto")) return "protobuf";

        // Scripts
        if (path.endsWith(".sh")
                || path.endsWith(".bash")
                || path.endsWith(".zsh")) {
            return "shell";
        }

        if (path.endsWith(".ps1")) return "powershell";

        if (path.endsWith(".bat")
                || path.endsWith(".cmd")) {
            return "batch";
        }

        // Otros
        if (path.endsWith(".svg")) return "svg";
        if (path.endsWith(".c")) return "c";

        return "unknown";
    }
}
