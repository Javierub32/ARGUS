package es.javierub.argus.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "CodeChunk")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CodeChunkEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String chunkId;
    private String projectId;
    private String fileId;

    private String relativePath;

    private int startLine;
    private int endLine;
    private int chunkIndex;

    @Lob
    private String content;

    private String language;
    private String fileSha256;

    @Lob
    private String embedding;
}
