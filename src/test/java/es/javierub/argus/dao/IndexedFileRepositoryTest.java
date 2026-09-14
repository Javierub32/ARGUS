package es.javierub.argus.dao;

import es.javierub.argus.entity.IndexedFileEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the JPA queries used to persist indexed-file metadata.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:argus-repository-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class IndexedFileRepositoryTest {

    @Autowired
    private IndexedFileRepository repository;

    @Test
    void returnsEmptyListForUnknownProject() {
        assertTrue(repository.findAllByProjectId("project-1").isEmpty());
    }

    @Test
    void persistsAndFindsFilesByProject() {
        IndexedFileEntity first = entity("project-1", "file-1", "a.java");
        IndexedFileEntity second = entity("project-1", "file-2", "z.java");

        repository.saveAll(List.of(first, second));

        assertEquals(
                List.of("file-1", "file-2"),
                repository.findAllByProjectId("project-1").stream()
                        .map(IndexedFileEntity::getFileId)
                        .toList()
        );
    }

    @Test
    void findsFileByProjectAndFileId() {
        IndexedFileEntity expected = repository.save(
                entity("project-1", "file-1", "src/Main.java")
        );

        assertEquals(
                expected.getId(),
                repository.findByProjectIdAndFileId("project-1", "file-1")
                        .orElseThrow()
                        .getId()
        );
        assertTrue(repository.findByProjectIdAndFileId("project-1", "missing").isEmpty());
    }

    @Test
    void findsDeletedFilesOutsideCurrentFileIds() {
        repository.saveAll(List.of(
                entity("project-1", "kept", "a.java"),
                entity("project-1", "deleted-2", "b.java"),
                entity("project-1", "deleted-1", "c.java"),
                entity("project-2", "other", "other.java")
        ));

        assertEquals(
                List.of("deleted-2", "deleted-1"),
                repository.findDeletedFiles("project-1", List.of("kept")).stream()
                        .map(IndexedFileEntity::getFileId)
                        .toList()
        );
    }

    @Test
    void deletesAllFilesForAProject() {
        repository.saveAll(List.of(
                entity("project-1", "file-1", "a.java"),
                entity("project-2", "file-2", "b.java")
        ));

        repository.deleteAllByProjectId("project-1");
        repository.flush();

        assertTrue(repository.findAllByProjectId("project-1").isEmpty());
        assertEquals(1, repository.findAllByProjectId("project-2").size());
    }

    private IndexedFileEntity entity(String projectId, String fileId, String relativePath) {
        return new IndexedFileEntity(
                null,
                projectId,
                fileId,
                fileId + "-sha",
                "C:/project/" + relativePath,
                relativePath,
                1,
                10,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:01Z"),
                5
        );
    }
}
