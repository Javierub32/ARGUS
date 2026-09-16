package es.javierub.argus.mapper;

import es.javierub.argus.dto.CodeChunk;
import es.javierub.argus.entity.CodeChunkEntity;

import java.util.Arrays;

public class CodeChunkMapper extends  MapperDTO<CodeChunk, CodeChunkEntity> {

    public CodeChunkEntity toEntity(CodeChunk dto) {
        if (dto == null) return null;

        CodeChunkEntity entity = new CodeChunkEntity();

        entity.setChunkId(dto.getChunkId());
        entity.setProjectId(dto.getProjectId());
        entity.setFileId(dto.getFileId());
        entity.setRelativePath(dto.getRelativePath());
        entity.setStartLine(dto.getStartLine());
        entity.setEndLine(dto.getEndLine());
        entity.setChunkIndex(dto.getChunkIndex());
        entity.setContent(dto.getContent());
        entity.setLanguage(dto.getLanguage());
        entity.setFileSha256(dto.getFileSha256());

        entity.setEmbedding(dto.getEmbedding() == null
                ? null
                : Arrays.toString(dto.getEmbedding()));

        return entity;
    }

    @Override
    public CodeChunk toDTO(CodeChunkEntity entity) {
        if (entity == null) return null;

        CodeChunk dto = new CodeChunk();

        dto.setChunkId(entity.getChunkId());
        dto.setProjectId(entity.getProjectId());
        dto.setFileId(entity.getFileId());
        dto.setRelativePath(entity.getRelativePath());
        dto.setStartLine(entity.getStartLine());
        dto.setEndLine(entity.getEndLine());
        dto.setChunkIndex(entity.getChunkIndex());
        dto.setContent(entity.getContent());
        dto.setLanguage(entity.getLanguage());
        dto.setFileSha256(entity.getFileSha256());
        dto.setEmbedding(parseEmbedding(entity.getEmbedding()));

        return dto;
    }

    private float[] parseEmbedding(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String text = value.trim()
                .replace("[", "")
                .replace("]", "");

        if (text.isBlank()) {
            return new float[0];
        }

        String[] values = text.split(",");
        float[] result = new float[values.length];

        for (int i = 0; i < values.length; i++) {
            result[i] = Float.parseFloat(values[i].trim());
        }

        return result;
    }
}
