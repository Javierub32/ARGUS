package es.javierub.argus.dao;

import es.javierub.argus.dto.IndexedFile;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProjectIndexRepository {
    Optional<IndexedFile> findByProjectIdAndFileId(
            String projectId,
            String fileId
    );

    List<IndexedFile> findByProjectId(String projectId);

    void replaceProject(
            String projectId,
            Collection<IndexedFile> files
    );
}
