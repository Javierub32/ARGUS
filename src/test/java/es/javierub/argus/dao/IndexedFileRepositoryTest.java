package es.javierub.argus.dao;

import es.javierub.argus.dto.IndexedFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests reading, writing, and validation of the metadata repository.
 */
@SpringBootTest
class IndexedFileRepositoryTest {

    /** JSON mapper configured by the Spring test context. */
    @Autowired
    private ObjectMapper objectMapper;

    /** Temporary directory used as the repository root. */
    @TempDir
    Path temporaryDirectory;

    /** Repository under test. */
    private IndexedFileRepository repository;

    /** Creates an isolated repository before each test case. */
    @BeforeEach
    void setUp() {
        repository = new IndexedFileRepository(
                objectMapper,
                temporaryDirectory.toString()
        );
    }

    /** Verifies that an unknown project returns an empty list. */
    @Test
    void returnsEmptyListForUnknownProject() {
        assertEquals(List.of(), repository.findByProjectId("project-1"));
    }

    /** Verifies that metadata is stored sorted by relative path. */
    @Test
    void persistsFilesSortedByRelativePath() {
        IndexedFile second = file("file-2", "z.java");
        IndexedFile first = file("file-1", "a.java");

        repository.replaceProject("project-1", List.of(second, first));

        List<IndexedFile> result = repository.findByProjectId("project-1");

        assertEquals(List.of("a.java", "z.java"), result.stream()
                .map(IndexedFile::getRelativePath)
                .toList());
        assertTrue(temporaryDirectory
                .resolve("project-1")
                .resolve("indexed-files.json")
                .toFile()
                .isFile());
    }

    /** Verifies lookup of a file by project and file identifier. */
    @Test
    void findsFileByProjectAndFileId() {
        IndexedFile expected = file("file-1", "src/Main.java");
        repository.replaceProject("project-1", List.of(expected));

        assertTrue(repository.findByProjectIdAndFileId("project-1", "file-1")
                .isPresent());
        assertEquals(expected.getRelativePath(), repository
                .findByProjectIdAndFileId("project-1", "file-1")
                .orElseThrow()
                .getRelativePath());
        assertFalse(repository.findByProjectIdAndFileId("project-1", "missing")
                .isPresent());
    }

    /** Verifies that reads do not expose a modifiable list. */
    @Test
    void returnsAnImmutableSnapshot() {
        repository.replaceProject("project-1", List.of(file("file-1", "a.java")));

        List<IndexedFile> result = repository.findByProjectId("project-1");

        assertThrows(UnsupportedOperationException.class, () -> result.clear());
    }

    /** Verifies that unsafe project IDs are rejected. */
    @Test
    void rejectsInvalidProjectIds() {
        assertThrows(IllegalArgumentException.class,
                () -> repository.findByProjectId("../outside"));
        assertThrows(IllegalArgumentException.class,
                () -> repository.replaceProject("project/1", List.of()));
    }

    /** Verifies that a file cannot be registered under another project. */
    @Test
    void rejectsFilesBelongingToAnotherProject() {
        IndexedFile wrongProject = new IndexedFile(
                "other-project",
                "file-1",
                "sha",
                Path.of("other-project", "a.java"),
                "a.java",
                0,
                1,
                Instant.now(),
                Instant.now(),
                0
        );

        assertThrows(IllegalArgumentException.class,
                () -> repository.replaceProject("project-1", List.of(wrongProject)));
    }

    /**
     * Creates test metadata for the configured project.
     *
     * @param fileId file identifier
     * @param relativePath relative file path
     * @return file metadata ready for persistence
     */
    private IndexedFile file(String fileId, String relativePath) {
        return new IndexedFile(
                "project-1",
                fileId,
                fileId + "-sha",
                temporaryDirectory.resolve(relativePath),
                relativePath,
                1,
                10,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:01Z"),
                5
        );
    }
}
