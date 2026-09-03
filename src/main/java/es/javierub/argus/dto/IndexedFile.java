package es.javierub.argus.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IndexedFile {
    private String projectId;
    private String fileId;
    private String sha256;

    private String relativePath;

    private int chunkCount;

    private long sizeBytes;
    private Instant modifiedAt;
    private Instant indexedAt;
    private long indexationMs;
}