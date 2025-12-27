package com.docst.service;

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
 * HybridSearchService 단위 테스트.
 * RRF (Reciprocal Rank Fusion) 알고리즘 검증
 */
@ExtendWith(MockitoExtension.class)
class HybridSearchServiceTest {

    @Mock
    private SearchService searchService;

    @Mock
    private SemanticSearchService semanticSearchService;

    @InjectMocks
    private HybridSearchService hybridSearchService;

    private UUID testProjectId;
    private UUID testDocId1;
    private UUID testDocId2;
    private UUID testDocId3;
    private UUID testChunkId1;
    private UUID testChunkId2;
    private UUID testChunkId3;

    @BeforeEach
    void setUp() {
        testProjectId = UUID.randomUUID();
        testDocId1 = UUID.randomUUID();
        testDocId2 = UUID.randomUUID();
        testDocId3 = UUID.randomUUID();
        testChunkId1 = UUID.randomUUID();
        testChunkId2 = UUID.randomUUID();
        testChunkId3 = UUID.randomUUID();
    }

    @Test
    @DisplayName("키워드/시맨틱 모두 빈 결과 → 빈 리스트 반환")
    void hybridSearch_bothResultsEmpty_returnsEmptyList() {
        // Given
        when(searchService.searchByKeyword(any(), anyString(), anyInt())).thenReturn(List.of());
        when(semanticSearchService.searchSemantic(any(), anyString(), anyInt())).thenReturn(List.of());

        // When
        List<SearchService.SearchResult> results = hybridSearchService.hybridSearch(testProjectId, "query", 10);

        // Then
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("키워드 결과만 있음 → RRF 점수로 정렬된 결과")
    void hybridSearch_onlyKeywordResults_returnsRankedResults() {
        // Given
        List<SearchService.SearchResult> keywordResults = List.of(
            createSearchResult(testDocId1, testChunkId1, 0.9, "Keyword result 1"),
            createSearchResult(testDocId2, testChunkId2, 0.7, "Keyword result 2")
        );

        when(searchService.searchByKeyword(any(), anyString(), anyInt())).thenReturn(keywordResults);
        when(semanticSearchService.searchSemantic(any(), anyString(), anyInt())).thenReturn(List.of());

        // When
        List<SearchService.SearchResult> results = hybridSearchService.hybridSearch(testProjectId, "query", 10);

        // Then
        assertEquals(2, results.size());
        // First result should have higher RRF score
        assertTrue(results.get(0).score() >= results.get(1).score());
    }

    @Test
    @DisplayName("시맨틱 결과만 있음 → RRF 점수로 정렬된 결과")
    void hybridSearch_onlySemanticResults_returnsRankedResults() {
        // Given
        List<SearchService.SearchResult> semanticResults = List.of(
            createSearchResult(testDocId1, testChunkId1, 0.95, "Semantic result 1"),
            createSearchResult(testDocId2, testChunkId2, 0.85, "Semantic result 2")
        );

        when(searchService.searchByKeyword(any(), anyString(), anyInt())).thenReturn(List.of());
        when(semanticSearchService.searchSemantic(any(), anyString(), anyInt())).thenReturn(semanticResults);

        // When
        List<SearchService.SearchResult> results = hybridSearchService.hybridSearch(testProjectId, "query", 10);

        // Then
        assertEquals(2, results.size());
        assertTrue(results.get(0).score() >= results.get(1).score());
    }

    @Test
    @DisplayName("중복 문서 → RRF로 병합 (양쪽 등장 문서가 최상위)")
    void hybridSearch_overlappingResults_mergesWithRRF() {
        // Given: Same document appears in both keyword and semantic results
        List<SearchService.SearchResult> keywordResults = List.of(
            createSearchResult(testDocId1, testChunkId1, 0.9, "Result 1"),  // Rank 0
            createSearchResult(testDocId2, testChunkId2, 0.7, "Result 2")   // Rank 1
        );

        List<SearchService.SearchResult> semanticResults = List.of(
            createSearchResult(testDocId1, testChunkId1, 0.95, "Result 1"), // Rank 0 (overlap)
            createSearchResult(testDocId3, testChunkId3, 0.85, "Result 3")  // Rank 1
        );

        when(searchService.searchByKeyword(any(), anyString(), anyInt())).thenReturn(keywordResults);
        when(semanticSearchService.searchSemantic(any(), anyString(), anyInt())).thenReturn(semanticResults);

        // When
        List<SearchService.SearchResult> results = hybridSearchService.hybridSearch(testProjectId, "query", 10);

        // Then
        assertEquals(3, results.size()); // 3 unique documents

        // testDocId1 appears in both, so should have highest RRF score
        // RRF score for testDocId1: 1/(60+0+1) + 1/(60+0+1) = 2/61 ≈ 0.0328
        // RRF score for testDocId2: 1/(60+1+1) = 1/62 ≈ 0.0161
        // RRF score for testDocId3: 1/(60+1+1) = 1/62 ≈ 0.0161

        assertEquals(testDocId1, results.get(0).documentId()); // Highest RRF score
    }

    @Test
    @DisplayName("topK 제한 적용 확인")
    void hybridSearch_respectsTopKLimit() {
        // Given
        List<SearchService.SearchResult> keywordResults = List.of(
            createSearchResult(testDocId1, testChunkId1, 0.9, "Result 1"),
            createSearchResult(testDocId2, testChunkId2, 0.8, "Result 2"),
            createSearchResult(testDocId3, testChunkId3, 0.7, "Result 3")
        );

        when(searchService.searchByKeyword(any(), anyString(), anyInt())).thenReturn(keywordResults);
        when(semanticSearchService.searchSemantic(any(), anyString(), anyInt())).thenReturn(List.of());

        // When
        List<SearchService.SearchResult> results = hybridSearchService.hybridSearch(testProjectId, "query", 2);

        // Then
        assertEquals(2, results.size()); // Limited to topK=2
    }

    @Test
    @DisplayName("RRF 점수 계산 검증 (1/(60+rank+1))")
    void hybridSearch_rrfScoreCalculation() {
        // Given
        UUID uniqueDocId1 = UUID.randomUUID();
        UUID uniqueDocId2 = UUID.randomUUID();
        UUID uniqueChunkId1 = UUID.randomUUID();
        UUID uniqueChunkId2 = UUID.randomUUID();

        List<SearchService.SearchResult> keywordResults = List.of(
            createSearchResult(uniqueDocId1, uniqueChunkId1, 0.9, "Doc1")  // Rank 0
        );

        List<SearchService.SearchResult> semanticResults = List.of(
            createSearchResult(uniqueDocId2, uniqueChunkId2, 0.95, "Doc2") // Rank 0
        );

        when(searchService.searchByKeyword(any(), anyString(), anyInt())).thenReturn(keywordResults);
        when(semanticSearchService.searchSemantic(any(), anyString(), anyInt())).thenReturn(semanticResults);

        // When
        List<SearchService.SearchResult> results = hybridSearchService.hybridSearch(testProjectId, "query", 10);

        // Then
        assertEquals(2, results.size());

        // Both should have same RRF score: 1/(60+0+1) = 1/61 ≈ 0.01639
        double expectedScore = 1.0 / 61.0;
        assertEquals(expectedScore, results.get(0).score(), 0.0001);
        assertEquals(expectedScore, results.get(1).score(), 0.0001);
    }

    @Test
    @DisplayName("서비스 호출 시 topK*2 배수 적용 확인")
    void hybridSearch_callsServicesWithCorrectMultiplier() {
        // Given
        when(searchService.searchByKeyword(any(), anyString(), anyInt())).thenReturn(List.of());
        when(semanticSearchService.searchSemantic(any(), anyString(), anyInt())).thenReturn(List.of());

        // When
        hybridSearchService.hybridSearch(testProjectId, "test query", 10);

        // Then
        // Should request topK * 2 from each service
        verify(searchService).searchByKeyword(testProjectId, "test query", 20);
        verify(semanticSearchService).searchSemantic(testProjectId, "test query", 20);
    }

    @Test
    @DisplayName("headingPath 보존 확인")
    void hybridSearch_preservesHeadingPath() {
        // Given
        String headingPath = "# Main > ## Section";
        List<SearchService.SearchResult> keywordResults = List.of(
            new SearchService.SearchResult(
                testDocId1,
                UUID.randomUUID(),
                "path/to/doc.md",
                "abc123",
                testChunkId1,
                headingPath,
                0.9,
                "snippet",
                "highlighted"
            )
        );

        when(searchService.searchByKeyword(any(), anyString(), anyInt())).thenReturn(keywordResults);
        when(semanticSearchService.searchSemantic(any(), anyString(), anyInt())).thenReturn(List.of());

        // When
        List<SearchService.SearchResult> results = hybridSearchService.hybridSearch(testProjectId, "query", 10);

        // Then
        assertEquals(1, results.size());
        assertEquals(headingPath, results.get(0).headingPath());
    }

    @Test
    @DisplayName("chunkId가 null인 결과 처리 (문서 레벨 결과)")
    void hybridSearch_handlesNullChunkIds() {
        // Given: Results without chunk IDs (document-level results)
        List<SearchService.SearchResult> keywordResults = List.of(
            createSearchResultWithoutChunk(testDocId1, 0.9, "Result 1"),
            createSearchResultWithoutChunk(testDocId2, 0.8, "Result 2")
        );

        when(searchService.searchByKeyword(any(), anyString(), anyInt())).thenReturn(keywordResults);
        when(semanticSearchService.searchSemantic(any(), anyString(), anyInt())).thenReturn(List.of());

        // When
        List<SearchService.SearchResult> results = hybridSearchService.hybridSearch(testProjectId, "query", 10);

        // Then
        assertEquals(2, results.size());
        assertNull(results.get(0).chunkId());
        assertNull(results.get(1).chunkId());
    }

    // Helper methods

    private SearchService.SearchResult createSearchResult(
            UUID docId, UUID chunkId, double score, String snippet) {
        return new SearchService.SearchResult(
            docId,
            UUID.randomUUID(),  // repositoryId
            "path/to/doc.md",
            "commit-sha",
            chunkId,
            "# Heading",
            score,
            snippet,
            snippet
        );
    }

    private SearchService.SearchResult createSearchResultWithoutChunk(
            UUID docId, double score, String snippet) {
        return new SearchService.SearchResult(
            docId,
            UUID.randomUUID(),
            "path/to/doc.md",
            "commit-sha",
            null,  // No chunk ID
            null,  // No heading path
            score,
            snippet,
            snippet
        );
    }
}
