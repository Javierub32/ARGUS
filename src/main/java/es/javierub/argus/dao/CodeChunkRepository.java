package es.javierub.argus.dao;

import es.javierub.argus.entity.CodeChunkEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CodeChunkRepository extends JpaRepository<CodeChunkEntity, Integer> {
    void deleteAllByProjectId(String projectId);

    void deleteAllByProjectIdAndFileId(String projectId, String fileId);
}
