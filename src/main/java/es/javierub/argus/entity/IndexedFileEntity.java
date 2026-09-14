package es.javierub.argus.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.nio.file.Path;
import java.time.Instant;

@Entity
@Table(name = "IndexedFile")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IndexedFileEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String projectId;
    private String fileId;
    private String sha256;

    private String absolutePath;
    private String relativePath;

    private int chunkCount;

    private long sizeBytes;
    private Instant modifiedAt;
    private Instant indexedAt;
    private long indexationMs;
}
