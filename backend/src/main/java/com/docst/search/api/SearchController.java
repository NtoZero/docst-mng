package com.docst.search.api;

import com.docst.api.ApiModels.SearchResultResponse;
import com.docst.api.ApiModels.SearchResponse;
import com.docst.api.ApiModels.SearchMetadata;
import com.docst.rag.RagMode;
import com.docst.rag.RagSearchStrategy;
import com.docst.rag.hybrid.FusionParams;
import com.docst.search.service.SearchService;
import com.docst.search.service.SearchService.SearchResult;
import com.docst.search.service.HybridSearchService;
import com.docst.search.service.SemanticSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 검색 컨트롤러.
 * 프로젝트 내 문서 검색 기능을 제공한다.
 * Phase 2-C: 키워드, 의미, 하이브리드 검색 지원
 * Phase 4: 전략 패턴 기반 RAG 모드 지원 (PgVector, Neo4j, Hybrid)
 */
@Tag(name = "Search", description = "문서 검색 API (키워드, 의미, 그래프, 하이브리드 검색)")
@RestController
@RequestMapping("/api/projects/{projectId}/search")
@Slf4j
public class SearchController {

    private final SearchService searchService;
    private final HybridSearchService hybridSearchService;
    private final SemanticSearchService semanticSearchService;
    private final Map<RagMode, RagSearchStrategy> strategyMap;

    // Phase 14-A: 기본값 상수
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.3;
    private static final String DEFAULT_FUSION_STRATEGY = "rrf";
    private static final int DEFAULT_RRF_K = 60;
    private static final double DEFAULT_VECTOR_WEIGHT = 0.6;

    /**
     * SearchController 생성자.
     * Spring이 RagSearchStrategy 구현체들을 자동으로 주입하고 전략 맵을 생성한다.
     */
    public SearchController(SearchService searchService,
                           HybridSearchService hybridSearchService,
                           SemanticSearchService semanticSearchService,
                           List<RagSearchStrategy> strategies) {
        this.searchService = searchService;
        this.hybridSearchService = hybridSearchService;
        this.semanticSearchService = semanticSearchService;
        // null 키를 가진 전략은 필터링 (테스트 환경에서 발생 가능)
        this.strategyMap = strategies.stream()
            .filter(s -> s.getSupportedMode() != null)
            .collect(Collectors.toMap(RagSearchStrategy::getSupportedMode, s -> s));
        log.info("Registered RAG strategies: {}", strategyMap.keySet());
    }

    /**
     * 프로젝트 내 문서를 검색한다.
     * Phase 2-C: 키워드, 의미, 하이브리드 검색 지원
     * Phase 4: 그래프 검색, auto 모드 추가
     * Phase 14-A: 시맨틱 서치 파라미터 고도화
     *
     * @param projectId 프로젝트 ID
     * @param query 검색어
     * @param mode 검색 모드 (keyword, semantic, graph, hybrid) - 기본값: semantic
     * @param topK 결과 개수 제한 (기본값: 10)
     * @param similarityThreshold 유사도 임계값 (0.0-1.0, 기본값: 0.3)
     * @param fusionStrategy 융합 전략 (rrf, weighted_sum, 기본값: rrf)
     * @param rrfK RRF 상수 K (기본값: 60)
     * @param vectorWeight 벡터 검색 가중치 (0.0-1.0, 기본값: 0.6)
     * @param keywordWeight 키워드 검색 가중치 (0.0-1.0, 기본값: 0.4)
     * @return 검색 결과 (결과 목록 + 메타데이터)
     */
    @Operation(
            summary = "문서 검색",
            description = "프로젝트 내 문서를 검색합니다. 검색 모드: keyword (키워드), semantic (벡터 검색), graph (그래프 검색), hybrid (하이브리드)"
    )
    @ApiResponse(responseCode = "200", description = "검색 성공")
    @GetMapping
    public ResponseEntity<SearchResponse> search(
            @Parameter(description = "프로젝트 ID") @PathVariable UUID projectId,
            @Parameter(description = "검색어") @RequestParam(name = "q") String query,
            @Parameter(description = "검색 모드 (keyword, semantic, graph, hybrid)") @RequestParam(required = false, defaultValue = "semantic") String mode,
            @Parameter(description = "결과 개수 제한 (기본값: 10)") @RequestParam(required = false, defaultValue = "10") Integer topK,
            @Parameter(description = "유사도 임계값 0.0-1.0 (기본값: 0.3)") @RequestParam(required = false) Double similarityThreshold,
            @Parameter(description = "융합 전략: rrf, weighted_sum (기본값: rrf)") @RequestParam(required = false) String fusionStrategy,
            @Parameter(description = "RRF 상수 K (기본값: 60)") @RequestParam(required = false) Integer rrfK,
            @Parameter(description = "벡터 검색 가중치 0.0-1.0 (기본값: 0.6)") @RequestParam(required = false) Double vectorWeight,
            @Parameter(description = "키워드 검색 가중치 0.0-1.0 (기본값: 0.4)") @RequestParam(required = false) Double keywordWeight
    ) {
        long startTime = System.currentTimeMillis();

        // Phase 14-A: 파라미터 기본값 적용
        double threshold = similarityThreshold != null ? similarityThreshold : DEFAULT_SIMILARITY_THRESHOLD;
        String strategy = fusionStrategy != null ? fusionStrategy : DEFAULT_FUSION_STRATEGY;
        int rrfKValue = rrfK != null ? rrfK : DEFAULT_RRF_K;
        double vecWeight = vectorWeight != null ? vectorWeight : DEFAULT_VECTOR_WEIGHT;
        double kwWeight = keywordWeight != null ? keywordWeight : (1.0 - vecWeight);

        // 검색 모드 결정
        RagMode ragMode = determineRagMode(mode, query);
        log.debug("Search: projectId={}, query='{}', mode={}, ragMode={}, topK={}, threshold={}, strategy={}",
            projectId, query, mode, ragMode, topK, threshold, strategy);

        // 전략 패턴으로 검색 실행
        List<SearchResult> results;
        String actualMode = mode.toLowerCase();

        if ("keyword".equalsIgnoreCase(mode)) {
            // 키워드 검색은 기존 SearchService 사용 (레거시 호환성)
            results = searchService.searchByKeyword(projectId, query, topK);
        } else if ("semantic".equalsIgnoreCase(mode)) {
            // Phase 14-A: 시맨틱 검색에 임계값 적용
            results = semanticSearchService.searchSemantic(projectId, query, topK, threshold);
        } else if ("hybrid".equalsIgnoreCase(mode)) {
            // Phase 14-A: 하이브리드 검색에 융합 파라미터 적용
            FusionParams fusionParams = "weighted_sum".equalsIgnoreCase(strategy)
                ? FusionParams.forWeightedSum(vecWeight, kwWeight, topK)
                : FusionParams.forRrf(rrfKValue, topK);
            results = hybridSearchService.hybridSearch(projectId, query, fusionParams, strategy);
        } else if ("graph".equalsIgnoreCase(mode)) {
            // 그래프 검색은 Neo4j 전략 사용
            RagSearchStrategy graphStrategy = strategyMap.get(RagMode.NEO4J);
            if (graphStrategy != null) {
                results = graphStrategy.search(projectId, query, topK);
            } else {
                log.warn("Neo4j strategy not available, falling back to semantic search");
                results = semanticSearchService.searchSemantic(projectId, query, topK, threshold);
                actualMode = "semantic";
            }
        } else {
            // 기타 모드: Phase 4 전략 패턴 사용
            RagSearchStrategy ragStrategy = strategyMap.get(ragMode);
            if (ragStrategy == null) {
                log.warn("Strategy not found for mode {}, falling back to PGVECTOR", ragMode);
                ragStrategy = strategyMap.get(RagMode.PGVECTOR);
            }
            results = ragStrategy.search(projectId, query, topK);
        }

        long queryTimeMs = System.currentTimeMillis() - startTime;

        // 검색 결과 변환
        List<SearchResultResponse> resultResponses = results.stream()
                .map(r -> new SearchResultResponse(
                        r.documentId(),
                        r.repositoryId(),
                        r.path(),
                        r.commitSha(),
                        r.chunkId(),
                        r.headingPath(),
                        r.score(),
                        r.snippet(),
                        r.highlightedSnippet()
                ))
                .toList();

        // Phase 14-A: 메타데이터 생성
        SearchMetadata metadata = new SearchMetadata(
                actualMode,
                resultResponses.size(),
                "keyword".equalsIgnoreCase(mode) ? null : threshold,
                "hybrid".equalsIgnoreCase(mode) ? strategy : null,
                queryTimeMs
        );

        return ResponseEntity.ok(new SearchResponse(resultResponses, metadata));
    }

    /**
     * 검색 모드 파라미터를 RAG 모드로 변환.
     * Phase 4: auto 모드는 향후 QueryRouter 추가 시 활성화
     *
     * @param modeParam 검색 모드 파라미터 (keyword, semantic, graph, hybrid, auto)
     * @param query 검색 쿼리 (auto 모드 시 분석용)
     * @return RAG 모드
     */
    private RagMode determineRagMode(String modeParam, String query) {
        return switch (modeParam.toLowerCase()) {
            case "semantic" -> RagMode.PGVECTOR;
            case "graph" -> RagMode.NEO4J;
            case "hybrid" -> RagMode.HYBRID;
            case "auto" -> {
                // TODO: Phase 4-E에서 QueryRouter 추가 후 활성화
                // yield queryRouter.analyzeAndRoute(query);
                log.debug("Auto mode not yet implemented, defaulting to PGVECTOR");
                yield RagMode.PGVECTOR;
            }
            default -> RagMode.PGVECTOR;  // keyword도 PGVECTOR로 (레거시 호환)
        };
    }
}
