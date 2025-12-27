package com.docst.api;

import com.docst.service.HybridSearchService;
import com.docst.service.SearchService;
import com.docst.service.SemanticSearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * SearchController 통합 테스트.
 * REST API 엔드포인트 검증
 */
@WebMvcTest(SearchController.class)
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SearchService searchService;

    @MockBean
    private SemanticSearchService semanticSearchService;

    @MockBean
    private HybridSearchService hybridSearchService;

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
        verify(semanticSearchService, never()).searchSemantic(any(), anyString(), anyInt());
        verify(hybridSearchService, never()).hybridSearch(any(), anyString(), anyInt());
    }

    @Test
    @DisplayName("검색 모드=semantic → SemanticSearchService.searchSemantic 호출")
    void search_withSemanticMode_callsSemanticSearchService() throws Exception {
        // Given
        UUID projectId = UUID.randomUUID();
        List<SearchService.SearchResult> mockResults = List.of(
            createSearchResult(UUID.randomUUID(), "semantic result")
        );

        when(semanticSearchService.searchSemantic(any(), anyString(), anyInt())).thenReturn(mockResults);

        // When & Then
        mockMvc.perform(get("/api/projects/{projectId}/search", projectId)
                .param("q", "test query")
                .param("mode", "semantic")
                .param("topK", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].snippet").value("semantic result"));

        verify(semanticSearchService).searchSemantic(projectId, "test query", 5);
        verify(searchService, never()).searchByKeyword(any(), anyString(), anyInt());
        verify(hybridSearchService, never()).hybridSearch(any(), anyString(), anyInt());
    }

    @Test
    @DisplayName("검색 모드=hybrid → HybridSearchService.hybridSearch 호출")
    void search_withHybridMode_callsHybridSearchService() throws Exception {
        // Given
        UUID projectId = UUID.randomUUID();
        List<SearchService.SearchResult> mockResults = List.of(
            createSearchResult(UUID.randomUUID(), "hybrid result")
        );

        when(hybridSearchService.hybridSearch(any(), anyString(), anyInt())).thenReturn(mockResults);

        // When & Then
        mockMvc.perform(get("/api/projects/{projectId}/search", projectId)
                .param("q", "test query")
                .param("mode", "hybrid")
                .param("topK", "15"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].snippet").value("hybrid result"));

        verify(hybridSearchService).hybridSearch(projectId, "test query", 15);
        verify(searchService, never()).searchByKeyword(any(), anyString(), anyInt());
        verify(semanticSearchService, never()).searchSemantic(any(), anyString(), anyInt());
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
    @DisplayName("잘못된 mode 값 → 기본값 keyword로 폴백")
    void search_withInvalidMode_defaultsToKeyword() throws Exception {
        // Given
        UUID projectId = UUID.randomUUID();
        when(searchService.searchByKeyword(any(), anyString(), anyInt())).thenReturn(List.of());

        // When & Then
        mockMvc.perform(get("/api/projects/{projectId}/search", projectId)
                .param("q", "test query")
                .param("mode", "invalid-mode"))
            .andExpect(status().isOk());

        verify(searchService).searchByKeyword(projectId, "test query", 10);
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
    @DisplayName("mode 대소문자 무관 처리 (SEMANTIC → semantic)")
    void search_caseSensitiveMode_handledCorrectly() throws Exception {
        // Given
        UUID projectId = UUID.randomUUID();
        when(semanticSearchService.searchSemantic(any(), anyString(), anyInt())).thenReturn(List.of());

        // When & Then - uppercase mode
        mockMvc.perform(get("/api/projects/{projectId}/search", projectId)
                .param("q", "test")
                .param("mode", "SEMANTIC"))
            .andExpect(status().isOk());

        verify(semanticSearchService).searchSemantic(projectId, "test", 10);
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
