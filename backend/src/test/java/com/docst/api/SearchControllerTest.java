package com.docst.api;

import com.docst.rag.RagMode;
import com.docst.rag.RagSearchStrategy;
import com.docst.search.api.SearchController;
import com.docst.search.service.HybridSearchService;
import com.docst.search.service.SearchService;
import com.docst.search.service.SemanticSearchService;
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
    private SemanticSearchService semanticSearchService;

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
        SearchController controller = new SearchController(
            searchService, hybridSearchService, semanticSearchService, strategies
        );

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
            .andExpect(jsonPath("$.results").isArray())
            .andExpect(jsonPath("$.results[0].snippet").value("keyword result"));

        verify(searchService).searchByKeyword(projectId, "test query", 10);
        verify(hybridSearchService, never()).hybridSearch(any(), anyString(), anyInt());
    }

    @Test
    @DisplayName("검색 모드=semantic → SemanticSearchService 호출 (Phase 14-A)")
    void search_withSemanticMode_callsSemanticSearchService() throws Exception {
        // Given
        UUID projectId = UUID.randomUUID();
        List<SearchService.SearchResult> mockResults = List.of(
            createSearchResult(UUID.randomUUID(), "semantic result")
        );

        when(semanticSearchService.searchSemantic(any(), anyString(), anyInt(), anyDouble()))
            .thenReturn(mockResults);

        // When & Then
        mockMvc.perform(get("/api/projects/{projectId}/search", projectId)
                .param("q", "test query")
                .param("mode", "semantic")
                .param("topK", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.results").isArray())
            .andExpect(jsonPath("$.results[0].snippet").value("semantic result"));

        verify(semanticSearchService).searchSemantic(eq(projectId), eq("test query"), eq(5), anyDouble());
        verify(searchService, never()).searchByKeyword(any(), anyString(), anyInt());
    }

    @Test
    @DisplayName("검색 모드=hybrid → HybridSearchService 호출 (Phase 14-A)")
    void search_withHybridMode_callsHybridSearchService() throws Exception {
        // Given
        UUID projectId = UUID.randomUUID();
        List<SearchService.SearchResult> mockResults = List.of(
            createSearchResult(UUID.randomUUID(), "hybrid result")
        );

        when(hybridSearchService.hybridSearch(any(), anyString(), any(), anyString()))
            .thenReturn(mockResults);

        // When & Then
        mockMvc.perform(get("/api/projects/{projectId}/search", projectId)
                .param("q", "test query")
                .param("mode", "hybrid")
                .param("topK", "15"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.results").isArray())
            .andExpect(jsonPath("$.results[0].snippet").value("hybrid result"));

        verify(hybridSearchService).hybridSearch(eq(projectId), eq("test query"), any(), anyString());
        verify(searchService, never()).searchByKeyword(any(), anyString(), anyInt());
    }

    @Test
    @DisplayName("mode 파라미터 없음 → 기본값 semantic 검색")
    void search_withoutModeParameter_defaultsToSemantic() throws Exception {
        // Given
        UUID projectId = UUID.randomUUID();
        List<SearchService.SearchResult> mockResults = List.of(
            createSearchResult(UUID.randomUUID(), "default result")
        );

        when(semanticSearchService.searchSemantic(any(), anyString(), anyInt(), anyDouble()))
            .thenReturn(mockResults);

        // When & Then
        mockMvc.perform(get("/api/projects/{projectId}/search", projectId)
                .param("q", "test query"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.results").isArray());

        verify(semanticSearchService).searchSemantic(eq(projectId), eq("test query"), eq(10), anyDouble());
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
                .param("q", "nonexistent query")
                .param("mode", "keyword"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.results").isArray())
            .andExpect(jsonPath("$.results").isEmpty());
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
                .param("q", "test query")
                .param("mode", "keyword"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.results").isArray())
            .andExpect(jsonPath("$.results.length()").value(3))
            .andExpect(jsonPath("$.results[0].snippet").value("result 1"))
            .andExpect(jsonPath("$.results[1].snippet").value("result 2"))
            .andExpect(jsonPath("$.results[2].snippet").value("result 3"));
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
                .param("q", "test query")
                .param("mode", "keyword"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.results[0].headingPath").value("# Main > ## Section"))
            .andExpect(jsonPath("$.results[0].chunkId").value(chunkId.toString()))
            .andExpect(jsonPath("$.results[0].score").value(0.95))
            .andExpect(jsonPath("$.results[0].snippet").value("snippet content"))
            .andExpect(jsonPath("$.results[0].highlightedSnippet").value("highlighted snippet"));
    }

    @Test
    @DisplayName("mode 대소문자 무관 처리 (SEMANTIC → semantic) (Phase 14-A)")
    void search_caseSensitiveMode_handledCorrectly() throws Exception {
        // Given
        UUID projectId = UUID.randomUUID();
        when(semanticSearchService.searchSemantic(any(), anyString(), anyInt(), anyDouble()))
            .thenReturn(List.of());

        // When & Then - uppercase mode
        mockMvc.perform(get("/api/projects/{projectId}/search", projectId)
                .param("q", "test")
                .param("mode", "SEMANTIC"))
            .andExpect(status().isOk());

        verify(semanticSearchService).searchSemantic(eq(projectId), eq("test"), eq(10), anyDouble());
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
