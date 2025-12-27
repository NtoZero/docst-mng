package com.docst.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 하이브리드 검색 서비스 (Phase 2-C).
 * RRF (Reciprocal Rank Fusion) 알고리즘으로 키워드 검색과 의미 검색 결과를 융합한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HybridSearchService {

    private final SearchService searchService;
    private final SemanticSearchService semanticSearchService;

    /** RRF 상수 K (Reciprocal Rank Fusion constant) */
    private static final int RRF_K = 60;

    /**
     * 하이브리드 검색을 수행한다.
     * 키워드 검색 + 의미 검색 결과를 RRF로 융합하여 상위 topK 개 반환한다.
     *
     * @param projectId 프로젝트 ID
     * @param query 검색 쿼리
     * @param topK 상위 K개 결과
     * @return 융합된 검색 결과 목록
     */
    public List<SearchService.SearchResult> hybridSearch(UUID projectId, String query, int topK) {
        // 키워드 검색 (topK * 2개 조회)
        List<SearchService.SearchResult> keywordResults =
            searchService.searchByKeyword(projectId, query, topK * 2);

        // 의미 검색 (topK * 2개 조회)
        List<SearchService.SearchResult> semanticResults =
            semanticSearchService.searchSemantic(projectId, query, topK * 2);

        log.debug("Hybrid search: keyword={}, semantic={}, query='{}'",
            keywordResults.size(), semanticResults.size(), query);

        // RRF 점수 계산 및 병합
        Map<UUID, RRFResult> rrfScores = new HashMap<>();

        // 키워드 결과 점수 추가
        for (int i = 0; i < keywordResults.size(); i++) {
            SearchService.SearchResult result = keywordResults.get(i);
            UUID id = getResultId(result);
            double rrfScore = 1.0 / (RRF_K + i + 1);

            rrfScores.merge(id, new RRFResult(id, rrfScore, result),
                (existing, newVal) -> new RRFResult(id, existing.score + newVal.score, result));
        }

        // 의미 결과 점수 추가
        for (int i = 0; i < semanticResults.size(); i++) {
            SearchService.SearchResult result = semanticResults.get(i);
            UUID id = getResultId(result);
            double rrfScore = 1.0 / (RRF_K + i + 1);

            rrfScores.merge(id, new RRFResult(id, rrfScore, result),
                (existing, newVal) -> new RRFResult(id, existing.score + newVal.score, existing.result));
        }

        // RRF 점수 기준 정렬 및 상위 topK 반환
        return rrfScores.values().stream()
            .sorted(Comparator.comparingDouble((RRFResult r) -> r.score).reversed())
            .limit(topK)
            .map(r -> new SearchService.SearchResult(
                r.result.documentId(),
                r.result.repositoryId(),
                r.result.path(),
                r.result.commitSha(),
                r.result.chunkId(),
                r.result.headingPath(),
                r.score,  // RRF 융합 점수
                r.result.snippet(),
                r.result.highlightedSnippet()
            ))
            .toList();
    }

    /**
     * SearchResult에서 고유 ID를 추출한다.
     * chunkId가 있으면 chunkId, 없으면 documentId 사용.
     *
     * @param result 검색 결과
     * @return 고유 ID
     */
    private UUID getResultId(SearchService.SearchResult result) {
        return result.chunkId() != null ? result.chunkId() : result.documentId();
    }

    /**
     * RRF 결과를 나타내는 내부 레코드.
     *
     * @param id 결과 ID (chunkId 또는 documentId)
     * @param score RRF 융합 점수
     * @param result 원본 SearchResult
     */
    private record RRFResult(
        UUID id,
        double score,
        SearchService.SearchResult result
    ) {}
}
