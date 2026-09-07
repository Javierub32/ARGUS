package es.javierub.argus.service;

import es.javierub.argus.dao.ChunkRepository;
import es.javierub.argus.dao.IndexedFileRepository;
import es.javierub.argus.dto.CodeChunk;
import es.javierub.argus.dto.IndexReport;
import es.javierub.argus.dto.IndexedFile;
import es.javierub.argus.indexing.FileScanner;
import es.javierub.argus.indexing.Hasher;
import es.javierub.argus.indexing.WholeFileChunker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests a project's incremental indexing lifecycle.
 */
class ProjectIndexServiceTest {

    /** Temporary directory acting as the root of the project under test. */
    @TempDir
    Path temporaryDirectory;

    /** Mock scanner that provides the project's files. */
    private FileScanner fileScanner;
    /** Mock repository for file metadata. */
    private IndexedFileRepository indexedFileRepository;
    /** Mock generator for chunks and embeddings. */
    private WholeFileChunker wholeFileChunker;
    /** Mock repository for chunks. */
    private ChunkRepository chunkRepository;
    /** Service under test. */
    private ProjectIndexService service;
    /** Normalized path of the temporary project. */
    private Path root;

    /** Initializes test doubles and the service before each case. */
    @BeforeEach
    void setUp() {
        root = temporaryDirectory.toAbsolutePath().normalize();
        fileScanner = mock(FileScanner.class);
        indexedFileRepository = mock(IndexedFileRepository.class);
        wholeFileChunker = mock(WholeFileChunker.class);
        chunkRepository = mock(ChunkRepository.class);
        service = new ProjectIndexService(
                fileScanner,
                indexedFileRepository,
                wholeFileChunker,
                chunkRepository
        );
    }

    /** Verifies indexing of new files and report construction. */
    @Test
    void indexesNewFilesAndBuildsReport() throws Exception {
        Path file = createFile("Main.java", "class Main {}\n");
        String projectId = projectId();
        List<CodeChunk> chunks = List.of(chunk("chunk-1"));
        when(fileScanner.scan(root)).thenReturn(List.of(file));
        when(indexedFileRepository.findByProjectId(projectId)).thenReturn(List.of());
        when(wholeFileChunker.chunk(any(IndexedFile.class), eq("class Main {}\n")))
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
                eq(Hasher.sha256(file.toString())),
                eq(chunks)
        );
        verify(indexedFileRepository).replaceProject(eq(projectId), any(Collection.class));
    }

    /** Verifies that an unchanged file is not processed again. */
    @Test
    void doesNotReindexUnchangedFiles() throws Exception {
        Path file = createFile("Main.java", "class Main {}\n");
        String projectId = projectId();
        IndexedFile previous = indexedFile(projectId, file);
        when(fileScanner.scan(root)).thenReturn(List.of(file));
        when(indexedFileRepository.findByProjectId(projectId)).thenReturn(List.of(previous));

        IndexReport report = service.index(root.toString());

        assertEquals(0, report.getNewFiles());
        assertEquals(0, report.getModifiedFiles());
        assertEquals(0, report.getDeletedFiles());
        assertEquals(1, report.getUnchangedFiles());
        assertEquals(0, report.getProcessedFiles());
        assertEquals(0, report.getProcessedChunks());
        verifyNoInteractions(wholeFileChunker, chunkRepository);
    }

    /** Verifies that a modified file regenerates its chunks. */
    @Test
    void reindexesModifiedFiles() throws Exception {
        Path file = createFile("Main.java", "class Main {}\n");
        String projectId = projectId();
        IndexedFile previous = indexedFile(projectId, file);
        previous.setSha256("old-sha");
        List<CodeChunk> chunks = List.of(chunk("chunk-1"));
        when(fileScanner.scan(root)).thenReturn(List.of(file));
        when(indexedFileRepository.findByProjectId(projectId)).thenReturn(List.of(previous));
        when(wholeFileChunker.chunk(any(IndexedFile.class), anyString())).thenReturn(chunks);

        IndexReport report = service.index(root.toString());

        assertEquals(0, report.getNewFiles());
        assertEquals(1, report.getModifiedFiles());
        assertEquals(1, report.getProcessedFiles());
        assertEquals(1, report.getProcessedChunks());
        verify(wholeFileChunker).chunk(any(IndexedFile.class), eq("class Main {}\n"));
        verify(chunkRepository).addOrReplaceFileChunks(
                eq(projectId),
                eq(Hasher.sha256(file.toString())),
                eq(chunks)
        );
    }

    /** Verifies that chunks for a deleted file are removed from the index. */
    @Test
    void removesChunksForDeletedFiles() throws Exception {
        String projectId = projectId();
        Path deletedPath = root.resolve("Deleted.java");
        IndexedFile deleted = new IndexedFile(
                projectId,
                "deleted-file",
                "old-sha",
                deletedPath,
                "Deleted.java",
                1,
                20,
                Instant.now(),
                Instant.now(),
                2
        );
        when(fileScanner.scan(root)).thenReturn(List.of());
        when(indexedFileRepository.findByProjectId(projectId)).thenReturn(List.of(deleted));

        IndexReport report = service.index(root.toString());

        assertEquals(0, report.getNewFiles());
        assertEquals(0, report.getModifiedFiles());
        assertEquals(1, report.getDeletedFiles());
        assertEquals(0, report.getProcessedFiles());
        verify(chunkRepository).deleteFileChunks(projectId, "deleted-file");
        verifyNoInteractions(wholeFileChunker);
    }

    /** Verifies that previous metadata is retained for unchanged files. */
    @Test
    void keepsPreviousMetadataForUnchangedFiles() throws Exception {
        Path file = createFile("Main.java", "class Main {}\n");
        String projectId = projectId();
        IndexedFile previous = indexedFile(projectId, file);
        when(fileScanner.scan(root)).thenReturn(List.of(file));
        when(indexedFileRepository.findByProjectId(projectId)).thenReturn(List.of(previous));

        service.index(root.toString());

        ArgumentCaptor<Collection<IndexedFile>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(indexedFileRepository).replaceProject(eq(projectId), captor.capture());
        assertEquals(previous, captor.getValue().iterator().next());
    }

    /** Verifies incremental behavior across multiple runs. */
    @Test
    void performsIncrementalIndexingAcrossSeveralRuns() throws Exception {
        Path modifiedFile = createFile("Modified.java", "class Modified {}\n");
        Path deletedFile = createFile("Deleted.java", "class Deleted {}\n");
        Path newFile = root.resolve("New.java");
        String projectId = projectId();

        List<IndexedFile> storedFiles = new ArrayList<>();
        when(fileScanner.scan(root)).thenReturn(
                List.of(modifiedFile, deletedFile),
                List.of(modifiedFile, newFile),
                List.of(modifiedFile, newFile)
        );
        when(indexedFileRepository.findByProjectId(projectId))
                .thenAnswer(invocation -> List.copyOf(storedFiles));
        doAnswer(invocation -> {
            Collection<IndexedFile> files = invocation.getArgument(1);
            storedFiles.clear();
            storedFiles.addAll(files);
            return null;
        }).when(indexedFileRepository).replaceProject(eq(projectId), any(Collection.class));
        when(wholeFileChunker.chunk(any(IndexedFile.class), anyString()))
                .thenAnswer(invocation -> {
                    IndexedFile file = invocation.getArgument(0);
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

        ArgumentCaptor<IndexedFile> indexedFiles =
                ArgumentCaptor.forClass(IndexedFile.class);
        verify(wholeFileChunker, org.mockito.Mockito.times(4))
                .chunk(indexedFiles.capture(), anyString());
        assertEquals(
                List.of("Modified.java", "Deleted.java", "Modified.java", "New.java"),
                indexedFiles.getAllValues().stream()
                        .map(IndexedFile::getRelativePath)
                        .toList()
        );

        ArgumentCaptor<String> fileIds = ArgumentCaptor.forClass(String.class);
        verify(chunkRepository, org.mockito.Mockito.times(4))
                .addOrReplaceFileChunks(
                        eq(projectId),
                        fileIds.capture(),
                        any(Collection.class)
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

    /**
     * Creates a file inside the temporary project.
     *
     * @param name file name
     * @param content file content in UTF-8
     * @return path to the created file
     * @throws Exception if the file cannot be written
     */
    private Path createFile(String name, String content) throws Exception {
        Path file = root.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    /**
     * Calculates the stable identifier of the temporary project.
     *
     * @return SHA-256 hash of the normalized project path
     * @throws Exception if SHA-256 cannot be calculated
     */
    private String projectId() throws Exception {
        return Hasher.sha256(root.toString().replace('\\', '/'));
    }

    /**
     * Builds metadata representing a file's previously indexed version.
     *
     * @param projectId project identifier
     * @param file file whose metadata will be created
     * @return metadata for the file's previous version
     * @throws Exception if the file properties cannot be read
     */
    private IndexedFile indexedFile(String projectId, Path file) throws Exception {
        return new IndexedFile(
                projectId,
                Hasher.sha256(file.toString()),
                Hasher.sha256(file),
                file,
                root.relativize(file).toString().replace('\\', '/'),
                1,
                Files.size(file),
                Files.getLastModifiedTime(file).toInstant(),
                Instant.now(),
                5
        );
    }

    /**
     * Creates a minimal chunk for mock responses.
     *
     * @param chunkId chunk identifier
     * @return test chunk with a valid embedding
     */
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
                "java",
                "sha",
                new float[]{0.1f}
        );
    }
}
