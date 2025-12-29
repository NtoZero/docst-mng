package com.docst.api;

import com.docst.rag.RagMode;
import com.docst.rag.RagSearchStrategy;
import com.docst.service.HybridSearchService;
import com.docst.service.SearchService;
import com.docst.service.SearchService.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SearchController Phase 4 전략 패턴 단위 테스트.
 * Phase 4-B: RagSearchStrategy 통합 검증
 */
@ExtendWith(MockitoExtension.class)
class SearchControllerStrategyTest {

    @Mock
    private SearchService searchService;

    @Mock
    private HybridSearchService hybridSearchService;

    @Mock
    private RagSearchStrategy pgVectorStrategy;

    @Mock
    private RagSearchStrategy neo4jStrategy;

    private SearchController controller;

    @BeforeEach
    void setUp() {
        // Configure strategy mocks
        when(pgVectorStrategy.getSupportedMode()).thenReturn(RagMode.PGVECTOR);
        when(neo4jStrategy.getSupportedMode()).thenReturn(RagMode.NEO4J);

        // Create controller with strategy list
        List<RagSearchStrategy> strategies = List.of(pgVectorStrategy, neo4jStrategy);
        controller = new SearchController(searchService, hybridSearchService, strategies);
    }

    @Test
    @DisplayName("전략 패턴: mode=semantic → PgVectorStrategy 호출")
    void search_semanticMode_usesPgVectorStrategy() {
        // Given
        UUID projectId = UUID.randomUUID();
        List<SearchResult> mockResults = List.of(
            createSearchResult(UUID.randomUUID(), "semantic result via strategy")
        );

        when(pgVectorStrategy.search(any(), anyString(), anyInt())).thenReturn(mockResults);

        // When
        ResponseEntity<List<ApiModels.SearchResultResponse>> response =
            controller.search(projectId, "test query", "semantic", 10);

        // Then
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("semantic result via strategy", response.getBody().get(0).snippet());

        // 전략 패턴 사용 시 PgVectorStrategy 호출 확인
        verify(pgVectorStrategy).search(projectId, "test query", 10);
        verify(neo4jStrategy, never()).search(any(), anyString(), anyInt());
    }

    @Test
    @DisplayName("전략 패턴: mode=graph → Neo4jStrategy 호출")
    void search_graphMode_usesNeo4jStrategy() {
        // Given
        UUID projectId = UUID.randomUUID();
        List<SearchResult> mockResults = List.of(
            createSearchResult(UUID.randomUUID(), "graph result")
        );

        when(neo4jStrategy.search(any(), anyString(), anyInt())).thenReturn(mockResults);

        // When
        ResponseEntity<List<ApiModels.SearchResultResponse>> response =
            controller.search(projectId, "graph query", "graph", 5);

        // Then
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());

        // Neo4jStrategy 호출 확인
        verify(neo4jStrategy).search(projectId, "graph query", 5);
        verify(pgVectorStrategy, never()).search(any(), anyString(), anyInt());
    }

    @Test
    @DisplayName("전략 패턴: mode=auto → PGVECTOR 폴백 (QueryRouter 미구현)")
    void search_autoMode_defaultsToPgVector() {
        // Given: QueryRouter 아직 구현 안됨 → PGVECTOR 폴백
        UUID projectId = UUID.randomUUID();
        List<SearchResult> mockResults = List.of(
            createSearchResult(UUID.randomUUID(), "auto mode result")
        );

        when(pgVectorStrategy.search(any(), anyString(), anyInt())).thenReturn(mockResults);

        // When
        ResponseEntity<List<ApiModels.SearchResultResponse>> response =
            controller.search(projectId, "auto query", "auto", 10);

        // Then
        assertEquals(200, response.getStatusCode().value());

        // Auto mode not implemented yet, defaults to PGVECTOR
        verify(pgVectorStrategy).search(projectId, "auto query", 10);
    }

    @Test
    @DisplayName("레거시 호환성: mode=keyword → SearchService 직접 호출")
    void search_keywordMode_usesLegacySearchService() {
        // Given
        UUID projectId = UUID.randomUUID();
        List<SearchResult> mockResults = List.of(
            createSearchResult(UUID.randomUUID(), "keyword result")
        );

        when(searchService.searchByKeyword(any(), anyString(), anyInt())).thenReturn(mockResults);

        // When
        ResponseEntity<List<ApiModels.SearchResultResponse>> response =
            controller.search(projectId, "keyword query", "keyword", 10);

        // Then
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("keyword result", response.getBody().get(0).snippet());

        // 레거시 SearchService 호출 확인
        verify(searchService).searchByKeyword(projectId, "keyword query", 10);
        verify(pgVectorStrategy, never()).search(any(), anyString(), anyInt());
    }

    @Test
    @DisplayName("mode=hybrid → RagMode.HYBRID 전략 사용 (향후 HybridSearchStrategy)")
    void search_hybridMode_usesHybridStrategy() {
        // Given
        // mode="hybrid"는 determineRagMode에서 RagMode.HYBRID로 변환됨
        // HYBRID 전략이 없으면 PGVECTOR로 폴백
        UUID projectId = UUID.randomUUID();
        List<SearchResult> mockResults = List.of(
            createSearchResult(UUID.randomUUID(), "hybrid fallback result")
        );

        when(pgVectorStrategy.search(any(), anyString(), anyInt())).thenReturn(mockResults);

        // When
        ResponseEntity<List<ApiModels.SearchResultResponse>> response =
            controller.search(projectId, "hybrid query", "hybrid", 10);

        // Then
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("hybrid fallback result", response.getBody().get(0).snippet());

        // HYBRID 전략 없음 → PGVECTOR 폴백
        verify(pgVectorStrategy).search(projectId, "hybrid query", 10);
    }

    @Test
    @DisplayName("determineRagMode: semantic → PGVECTOR")
    void determineRagMode_semantic_returnsPgVector() {
        // Given
        UUID projectId = UUID.randomUUID();
        when(pgVectorStrategy.search(any(), anyString(), anyInt())).thenReturn(List.of());

        // When
        controller.search(projectId, "query", "semantic", 10);

        // Then: PGVECTOR 전략 사용 확인
        verify(pgVectorStrategy).search(projectId, "query", 10);
    }

    @Test
    @DisplayName("determineRagMode: graph → NEO4J")
    void determineRagMode_graph_usesNeo4j() {
        // Given
        UUID projectId = UUID.randomUUID();
        when(neo4jStrategy.search(any(), anyString(), anyInt())).thenReturn(List.of());

        // When
        controller.search(projectId, "query", "graph", 10);

        // Then: NEO4J 전략 사용 확인
        verify(neo4jStrategy).search(projectId, "query", 10);
    }

    @Test
    @DisplayName("mode 대소문자 무관 처리 (SEMANTIC → semantic)")
    void search_caseInsensitiveMode() {
        // Given
        UUID projectId = UUID.randomUUID();
        when(pgVectorStrategy.search(any(), anyString(), anyInt())).thenReturn(List.of());

        // When - uppercase mode
        controller.search(projectId, "query", "SEMANTIC", 10);

        // Then
        verify(pgVectorStrategy).search(projectId, "query", 10);
    }

    // Helper method

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
