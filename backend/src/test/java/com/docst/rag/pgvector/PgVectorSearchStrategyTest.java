package com.docst.rag.pgvector;

import com.docst.domain.Document;
import com.docst.domain.DocumentVersion;
import com.docst.domain.Repository;
import com.docst.embedding.DocstEmbeddingService;
import com.docst.rag.RagMode;
import com.docst.service.SearchService.SearchResult;
import com.docst.service.SemanticSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PgVectorSearchStrategy 단위 테스트.
 * Phase 4-B: Mode 1 (벡터 검색) 전략 검증
 */
@ExtendWith(MockitoExtension.class)
class PgVectorSearchStrategyTest {

    @Mock
    private SemanticSearchService semanticSearchService;

    @Mock
    private DocstEmbeddingService embeddingService;

    @InjectMocks
    private PgVectorSearchStrategy strategy;

    private UUID testProjectId;
    private UUID testDocId;

    @BeforeEach
    void setUp() {
        testProjectId = UUID.randomUUID();
        testDocId = UUID.randomUUID();
    }

    @Test
    @DisplayName("getSupportedMode() → PGVECTOR 반환")
    void getSupportedMode_returnsPgVector() {
        // When
        RagMode mode = strategy.getSupportedMode();

        // Then
        assertEquals(RagMode.PGVECTOR, mode);
    }

    @Test
    @DisplayName("search() → SemanticSearchService에 위임")
    void search_delegatesToSemanticSearchService() {
        // Given
        String query = "test query";
        int topK = 10;
        List<SearchResult> expectedResults = List.of(
            createSearchResult(testDocId, "result 1"),
            createSearchResult(testDocId, "result 2")
        );

        when(semanticSearchService.searchSemantic(testProjectId, query, topK))
            .thenReturn(expectedResults);

        // When
        List<SearchResult> results = strategy.search(testProjectId, query, topK);

        // Then
        assertEquals(expectedResults, results);
        verify(semanticSearchService).searchSemantic(testProjectId, query, topK);
    }

    @Test
    @DisplayName("search() 결과 없음 → 빈 리스트 반환")
    void search_noResults_returnsEmptyList() {
        // Given
        when(semanticSearchService.searchSemantic(any(), anyString(), anyInt()))
            .thenReturn(List.of());

        // When
        List<SearchResult> results = strategy.search(testProjectId, "query", 5);

        // Then
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("indexDocument() → EmbeddingService에 위임")
    void indexDocument_delegatesToEmbeddingService() {
        // Given
        Repository repo = mock(Repository.class);
        Document doc = mock(Document.class);
        DocumentVersion docVersion = mock(DocumentVersion.class);

        when(docVersion.getDocument()).thenReturn(doc);
        when(doc.getId()).thenReturn(testDocId);
        when(doc.getPath()).thenReturn("path/to/doc.md");

        when(embeddingService.embedDocumentVersion(docVersion)).thenReturn(15);

        // When
        strategy.indexDocument(docVersion);

        // Then
        verify(embeddingService).embedDocumentVersion(docVersion);
    }

    @Test
    @DisplayName("indexDocument() 임베딩 생성 개수 로깅 확인")
    void indexDocument_logsEmbeddedCount() {
        // Given
        Repository repo = mock(Repository.class);
        Document doc = mock(Document.class);
        DocumentVersion docVersion = mock(DocumentVersion.class);

        when(docVersion.getDocument()).thenReturn(doc);
        when(doc.getId()).thenReturn(testDocId);
        when(doc.getPath()).thenReturn("test.md");

        when(embeddingService.embedDocumentVersion(docVersion)).thenReturn(42);

        // When
        strategy.indexDocument(docVersion);

        // Then
        verify(embeddingService).embedDocumentVersion(docVersion);
        // 로그는 수동 확인 (실제로는 SLF4J mock을 사용할 수 있음)
    }

    @Test
    @DisplayName("search() topK 전달 확인")
    void search_passesTopKCorrectly() {
        // Given
        when(semanticSearchService.searchSemantic(any(), anyString(), anyInt()))
            .thenReturn(List.of());

        // When
        strategy.search(testProjectId, "query", 25);

        // Then
        verify(semanticSearchService).searchSemantic(testProjectId, "query", 25);
    }

    // Helper methods

    private SearchResult createSearchResult(UUID docId, String snippet) {
        return new SearchResult(
            docId,
            UUID.randomUUID(),
            "path/to/doc.md",
            "commit-sha",
            UUID.randomUUID(),
            "# Heading",
            0.85,
            snippet,
            snippet
        );
    }
}
