package com.docst.rag.hybrid;

import com.docst.service.SearchService.SearchResult;

import java.util.List;

/**
 * 검색 결과 융합 전략 인터페이스.
 * Phase 4-D: 동적 설정을 지원하는 융합 전략 패턴.
 *
 * 구현체:
 * - RrfFusionStrategy: Reciprocal Rank Fusion
 * - WeightedSumFusionStrategy: 가중 합계
 */
public interface FusionStrategy {

    /**
     * 두 검색 결과 목록을 융합한다.
     *
     * @param vectorResults 벡터/키워드 검색 결과
     * @param graphResults 그래프 검색 결과
     * @param params 융합 파라미터
     * @return 융합된 검색 결과
     */
    List<SearchResult> fuse(
        List<SearchResult> vectorResults,
        List<SearchResult> graphResults,
        FusionParams params
    );

    /**
     * 전략 이름을 반환한다.
     * RagConfigDto.HybridConfig.fusionStrategy와 매칭된다.
     *
     * @return 전략 이름 (예: "rrf", "weighted_sum")
     */
    String getName();
}
