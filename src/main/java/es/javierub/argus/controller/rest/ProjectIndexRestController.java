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

@RestController
@AllArgsConstructor
@RequestMapping("/api")
public class ProjectIndexRestController {
    private final ProjectIndexService projectIndexService;

    @PostMapping("/index")
    public IndexReport index(@Valid @RequestBody IndexRequest request) throws IOException {
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
