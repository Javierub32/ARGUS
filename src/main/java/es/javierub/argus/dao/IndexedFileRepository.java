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

/**
 * Stores and retrieves metadata for indexed files.
 *
 * <p>Metadata is stored in one JSON file per project. Reads and writes are
 * protected by a read/write lock, and writes use a temporary file to reduce the
 * risk of leaving incomplete JSON behind.</p>
 */
@Repository
public class IndexedFileRepository{

    /** Jackson reference used to deserialize a list of indexed files. */
    private static final TypeReference<List<IndexedFile>> FILE_LIST_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;
    private final Path rootPath;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * Creates a metadata repository.
     *
     * @param objectMapper JSON mapper provided by Spring
     * @param rootPath root path where metadata will be stored
     */
    public IndexedFileRepository(
            ObjectMapper objectMapper,
            @Value("${app.indexed-files-root}") String rootPath
    ) {
        this.objectMapper = objectMapper;
        this.rootPath = Path.of(rootPath).toAbsolutePath().normalize();
    }

    /**
     * Finds a specific file within a project.
     *
     * @param projectId project identifier
     * @param fileId file identifier
     * @return the matching file, or {@link Optional#empty()} if it does not exist
     */
    public Optional<IndexedFile> findByProjectIdAndFileId(String projectId, String fileId) {
        return findByProjectId(projectId)
                .stream()
                .filter(file -> fileId.equals(file.getFileId()))
                .findFirst();
    }

    /**
     * Retrieves an immutable snapshot of a project's files.
     *
     * @param projectId project identifier
     * @return immutable metadata list, empty if the project has not been registered
     */
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

    /**
     * Atomically replaces a project's metadata.
     *
     * @param projectId project identifier
     * @param files files that should remain associated with the project
     * @throws IllegalArgumentException if a file belongs to another project
     */
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

    /**
     * Gets the metadata JSON path for a project.
     *
     * @param projectId project identifier
     * @return path to the project's JSON file
     * @throws IllegalArgumentException if the ID does not use the permitted format
     */
    private Path metadataFile(String projectId) {
        if (!projectId.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("projectId no válido");
        }

        return rootPath.resolve(projectId).resolve("indexed-files.json");
    }

    /**
     * Reads metadata from a JSON file.
     *
     * @param file JSON file to read
     * @return independent mutable list of the deserialized data
     */
    private List<IndexedFile> read(Path file) {
        List<IndexedFile> files = objectMapper.readValue(file.toFile(), FILE_LIST_TYPE);

        return files == null ? new ArrayList<>() : new ArrayList<>(files);
    }

    /**
     * Writes metadata by replacing the target through a temporary file.
     *
     * @param target final JSON destination
     * @param files metadata to serialize
     * @throws UncheckedIOException if any input/output operation fails
     */
    private void writeJson(Path target, List<IndexedFile> files) {
        try {
            // Create <projectId>/ if it does not exist.
            Files.createDirectories(target.getParent());

            // Write to a temporary file so the existing JSON is not replaced
            // until the new JSON is complete.
            Path temp = Files.createTempFile(target.getParent(),"indexed-files-",".tmp");

            try {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), files);

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
}
