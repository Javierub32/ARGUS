package es.javierub.argus.controller.rest;

import es.javierub.argus.dto.IndexReport;
import es.javierub.argus.dto.IndexRequest;
import es.javierub.argus.service.ProjectIndexService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;

/**
 * Exposes HTTP operations related to project indexing.
 *
 * <p>Request-body validation is delegated to Jakarta Validation, while the
 * indexing operation is delegated to {@link ProjectIndexService}.</p>
 */
@RestController
@AllArgsConstructor
@RequestMapping("/api")
public class ProjectIndexRestController {
    private final ProjectIndexService projectIndexService;

    /**
     * Indexes the project specified in the request.
     *
     * <p>This endpoint is available as {@code POST /api/index} and requires the
     * supplied path to exist and be a directory.</p>
     *
     * @param request validated request containing the project's root path
     * @return report containing the indexing result
     * @throws IOException if the project cannot be read or the index cannot be updated
     * @throws NoSuchAlgorithmException if SHA-256 is not available in the JVM
     * @throws IllegalArgumentException if the path does not exist or is not a directory
     */
    @PostMapping("/index")
    public IndexReport index(@Valid @RequestBody IndexRequest request) throws IOException, NoSuchAlgorithmException {
        Path root = Path.of(request.getProjectRoot())
                .toAbsolutePath()
                .normalize();

        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException(
                    "La ruta no existe o no es un directorio: " + root
            );
        }

        return projectIndexService.index(request.getProjectRoot());
    }
}
