package com.docst.search.service;

import com.docst.rag.hybrid.FusionParams;
import com.docst.rag.hybrid.FusionStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 하이브리드 검색 서비스 (Phase 2-C, Phase 4-D 업데이트).
 * FusionStrategy 패턴으로 키워드 검색과 의미 검색 결과를 융합한다.
 *
 * 지원 융합 전략:
 * - RRF (Reciprocal Rank Fusion)
 * - WeightedSum (가중 합계)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HybridSearchService {

    private final SearchService searchService;
    private final SemanticSearchService semanticSearchService;
    private final List<FusionStrategy> fusionStrategies;

    /** 기본 RRF 상수 K (하위 호환성용) */
    private static final int DEFAULT_RRF_K = 60;

    /**
     * 하이브리드 검색을 수행한다 (기본 RRF 전략 사용).
     * 키워드 검색 + 의미 검색 결과를 RRF로 융합하여 상위 topK 개 반환한다.
     *
     * @param projectId 프로젝트 ID
     * @param query 검색 쿼리
     * @param topK 상위 K개 결과
     * @return 융합된 검색 결과 목록
     */
    public List<SearchService.SearchResult> hybridSearch(UUID projectId, String query, int topK) {
        return hybridSearch(projectId, query, FusionParams.forRrf(DEFAULT_RRF_K, topK), "rrf");
    }

    /**
     * 하이브리드 검색을 수행한다 (동적 융합 전략).
     * Phase 4-D: 프로젝트별 설정에 따른 융합 전략 선택.
     *
     * @param projectId 프로젝트 ID
     * @param query 검색 쿼리
     * @param params 융합 파라미터
     * @param strategyName 융합 전략 이름 (rrf, weighted_sum)
     * @return 융합된 검색 결과 목록
     */
    public List<SearchService.SearchResult> hybridSearch(
        UUID projectId,
        String query,
        FusionParams params,
        String strategyName
    ) {
        // 키워드 검색 (topK * 2개 조회)
        List<SearchService.SearchResult> keywordResults =
            searchService.searchByKeyword(projectId, query, params.topK() * 2);

        // 의미 검색 (topK * 2개 조회)
        List<SearchService.SearchResult> semanticResults =
            semanticSearchService.searchSemantic(projectId, query, params.topK() * 2);

        log.debug("Hybrid search: keyword={}, semantic={}, strategy={}, query='{}'",
            keywordResults.size(), semanticResults.size(), strategyName, query);

        // 융합 전략 선택
        FusionStrategy strategy = getStrategy(strategyName);

        // 융합 수행
        return strategy.fuse(keywordResults, semanticResults, params);
    }

    /**
     * 융합 전략을 이름으로 조회한다.
     *
     * @param name 전략 이름 (예: "rrf", "weighted_sum")
     * @return 융합 전략
     * @throws IllegalArgumentException 전략을 찾을 수 없는 경우
     */
    public FusionStrategy getStrategy(String name) {
        String normalizedName = name != null ? name.toLowerCase() : "rrf";

        return fusionStrategies.stream()
            .filter(s -> s.getName().equalsIgnoreCase(normalizedName))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Unknown fusion strategy: " + name + ". Available: rrf, weighted_sum"));
    }

    /**
     * 사용 가능한 모든 융합 전략 이름 목록을 반환한다.
     *
     * @return 전략 이름 목록
     */
    public List<String> getAvailableStrategies() {
        return fusionStrategies.stream()
            .map(FusionStrategy::getName)
            .toList();
    }
}
