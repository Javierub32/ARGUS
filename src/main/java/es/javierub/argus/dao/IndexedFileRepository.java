package es.javierub.argus.dao;

import es.javierub.argus.entity.IndexedFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;


@Repository
public interface IndexedFileRepository extends JpaRepository<IndexedFileEntity, Integer> {
    List<IndexedFileEntity> findAllByProjectId(String projectId);

    Optional<IndexedFileEntity> findByFileId(String fileId);

    Optional<IndexedFileEntity> findByProjectIdAndFileId(String projectId, String fileId);

    @Query("""
        SELECT f FROM IndexedFileEntity f
        WHERE f.projectId = :projectId
          AND f.fileId NOT IN :currentFileIds
        ORDER BY f.relativePath
        """)
    List<IndexedFileEntity> findDeletedFiles(@Param("projectId") String projectId,
                                             @Param("currentFileIds") List<String> currentFileIds);

    void deleteAllByProjectId(String projectId);

    /*
    public void replaceProject(String projectId, Collection<IndexedFile> files) {
        lock.writeLock().lock();

        try {
            List<IndexedFile> newFiles = new ArrayList<>(files);

            newFiles.forEach(file -> {
                if (!projectId.equals(file.getProjectId())) {
                    throw new IllegalArgumentException("El proyecto del archivo no coincide");
                }
            });

            newFiles.sort(Comparator.comparing(file -> file.getRelativePath()));

            // writeJson(metadataFile(projectId), newFiles);
        } finally {
            lock.writeLock().unlock();
        }
    }
    */
}
