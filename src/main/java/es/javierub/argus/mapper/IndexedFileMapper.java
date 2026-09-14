package es.javierub.argus.mapper;

import es.javierub.argus.dto.IndexedFile;
import es.javierub.argus.entity.IndexedFileEntity;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
public class IndexedFileMapper extends MapperDTO<IndexedFile, IndexedFileEntity> {

    @Override
    public IndexedFile toDTO(IndexedFileEntity entity) {
        if (entity == null) return null;

        Path path = entity.getAbsolutePath() == null
                ? null
                : Path.of(entity.getAbsolutePath());

        return new IndexedFile(
                entity.getProjectId(),
                entity.getFileId(),
                entity.getSha256(),
                path,
                entity.getRelativePath(),
                entity.getChunkCount(),
                entity.getSizeBytes(),
                entity.getModifiedAt(),
                entity.getIndexedAt(),
                entity.getIndexationMs()
        );
    }
}
