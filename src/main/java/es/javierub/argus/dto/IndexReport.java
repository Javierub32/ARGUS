package es.javierub.argus.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class IndexReport {
    private String projectRoot;

    private int newFiles;
    private int modifiedFiles;
    private int deletedFiles;
    private int unchangedFiles;

    private int processedFiles;
    private int processedChunks;
    private long durationMs;
    private long durationEmbeddingMs;

}
