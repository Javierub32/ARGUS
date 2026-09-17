package es.javierub.argus.service;

import es.javierub.argus.dao.ChunkRepository;
import es.javierub.argus.dao.IndexedFileRepository;
import es.javierub.argus.debug.JsonWriter;
import es.javierub.argus.dto.CodeChunk;
import es.javierub.argus.dto.IndexReport;
import es.javierub.argus.entity.IndexedFileEntity;
import es.javierub.argus.indexing.FileScanner;
import es.javierub.argus.indexing.Hasher;
import es.javierub.argus.indexing.WholeFileChunker;
import es.javierub.argus.mapper.CodeChunkMapper;
import es.javierub.argus.mapper.IndexedFileMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests a project's incremental indexing lifecycle.
 */
class ProjectIndexServiceTest {

    @TempDir
    Path temporaryDirectory;

    private FileScanner fileScanner;
    private IndexedFileRepository indexedFileRepository;
    private WholeFileChunker wholeFileChunker;
    private ChunkRepository chunkRepository;
    private IndexedFileService indexedFileService;
    private IndexedFileMapper indexedFileMapper;
    private CodeChunkService codeChunkService;
    private CodeChunkMapper codeChunkMapper;
    private JsonWriter jsonWriter;
    private ProjectIndexService service;
    private Path root;

    @BeforeEach
    void setUp() {
        root = temporaryDirectory.toAbsolutePath().normalize();
        fileScanner = mock(FileScanner.class);
        indexedFileRepository = mock(IndexedFileRepository.class);
        wholeFileChunker = mock(WholeFileChunker.class);
        chunkRepository = mock(ChunkRepository.class);
        indexedFileService = mock(IndexedFileService.class);
        indexedFileMapper = mock(IndexedFileMapper.class);
        codeChunkService = mock(CodeChunkService.class);
        codeChunkMapper = mock(CodeChunkMapper.class);
        jsonWriter = mock(JsonWriter.class);
        service = new ProjectIndexService(
                fileScanner,
                indexedFileRepository,
                wholeFileChunker,
                chunkRepository,
                indexedFileService,
                indexedFileMapper,
                codeChunkService,
                codeChunkMapper,
                jsonWriter
        );
    }

    @Test
    void indexesNewFilesAndBuildsReport() throws Exception {
        Path file = createFile("Main.java", "class Main {}\n");
        String projectId = projectId();
        IndexedFileEntity current = indexedFile(projectId, file);
        List<CodeChunk> chunks = List.of(chunk("chunk-1"));
        givenCreatedFile(file, projectId, current);
        when(fileScanner.scan(root)).thenReturn(List.of(file));
        when(indexedFileRepository.findByProjectIdAndFileId(projectId, current.getFileId()))
                .thenReturn(Optional.empty());
        when(indexedFileRepository.findDeletedFiles(projectId, List.of(current.getFileId())))
                .thenReturn(List.of());
        when(wholeFileChunker.chunk(any(IndexedFileEntity.class), eq("class Main {}\n")))
                .thenReturn(chunks);

        IndexReport report = service.index(root.toString());

        assertEquals(1, report.getNewFiles());
        assertEquals(0, report.getModifiedFiles());
        assertEquals(0, report.getDeletedFiles());
        assertEquals(0, report.getUnchangedFiles());
        assertEquals(1, report.getProcessedFiles());
        assertEquals(1, report.getProcessedChunks());
        verify(chunkRepository).addOrReplaceFileChunks(
                eq(projectId),
                eq(current.getFileId()),
                eq(chunks)
        );
        verify(codeChunkService).replaceFile(
                eq(projectId),
                eq(current.getFileId()),
                eq(chunks)
        );
        verify(indexedFileService).replaceProject(eq(projectId), anyList());
    }

    @Test
    void doesNotReindexUnchangedFiles() throws Exception {
        Path file = createFile("Main.java", "class Main {}\n");
        String projectId = projectId();
        IndexedFileEntity current = indexedFile(projectId, file);
        IndexedFileEntity previous = indexedFile(projectId, file);
        givenCreatedFile(file, projectId, current);
        when(fileScanner.scan(root)).thenReturn(List.of(file));
        when(indexedFileRepository.findByProjectIdAndFileId(projectId, current.getFileId()))
                .thenReturn(Optional.of(previous));
        when(indexedFileRepository.findDeletedFiles(projectId, List.of(current.getFileId())))
                .thenReturn(List.of());

        IndexReport report = service.index(root.toString());

        assertEquals(0, report.getNewFiles());
        assertEquals(0, report.getModifiedFiles());
        assertEquals(0, report.getDeletedFiles());
        assertEquals(1, report.getUnchangedFiles());
        assertEquals(0, report.getProcessedFiles());
        assertEquals(0, report.getProcessedChunks());
        verifyNoInteractions(wholeFileChunker, chunkRepository, codeChunkService);
    }

    @Test
    void reindexesModifiedFiles() throws Exception {
        Path file = createFile("Main.java", "class Main {}\n");
        String projectId = projectId();
        IndexedFileEntity current = indexedFile(projectId, file);
        IndexedFileEntity previous = indexedFile(projectId, file);
        previous.setSha256("old-sha");
        List<CodeChunk> chunks = List.of(chunk("chunk-1"));
        givenCreatedFile(file, projectId, current);
        when(fileScanner.scan(root)).thenReturn(List.of(file));
        when(indexedFileRepository.findByProjectIdAndFileId(projectId, current.getFileId()))
                .thenReturn(Optional.of(previous));
        when(indexedFileRepository.findDeletedFiles(projectId, List.of(current.getFileId())))
                .thenReturn(List.of());
        when(wholeFileChunker.chunk(any(IndexedFileEntity.class), anyString())).thenReturn(chunks);

        IndexReport report = service.index(root.toString());

        assertEquals(0, report.getNewFiles());
        assertEquals(1, report.getModifiedFiles());
        assertEquals(0, report.getDeletedFiles());
        assertEquals(0, report.getUnchangedFiles());
        assertEquals(1, report.getProcessedFiles());
        assertEquals(1, report.getProcessedChunks());
        verify(wholeFileChunker).chunk(any(IndexedFileEntity.class), eq("class Main {}\n"));
        verify(chunkRepository).addOrReplaceFileChunks(
                eq(projectId),
                eq(current.getFileId()),
                eq(chunks)
        );
        verify(codeChunkService).replaceFile(
                eq(projectId),
                eq(current.getFileId()),
                eq(chunks)
        );
    }

    @Test
    void removesChunksForDeletedFiles() throws Exception {
        String projectId = projectId();
        IndexedFileEntity deleted = new IndexedFileEntity(
                null,
                projectId,
                "deleted-file",
                "old-sha",
                root.resolve("Deleted.java").toString(),
                "Deleted.java",
                1,
                20,
                Instant.now(),
                Instant.now(),
                2
        );
        when(fileScanner.scan(root)).thenReturn(List.of());
        when(indexedFileRepository.findAllByProjectId(projectId)).thenReturn(List.of(deleted));

        IndexReport report = service.index(root.toString());

        assertEquals(0, report.getNewFiles());
        assertEquals(0, report.getModifiedFiles());
        assertEquals(1, report.getDeletedFiles());
        assertEquals(0, report.getUnchangedFiles());
        assertEquals(0, report.getProcessedFiles());
        assertEquals(0, report.getProcessedChunks());
        verify(chunkRepository).deleteFileChunks(projectId, "deleted-file");
        verify(codeChunkService).deleteFile(projectId, "deleted-file");
        verifyNoInteractions(wholeFileChunker);
    }

    @Test
    void keepsPreviousMetadataForUnchangedFiles() throws Exception {
        Path file = createFile("Main.java", "class Main {}\n");
        String projectId = projectId();
        IndexedFileEntity current = indexedFile(projectId, file);
        IndexedFileEntity previous = indexedFile(projectId, file);
        givenCreatedFile(file, projectId, current);
        when(fileScanner.scan(root)).thenReturn(List.of(file));
        when(indexedFileRepository.findByProjectIdAndFileId(projectId, current.getFileId()))
                .thenReturn(Optional.of(previous));
        when(indexedFileRepository.findDeletedFiles(projectId, List.of(current.getFileId())))
                .thenReturn(List.of());

        service.index(root.toString());

        ArgumentCaptor<List<IndexedFileEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(indexedFileService).replaceProject(eq(projectId), captor.capture());
        assertSame(previous, captor.getValue().getFirst());
    }

    @Test
    void performsIncrementalIndexingAcrossSeveralRuns() throws Exception {
        Path modifiedFile = createFile("Modified.java", "class Modified {}\n");
        Path deletedFile = createFile("Deleted.java", "class Deleted {}\n");
        Path newFile = root.resolve("New.java");
        String projectId = projectId();

        List<IndexedFileEntity> storedFiles = new ArrayList<>();
        when(fileScanner.scan(root)).thenReturn(
                List.of(modifiedFile, deletedFile),
                List.of(modifiedFile, newFile),
                List.of(modifiedFile, newFile)
        );
        givenCreatedFileEachTime(modifiedFile, projectId);
        givenCreatedFileEachTime(deletedFile, projectId);
        givenCreatedFileEachTime(newFile, projectId);
        when(indexedFileRepository.findByProjectIdAndFileId(eq(projectId), anyString()))
                .thenAnswer(invocation -> storedFiles.stream()
                        .filter(file -> file.getFileId().equals(invocation.getArgument(1)))
                        .findFirst());
        when(indexedFileRepository.findDeletedFiles(eq(projectId), anyList()))
                .thenAnswer(invocation -> {
                    List<String> currentIds = invocation.getArgument(1);
                    return storedFiles.stream()
                            .filter(file -> !currentIds.contains(file.getFileId()))
                            .toList();
                });
        doAnswer(invocation -> {
            List<IndexedFileEntity> files = invocation.getArgument(1);
            storedFiles.clear();
            storedFiles.addAll(files);
            return null;
        }).when(indexedFileService).replaceProject(eq(projectId), anyList());
        when(wholeFileChunker.chunk(any(IndexedFileEntity.class), anyString()))
                .thenAnswer(invocation -> {
                    IndexedFileEntity file = invocation.getArgument(0);
                    return List.of(chunk(file.getRelativePath()));
                });

        IndexReport firstReport = service.index(root.toString());

        assertEquals(2, firstReport.getNewFiles());
        assertEquals(0, firstReport.getModifiedFiles());
        assertEquals(0, firstReport.getDeletedFiles());
        assertEquals(0, firstReport.getUnchangedFiles());
        assertEquals(2, firstReport.getProcessedFiles());
        assertEquals(2, firstReport.getProcessedChunks());

        Files.writeString(
                modifiedFile,
                "class Modified { int changed; }\n",
                StandardCharsets.UTF_8
        );
        Files.delete(deletedFile);
        Files.writeString(newFile, "class New {}\n", StandardCharsets.UTF_8);

        IndexReport secondReport = service.index(root.toString());

        assertEquals(1, secondReport.getNewFiles());
        assertEquals(1, secondReport.getModifiedFiles());
        assertEquals(1, secondReport.getDeletedFiles());
        assertEquals(0, secondReport.getUnchangedFiles());
        assertEquals(2, secondReport.getProcessedFiles());
        assertEquals(2, secondReport.getProcessedChunks());

        IndexReport thirdReport = service.index(root.toString());

        assertEquals(0, thirdReport.getNewFiles());
        assertEquals(0, thirdReport.getModifiedFiles());
        assertEquals(0, thirdReport.getDeletedFiles());
        assertEquals(2, thirdReport.getUnchangedFiles());
        assertEquals(0, thirdReport.getProcessedFiles());
        assertEquals(0, thirdReport.getProcessedChunks());

        ArgumentCaptor<IndexedFileEntity> indexedFiles =
                ArgumentCaptor.forClass(IndexedFileEntity.class);
        verify(wholeFileChunker, org.mockito.Mockito.times(4))
                .chunk(indexedFiles.capture(), anyString());
        assertEquals(
                List.of("Modified.java", "Deleted.java", "Modified.java", "New.java"),
                indexedFiles.getAllValues().stream()
                        .map(IndexedFileEntity::getRelativePath)
                        .toList()
        );

        ArgumentCaptor<String> fileIds = ArgumentCaptor.forClass(String.class);
        verify(chunkRepository, org.mockito.Mockito.times(4))
                .addOrReplaceFileChunks(
                        eq(projectId),
                        fileIds.capture(),
                        anyList()
                );
        assertEquals(
                List.of(
                        Hasher.sha256(modifiedFile.toString()),
                        Hasher.sha256(deletedFile.toString()),
                        Hasher.sha256(modifiedFile.toString()),
                        Hasher.sha256(newFile.toString())
                ),
                fileIds.getAllValues()
        );
        verify(chunkRepository).deleteFileChunks(
                projectId,
                Hasher.sha256(deletedFile.toString())
        );
    }

    private void givenCreatedFile(
            Path file,
            String projectId,
            IndexedFileEntity entity
    ) throws Exception {
        when(indexedFileService.createIndexedFile(root, projectId, file)).thenReturn(entity);
    }

    private void givenCreatedFileEachTime(Path file, String projectId) throws Exception {
        when(indexedFileService.createIndexedFile(root, projectId, file))
                .thenAnswer(invocation -> indexedFile(projectId, file));
    }

    private Path createFile(String name, String content) throws Exception {
        Path file = root.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private String projectId() throws Exception {
        return Hasher.sha256(root.toString().replace('\\', '/'));
    }

    private IndexedFileEntity indexedFile(String projectId, Path file) throws Exception {
        return new IndexedFileEntity(
                null,
                projectId,
                Hasher.sha256(file.toString()),
                Hasher.sha256(file),
                file.toString(),
                root.relativize(file).toString().replace('\\', '/'),
                1,
                Files.size(file),
                Files.getLastModifiedTime(file).toInstant(),
                Instant.now(),
                5
        );
    }

    private CodeChunk chunk(String chunkId) {
        return new CodeChunk(
                chunkId,
                "project-1",
                "file-1",
                "Main.java",
                1,
                1,
                0,
                "content",
                "unknown",
                "sha",
                new float[]{0.1f}
        );
    }
}
