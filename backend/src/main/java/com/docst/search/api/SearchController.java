package com.docst.search.api;

import com.docst.api.ApiModels.SearchResultResponse;
import com.docst.rag.RagMode;
import com.docst.rag.RagSearchStrategy;
import com.docst.search.service.SearchService;
import com.docst.search.service.SearchService.SearchResult;
import com.docst.search.service.HybridSearchService;
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
    private final Map<RagMode, RagSearchStrategy> strategyMap;

    /**
     * SearchController 생성자.
     * Spring이 RagSearchStrategy 구현체들을 자동으로 주입하고 전략 맵을 생성한다.
     */
    public SearchController(SearchService searchService,
                           HybridSearchService hybridSearchService,
                           List<RagSearchStrategy> strategies) {
        this.searchService = searchService;
        this.hybridSearchService = hybridSearchService;
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
     *
     * @param projectId 프로젝트 ID
     * @param query 검색어
     * @param mode 검색 모드 (keyword, semantic, graph, hybrid, auto) - 기본값: keyword
     * @param topK 결과 개수 제한 (기본값: 10)
     * @return 검색 결과 목록
     */
    @Operation(
            summary = "문서 검색",
            description = "프로젝트 내 문서를 검색합니다. 검색 모드: keyword (키워드), semantic (벡터 검색), graph (그래프 검색), hybrid (하이브리드), auto (자동 선택)"
    )
    @ApiResponse(responseCode = "200", description = "검색 성공")
    @GetMapping
    public ResponseEntity<List<SearchResultResponse>> search(
            @Parameter(description = "프로젝트 ID") @PathVariable UUID projectId,
            @Parameter(description = "검색어") @RequestParam(name = "q") String query,
            @Parameter(description = "검색 모드 (keyword, semantic, graph, hybrid, auto)") @RequestParam(required = false, defaultValue = "keyword") String mode,
            @Parameter(description = "결과 개수 제한 (기본값: 10)") @RequestParam(required = false, defaultValue = "10") Integer topK
    ) {
        // 검색 모드 결정
        RagMode ragMode = determineRagMode(mode, query);
        log.debug("Search: projectId={}, query='{}', mode={}, ragMode={}, topK={}",
            projectId, query, mode, ragMode, topK);

        // 전략 패턴으로 검색 실행
        List<SearchResult> results;
        if ("keyword".equalsIgnoreCase(mode)) {
            // 키워드 검색은 기존 SearchService 사용 (레거시 호환성)
            results = searchService.searchByKeyword(projectId, query, topK);
        } else if ("hybrid".equalsIgnoreCase(mode) && ragMode == RagMode.PGVECTOR) {
            // 기존 하이브리드 검색 (키워드 + 벡터)은 HybridSearchService 사용
            results = hybridSearchService.hybridSearch(projectId, query, topK);
        } else {
            // Phase 4 전략 패턴 사용
            RagSearchStrategy strategy = strategyMap.get(ragMode);
            if (strategy == null) {
                // 폴백: PgVector
                log.warn("Strategy not found for mode {}, falling back to PGVECTOR", ragMode);
                strategy = strategyMap.get(RagMode.PGVECTOR);
            }
            results = strategy.search(projectId, query, topK);
        }

        List<SearchResultResponse> response = results.stream()
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

        return ResponseEntity.ok(response);
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
