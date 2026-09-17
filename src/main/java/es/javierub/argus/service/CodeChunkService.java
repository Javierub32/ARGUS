package es.javierub.argus.service;

import es.javierub.argus.dao.CodeChunkRepository;
import es.javierub.argus.dto.CodeChunk;
import es.javierub.argus.entity.CodeChunkEntity;
import es.javierub.argus.entity.IndexedFileEntity;
import es.javierub.argus.mapper.CodeChunkMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CodeChunkService {
    private final CodeChunkRepository codeChunkRepository;
    private final CodeChunkMapper codeChunkMapper;

    @Transactional
    public void replaceFile(String projectId, String fileId, List<CodeChunk> chunks) {
        codeChunkRepository.deleteAllByProjectIdAndFileId(projectId, fileId);
        codeChunkRepository.flush();

        codeChunkRepository.saveAll(chunks.stream().map(dto -> codeChunkMapper.toEntity(dto)).toList());
    }

    @Transactional
    public void deleteFile(String projectId, String fileId) {
        codeChunkRepository.deleteAllByProjectIdAndFileId(projectId, fileId);
    }
}
