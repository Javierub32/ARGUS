package es.javierub.argus.service;

import es.javierub.argus.dao.IndexedFileRepository;
import es.javierub.argus.debug.JsonWriter;
import es.javierub.argus.dto.IndexedFile;
import es.javierub.argus.entity.IndexedFileEntity;
import es.javierub.argus.indexing.Hasher;
import es.javierub.argus.mapper.IndexedFileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IndexedFileService {
    private final IndexedFileRepository indexedFileRepository;

    public IndexedFileEntity createIndexedFile(Path root, String projectId, Path filePath) throws IOException, NoSuchAlgorithmException {
        String fileId = Hasher.sha256(filePath.toString());
        String fileHash256 = Hasher.sha256(filePath);
        String absolutePath = filePath.toString().replace('\\', '/');
        String relativePath = root.relativize(filePath).toString().replace('\\', '/');
        long fileSize = Files.size(filePath);
        Instant modifiedAt = Files.getLastModifiedTime(filePath).toInstant();
        Instant indexedAt = Instant.now();

        return new IndexedFileEntity(
                null,
                projectId,
                fileId,
                fileHash256,
                absolutePath,
                relativePath,
                0,
                fileSize,
                modifiedAt,
                indexedAt,
                0
        );
    }

    @Transactional
    public void replaceProject(String projectId, List<IndexedFileEntity> currentFiles) {
        indexedFileRepository.deleteAllByProjectId(projectId);
        indexedFileRepository.flush();

        currentFiles.forEach(file -> file.setId(null));
        indexedFileRepository.saveAll(currentFiles);
    }

}
