package es.javierub.argus.controller.rest;

import es.javierub.argus.dto.IndexReport;
import es.javierub.argus.dto.IndexRequest;
import es.javierub.argus.service.ProjectIndexService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the project-indexing REST controller.
 */
class ProjectIndexRestControllerTest {

    /** Verifies that a valid path is delegated to the indexing service. */
    @Test
    void delegatesIndexingForAnExistingDirectory(@TempDir Path temporaryDirectory)
            throws Exception {
        ProjectIndexService service = mock(ProjectIndexService.class);
        ProjectIndexRestController controller = new ProjectIndexRestController(service);
        IndexRequest request = new IndexRequest(temporaryDirectory.toString());
        IndexReport expected = new IndexReport(
                temporaryDirectory.toString(),
                1, 0, 0, 0, 1, 1, 10, 5
        );
        when(service.index(request.getProjectRoot())).thenReturn(expected);

        IndexReport result = controller.index(request);

        assertSame(expected, result);
        verify(service).index(request.getProjectRoot());
    }

    /** Verifies that a path that does not exist is rejected. */
    @Test
    void rejectsANonExistingDirectory(@TempDir Path temporaryDirectory) {
        ProjectIndexService service = mock(ProjectIndexService.class);
        ProjectIndexRestController controller = new ProjectIndexRestController(service);
        String missingPath = temporaryDirectory.resolve("missing").toString();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> controller.index(new IndexRequest(missingPath))
        );

        assertEquals("La ruta no existe o no es un directorio: "
                + Path.of(missingPath).toAbsolutePath().normalize(), exception.getMessage());
    }

    /** Verifies that a file is rejected when a directory is expected. */
    @Test
    void rejectsAFileInsteadOfADirectory(@TempDir Path temporaryDirectory)
            throws Exception {
        ProjectIndexService service = mock(ProjectIndexService.class);
        ProjectIndexRestController controller = new ProjectIndexRestController(service);
        Path file = temporaryDirectory.resolve("not-a-directory.txt");
        Files.writeString(file, "content");

        assertThrows(
                IllegalArgumentException.class,
                () -> controller.index(new IndexRequest(file.toString()))
        );
    }
}
