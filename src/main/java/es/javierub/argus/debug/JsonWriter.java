package es.javierub.argus.debug;

import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Component
public class JsonWriter {

    private final ObjectMapper objectMapper;
    private final Path rootPath;

    public JsonWriter(ObjectMapper objectMapper, @Value("${app.json-root}") String rootPath) {
        this.objectMapper = objectMapper;
        this.rootPath = Path.of(rootPath).toAbsolutePath().normalize();
    }

    public <T> void write(String folderName, String fileName, T data) {
        validateName(folderName);
        validateName(fileName);

        Path folder = rootPath.resolve(folderName).normalize();
        Path target = folder.resolve(fileName + ".json").normalize();

        if (!target.startsWith(rootPath)) {
            throw new IllegalArgumentException("La ruta del JSON no es válida");
        }

        try {
            Files.createDirectories(folder);

            Path temp = Files.createTempFile(folder, fileName + "-", ".tmp");

            try {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), data);

                try {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

                } catch (AtomicMoveNotSupportedException exception) {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp);
            }

        } catch (IOException exception) {
            throw new UncheckedIOException("No se pudo escribir " + target, exception);
        }
    }

    private void validateName(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException(
                    "Nombre no válido: " + name
            );
        }
    }
}