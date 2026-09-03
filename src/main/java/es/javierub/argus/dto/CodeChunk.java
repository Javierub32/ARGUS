package es.javierub.argus.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CodeChunk {
    private String chunkId;
    private String projectId;
    private String fileId;

    private String relativePath;

    private int startLine;
    private int endLine;
    private int chunkIndex;

    private String text;

    private String language;   // Revisar si sobra
    private String fileSha256; // Revisar si sobra
    // Añadir llamada a embedding
}
