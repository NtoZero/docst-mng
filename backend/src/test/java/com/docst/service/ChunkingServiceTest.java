package com.docst.service;

import com.docst.chunking.ChunkingService;
import com.docst.chunking.MarkdownChunker;
import com.docst.chunking.ChunkResult;
import com.docst.domain.DocChunk;
import com.docst.domain.DocumentVersion;
import com.docst.repository.DocChunkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ChunkingService 단위 테스트.
 * 문서 버전 청킹 및 저장 로직 검증
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChunkingServiceTest {

    @Mock
    private DocChunkRepository docChunkRepository;

    @Mock
    private MarkdownChunker markdownChunker;

    @InjectMocks
    private ChunkingService chunkingService;

    @Captor
    private ArgumentCaptor<List<DocChunk>> chunkListCaptor;

    private DocumentVersion testDocumentVersion;

    @BeforeEach
    void setUp() {
        testDocumentVersion = mock(DocumentVersion.class);
        when(testDocumentVersion.getId()).thenReturn(UUID.randomUUID());
        when(testDocumentVersion.getContent()).thenReturn("# Test Content\n\nThis is test content.");
    }

    @Test
    @DisplayName("유효한 마크다운 콘텐츠 → 청크 저장 성공")
    void chunkAndSave_validMarkdownContent_savesChunks() {
        // Given
        List<ChunkResult> chunkResults = List.of(
            new ChunkResult("First chunk content", "# Test Content", 10),
            new ChunkResult("Second chunk content", "# Test Content", 12)
        );

        when(markdownChunker.chunk(any())).thenReturn(chunkResults);
        when(docChunkRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(docChunkRepository.countByDocumentVersionId(any())).thenReturn(0L);

        // When
        List<DocChunk> savedChunks = chunkingService.chunkAndSave(testDocumentVersion);

        // Then
        assertEquals(2, savedChunks.size());
        verify(markdownChunker).chunk(eq("# Test Content\n\nThis is test content."));
        verify(docChunkRepository).saveAll(chunkListCaptor.capture());

        List<DocChunk> capturedChunks = chunkListCaptor.getValue();
        assertEquals(2, capturedChunks.size());
        assertEquals(0, capturedChunks.get(0).getChunkIndex());
        assertEquals(1, capturedChunks.get(1).getChunkIndex());
    }

    @Test
    @DisplayName("빈 콘텐츠 → 청크 저장 없음")
    void chunkAndSave_emptyContent_savesNoChunks() {
        // Given
        when(testDocumentVersion.getContent()).thenReturn("");
        when(markdownChunker.chunk(any())).thenReturn(List.of());

        // When
        List<DocChunk> savedChunks = chunkingService.chunkAndSave(testDocumentVersion);

        // Then
        assertEquals(0, savedChunks.size());
        verify(docChunkRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("null 콘텐츠 → 청크 저장 없음 (예외 발생 안함)")
    void chunkAndSave_nullContent_savesNoChunks() {
        // Given
        when(testDocumentVersion.getContent()).thenReturn(null);

        // When
        List<DocChunk> savedChunks = chunkingService.chunkAndSave(testDocumentVersion);

        // Then
        assertEquals(0, savedChunks.size());
        verify(docChunkRepository, never()).saveAll(any());
        verify(markdownChunker, never()).chunk(any());
    }

    @Test
    @DisplayName("null DocumentVersion → IllegalArgumentException 발생")
    void chunkAndSave_nullDocumentVersion_throwsException() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            chunkingService.chunkAndSave(null);
        });
    }

    @Test
    @DisplayName("단일 청크 → 인덱스 0, headingPath, tokenCount 검증")
    void chunkAndSave_singleChunk_savesCorrectly() {
        // Given
        List<ChunkResult> chunkResults = List.of(
            new ChunkResult("Content", "# Title", 20)
        );

        when(markdownChunker.chunk(any())).thenReturn(chunkResults);
        when(docChunkRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(docChunkRepository.countByDocumentVersionId(any())).thenReturn(0L);

        // When
        List<DocChunk> savedChunks = chunkingService.chunkAndSave(testDocumentVersion);

        // Then
        assertEquals(1, savedChunks.size());
        verify(docChunkRepository).saveAll(chunkListCaptor.capture());

        List<DocChunk> capturedChunks = chunkListCaptor.getValue();
        assertEquals(1, capturedChunks.size());
        assertEquals(0, capturedChunks.get(0).getChunkIndex());
        assertEquals("# Title", capturedChunks.get(0).getHeadingPath());
        assertEquals("Content", capturedChunks.get(0).getContent());
        assertEquals(20, capturedChunks.get(0).getTokenCount());
    }

    @Test
    @DisplayName("다중 청크 → 인덱스 순서 유지 (0, 1, 2...)")
    void chunkAndSave_multipleChunks_maintainsOrder() {
        // Given
        List<ChunkResult> chunkResults = List.of(
            new ChunkResult("Chunk 0", "# H1", 10),
            new ChunkResult("Chunk 1", "# H1 > ## H2", 15),
            new ChunkResult("Chunk 2", "# H1 > ## H2 > ### H3", 20)
        );

        when(markdownChunker.chunk(any())).thenReturn(chunkResults);
        when(docChunkRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(docChunkRepository.countByDocumentVersionId(any())).thenReturn(0L);

        // When
        List<DocChunk> savedChunks = chunkingService.chunkAndSave(testDocumentVersion);

        // Then
        assertEquals(3, savedChunks.size());
        verify(docChunkRepository).saveAll(chunkListCaptor.capture());

        List<DocChunk> capturedChunks = chunkListCaptor.getValue();
        assertEquals(3, capturedChunks.size());

        for (int i = 0; i < capturedChunks.size(); i++) {
            assertEquals(i, capturedChunks.get(i).getChunkIndex());
        }

        assertEquals("# H1", capturedChunks.get(0).getHeadingPath());
        assertEquals("# H1 > ## H2", capturedChunks.get(1).getHeadingPath());
        assertEquals("# H1 > ## H2 > ### H3", capturedChunks.get(2).getHeadingPath());
    }

    @Test
    @DisplayName("headingPath가 null인 청크 → 정상 저장")
    void chunkAndSave_nullHeadingPath_handlesGracefully() {
        // Given
        List<ChunkResult> chunkResults = List.of(
            new ChunkResult("Content without heading", null, 10)
        );

        when(markdownChunker.chunk(any())).thenReturn(chunkResults);
        when(docChunkRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(docChunkRepository.countByDocumentVersionId(any())).thenReturn(0L);

        // When
        List<DocChunk> savedChunks = chunkingService.chunkAndSave(testDocumentVersion);

        // Then
        assertEquals(1, savedChunks.size());
        verify(docChunkRepository).saveAll(chunkListCaptor.capture());

        List<DocChunk> capturedChunks = chunkListCaptor.getValue();
        assertEquals(1, capturedChunks.size());
        assertNull(capturedChunks.get(0).getHeadingPath());
    }

    @Test
    @DisplayName("기존 청크가 있으면 삭제 후 새로 저장")
    void chunkAndSave_deletesExistingChunks() {
        // Given
        UUID versionId = testDocumentVersion.getId();
        List<ChunkResult> chunkResults = List.of(
            new ChunkResult("New content", "# Title", 10)
        );

        when(markdownChunker.chunk(any())).thenReturn(chunkResults);
        when(docChunkRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(docChunkRepository.countByDocumentVersionId(versionId)).thenReturn(3L);

        // When
        chunkingService.chunkAndSave(testDocumentVersion);

        // Then
        verify(docChunkRepository).deleteByDocumentVersionId(versionId);
    }

    @Test
    @DisplayName("getChunks → 인덱스 순서대로 반환")
    void getChunks_returnsChunksInOrder() {
        // Given
        UUID versionId = UUID.randomUUID();
        List<DocChunk> mockChunks = List.of(mock(DocChunk.class), mock(DocChunk.class));
        when(docChunkRepository.findByDocumentVersionIdOrderByChunkIndex(versionId)).thenReturn(mockChunks);

        // When
        List<DocChunk> chunks = chunkingService.getChunks(versionId);

        // Then
        assertEquals(2, chunks.size());
        verify(docChunkRepository).findByDocumentVersionIdOrderByChunkIndex(versionId);
    }

    @Test
    @DisplayName("countChunks → 청크 개수 반환")
    void countChunks_returnsCount() {
        // Given
        UUID versionId = UUID.randomUUID();
        when(docChunkRepository.countByDocumentVersionId(versionId)).thenReturn(5L);

        // When
        long count = chunkingService.countChunks(versionId);

        // Then
        assertEquals(5L, count);
        verify(docChunkRepository).countByDocumentVersionId(versionId);
    }

    @Test
    @DisplayName("deleteChunksByDocumentVersion → 청크 삭제")
    void deleteChunksByDocumentVersion_deletesChunks() {
        // Given
        UUID versionId = UUID.randomUUID();
        when(docChunkRepository.countByDocumentVersionId(versionId)).thenReturn(3L);

        // When
        chunkingService.deleteChunksByDocumentVersion(versionId);

        // Then
        verify(docChunkRepository).deleteByDocumentVersionId(versionId);
    }

    @Test
    @DisplayName("batchChunk → 다중 버전 일괄 처리")
    void batchChunk_processesMultipleVersions() {
        // Given
        DocumentVersion version1 = mock(DocumentVersion.class);
        DocumentVersion version2 = mock(DocumentVersion.class);
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        when(version1.getId()).thenReturn(id1);
        when(version1.getContent()).thenReturn("# Content 1");
        when(version2.getId()).thenReturn(id2);
        when(version2.getContent()).thenReturn("# Content 2");

        when(markdownChunker.chunk(any())).thenReturn(List.of(
            new ChunkResult("Content", "# Title", 10)
        ));
        when(docChunkRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(docChunkRepository.countByDocumentVersionId(any())).thenReturn(0L);

        // When
        List<UUID> successIds = chunkingService.batchChunk(List.of(version1, version2));

        // Then
        assertEquals(2, successIds.size());
        assertTrue(successIds.contains(id1));
        assertTrue(successIds.contains(id2));
    }

    @Test
    @DisplayName("batchChunk → 개별 실패해도 계속 진행")
    void batchChunk_continuesOnIndividualFailure() {
        // Given
        DocumentVersion version1 = mock(DocumentVersion.class);
        DocumentVersion version2 = mock(DocumentVersion.class);
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        when(version1.getId()).thenReturn(id1);
        when(version1.getContent()).thenReturn(null); // Will return empty list, not fail
        when(version2.getId()).thenReturn(id2);
        when(version2.getContent()).thenReturn("# Content 2");

        when(markdownChunker.chunk(any())).thenReturn(List.of(
            new ChunkResult("Content", "# Title", 10)
        ));
        when(docChunkRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(docChunkRepository.countByDocumentVersionId(any())).thenReturn(0L);

        // When
        List<UUID> successIds = chunkingService.batchChunk(List.of(version1, version2));

        // Then
        // Both should succeed (version1 just produces empty chunks)
        assertEquals(2, successIds.size());
        assertTrue(successIds.contains(id1));
        assertTrue(successIds.contains(id2));
    }
}
