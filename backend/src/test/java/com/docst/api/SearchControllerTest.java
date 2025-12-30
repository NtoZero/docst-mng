package com.docst.api;

import com.docst.rag.RagMode;
import com.docst.rag.RagSearchStrategy;
import com.docst.service.HybridSearchService;
import com.docst.service.SearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * SearchController 단위 테스트.
 * standaloneSetup을 사용하여 전략 패턴 주입을 정확하게 테스트
 */
@ExtendWith(MockitoExtension.class)
class SearchControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SearchService searchService;

    @Mock
    private HybridSearchService hybridSearchService;

    @Mock
    private RagSearchStrategy pgVectorStrategy;

    @Mock
    private RagSearchStrategy hybridStrategy;

    @BeforeEach
    void setUp() {
        // Setup RagSearchStrategy mocks BEFORE creating controller
        when(pgVectorStrategy.getSupportedMode()).thenReturn(RagMode.PGVECTOR);
        when(hybridStrategy.getSupportedMode()).thenReturn(RagMode.HYBRID);

        // Create controller with properly configured strategies
        List<RagSearchStrategy> strategies = List.of(pgVectorStrategy, hybridStrategy);
        SearchController controller = new SearchController(searchService, hybridSearchService, strategies);

        // Setup MockMvc with standalone configuration
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("검색 모드=keyword → SearchService.searchByKeyword 호출")
    void search_withKeywordMode_callsSearchService() throws Exception {
        // Given
        UUID projectId = UUID.randomUUID();
        List<SearchService.SearchResult> mockResults = List.of(
            createSearchResult(UUID.randomUUID(), "keyword result")
        );

        when(searchService.searchByKeyword(any(), anyString(), anyInt())).thenReturn(mockResults);

        // When & Then
        mockMvc.perform(get("/api/projects/{projectId}/search", projectId)
                .param("q", "test query")
                .param("mode", "keyword")
                .param("topK", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].snippet").value("keyword result"));

        verify(searchService).searchByKeyword(projectId, "test query", 10);
        verify(hybridSearchService, never()).hybridSearch(any(), anyString(), anyInt());
    }

    @Test
    @DisplayName("검색 모드=semantic → RagSearchStrategy.search 호출 (Phase 4)")
    void search_withSemanticMode_callsRagSearchStrategy() throws Exception {
        // Given
        UUID projectId = UUID.randomUUID();
        List<SearchService.SearchResult> mockResults = List.of(
            createSearchResult(UUID.randomUUID(), "semantic result")
        );

        // Phase 4: semantic 모드는 PgVectorSearchStrategy를 사용
        when(pgVectorStrategy.search(any(), anyString(), anyInt())).thenReturn(mockResults);

        // When & Then
        mockMvc.perform(get("/api/projects/{projectId}/search", projectId)
                .param("q", "test query")
                .param("mode", "semantic")
                .param("topK", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].snippet").value("semantic result"));

        verify(pgVectorStrategy).search(projectId, "test query", 5);
        verify(searchService, never()).searchByKeyword(any(), anyString(), anyInt());
    }

    @Test
    @DisplayName("검색 모드=hybrid → RagSearchStrategy.search 호출 (Phase 4)")
    void search_withHybridMode_callsRagSearchStrategy() throws Exception {
        // Given
        UUID projectId = UUID.randomUUID();
        List<SearchService.SearchResult> mockResults = List.of(
            createSearchResult(UUID.randomUUID(), "hybrid result")
        );

        // Phase 4: hybrid 모드는 HybridSearchStrategy를 사용 (PgVector + Neo4j)
        when(hybridStrategy.search(any(), anyString(), anyInt())).thenReturn(mockResults);

        // When & Then
        mockMvc.perform(get("/api/projects/{projectId}/search", projectId)
                .param("q", "test query")
                .param("mode", "hybrid")
                .param("topK", "15"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].snippet").value("hybrid result"));

        verify(hybridStrategy).search(projectId, "test query", 15);
        verify(searchService, never()).searchByKeyword(any(), anyString(), anyInt());
    }

    @Test
    @DisplayName("mode 파라미터 없음 → 기본값 keyword 검색")
    void search_withoutModeParameter_defaultsToKeyword() throws Exception {
        // Given
        UUID projectId = UUID.randomUUID();
        List<SearchService.SearchResult> mockResults = List.of(
            createSearchResult(UUID.randomUUID(), "default result")
        );

        when(searchService.searchByKeyword(any(), anyString(), anyInt())).thenReturn(mockResults);

        // When & Then
        mockMvc.perform(get("/api/projects/{projectId}/search", projectId)
                .param("q", "test query"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());

        verify(searchService).searchByKeyword(eq(projectId), eq("test query"), eq(10)); // default topK=10
    }

    @Test
    @DisplayName("topK 파라미터 없음 → 기본값 10")
    void search_withoutTopKParameter_defaultsTo10() throws Exception {
        // Given
        UUID projectId = UUID.randomUUID();
        when(searchService.searchByKeyword(any(), anyString(), anyInt())).thenReturn(List.of());

        // When & Then
        mockMvc.perform(get("/api/projects/{projectId}/search", projectId)
                .param("q", "test query")
                .param("mode", "keyword"))
            .andExpect(status().isOk());

        verify(searchService).searchByKeyword(projectId, "test query", 10);
    }

    @Test
    @DisplayName("잘못된 mode 값 → PGVECTOR 전략으로 폴백 (Phase 4)")
    void search_withInvalidMode_fallsToPgVectorStrategy() throws Exception {
        // Given
        UUID projectId = UUID.randomUUID();
        when(pgVectorStrategy.search(any(), anyString(), anyInt())).thenReturn(List.of());

        // When & Then
        mockMvc.perform(get("/api/projects/{projectId}/search", projectId)
                .param("q", "test query")
                .param("mode", "invalid-mode"))
            .andExpect(status().isOk());

        // Phase 4: 잘못된 mode는 PGVECTOR 전략으로 폴백
        verify(pgVectorStrategy).search(projectId, "test query", 10);
    }

    @Test
    @DisplayName("검색 결과 없음 → 빈 배열 반환")
    void search_withEmptyResults_returnsEmptyArray() throws Exception {
        // Given
        UUID projectId = UUID.randomUUID();
        when(searchService.searchByKeyword(any(), anyString(), anyInt())).thenReturn(List.of());

        // When & Then
        mockMvc.perform(get("/api/projects/{projectId}/search", projectId)
                .param("q", "nonexistent query"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("다중 검색 결과 → 모든 결과 배열로 반환")
    void search_withMultipleResults_returnsAllResults() throws Exception {
        // Given
        UUID projectId = UUID.randomUUID();
        List<SearchService.SearchResult> mockResults = List.of(
            createSearchResult(UUID.randomUUID(), "result 1"),
            createSearchResult(UUID.randomUUID(), "result 2"),
            createSearchResult(UUID.randomUUID(), "result 3")
        );

        when(searchService.searchByKeyword(any(), anyString(), anyInt())).thenReturn(mockResults);

        // When & Then
        mockMvc.perform(get("/api/projects/{projectId}/search", projectId)
                .param("q", "test query"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].snippet").value("result 1"))
            .andExpect(jsonPath("$[1].snippet").value("result 2"))
            .andExpect(jsonPath("$[2].snippet").value("result 3"));
    }

    @Test
    @DisplayName("응답에 headingPath, chunkId, score, snippet 포함 확인")
    void search_includesHeadingPath_inResponse() throws Exception {
        // Given
        UUID projectId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        UUID chunkId = UUID.randomUUID();

        SearchService.SearchResult resultWithHeading = new SearchService.SearchResult(
            docId,
            UUID.randomUUID(),
            "path/to/doc.md",
            "commit-sha",
            chunkId,
            "# Main > ## Section",
            0.95,
            "snippet content",
            "highlighted snippet"
        );

        when(searchService.searchByKeyword(any(), anyString(), anyInt()))
            .thenReturn(List.of(resultWithHeading));

        // When & Then
        mockMvc.perform(get("/api/projects/{projectId}/search", projectId)
                .param("q", "test query"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].headingPath").value("# Main > ## Section"))
            .andExpect(jsonPath("$[0].chunkId").value(chunkId.toString()))
            .andExpect(jsonPath("$[0].score").value(0.95))
            .andExpect(jsonPath("$[0].snippet").value("snippet content"))
            .andExpect(jsonPath("$[0].highlightedSnippet").value("highlighted snippet"));
    }

    @Test
    @DisplayName("mode 대소문자 무관 처리 (SEMANTIC → semantic) (Phase 4)")
    void search_caseSensitiveMode_handledCorrectly() throws Exception {
        // Given
        UUID projectId = UUID.randomUUID();
        when(pgVectorStrategy.search(any(), anyString(), anyInt())).thenReturn(List.of());

        // When & Then - uppercase mode
        mockMvc.perform(get("/api/projects/{projectId}/search", projectId)
                .param("q", "test")
                .param("mode", "SEMANTIC"))
            .andExpect(status().isOk());

        // Phase 4: SEMANTIC 모드는 PgVectorSearchStrategy 사용
        verify(pgVectorStrategy).search(projectId, "test", 10);
    }

    // Helper method

    private SearchService.SearchResult createSearchResult(UUID docId, String snippet) {
        return new SearchService.SearchResult(
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
