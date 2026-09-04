package es.javierub.argus.dao;

import es.javierub.argus.dto.IndexedFile;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Repository
public class IndexedFileRepository implements ProjectIndexRepository {

    // Referencia para convertir el JSON a List<IndexedFile>
    private static final TypeReference<List<IndexedFile>> FILE_LIST_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;
    private final Path rootPath;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public IndexedFileRepository(
            ObjectMapper objectMapper,
            @Value("${app.indexed-files-root}") String rootPath
    ) {
        this.objectMapper = objectMapper;
        this.rootPath = Path.of(rootPath).toAbsolutePath().normalize();
    }

    @Override
    public Optional<IndexedFile> findByProjectIdAndFileId(String projectId, String fileId) {
        return findByProjectId(projectId)
                .stream()
                .filter(file -> fileId.equals(file.getFileId()))
                .findFirst();
    }

    @Override
    public List<IndexedFile> findByProjectId(String projectId) {
        lock.readLock().lock();

        try {
            Path file = metadataFile(projectId);

            if (Files.notExists(file)) {
                return List.of();
            }

            return List.copyOf(read(file));
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
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

            writeJson(metadataFile(projectId), newFiles);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private Path metadataFile(String projectId) {
        if (!projectId.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("projectId no válido");
        }

        return rootPath.resolve(projectId).resolve("indexed-files.json");
    }

    private List<IndexedFile> read(Path file) {
        List<IndexedFile> files = objectMapper.readValue(file.toFile(), FILE_LIST_TYPE);

        return files == null ? new ArrayList<>() : new ArrayList<>(files);
    }

    private void writeJson(Path target, List<IndexedFile> files) {
        try {
            // Crea <projectId>/ si no existe
            Files.createDirectories(target.getParent());

            // Creamos un archivo temporal para no sustituirlo por el JSON actual
            // hasta que tengamos el JSON nuevo hecho.
            Path temp = Files.createTempFile(target.getParent(),"indexed-files-",".tmp");

            try {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), files);

                try {
                    Files.move(
                            temp,
                            target,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE
                    );
                } catch (AtomicMoveNotSupportedException exception) {
                    Files.move(
                            temp,
                            target,
                            StandardCopyOption.REPLACE_EXISTING
                    );
                }
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "No se pudo escribir " + target,
                    exception
            );
        }
    }
}