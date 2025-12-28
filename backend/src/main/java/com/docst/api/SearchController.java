package com.docst.api;

import com.docst.api.ApiModels.SearchResultResponse;
import com.docst.service.SearchService;
import com.docst.service.SearchService.SearchResult;
import com.docst.service.SemanticSearchService;
import com.docst.service.HybridSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 검색 컨트롤러.
 * 프로젝트 내 문서 검색 기능을 제공한다.
 * Phase 2-C: 키워드, 의미, 하이브리드 검색 지원
 */
@Tag(name = "Search", description = "문서 검색 API (키워드, 의미, 하이브리드 검색)")
@RestController
@RequestMapping("/api/projects/{projectId}/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;
    private final SemanticSearchService semanticSearchService;
    private final HybridSearchService hybridSearchService;

    /**
     * 프로젝트 내 문서를 검색한다.
     * Phase 2-C: 키워드, 의미, 하이브리드 검색 지원
     *
     * @param projectId 프로젝트 ID
     * @param query 검색어
     * @param mode 검색 모드 (keyword, semantic, hybrid) - 기본값: keyword
     * @param topK 결과 개수 제한 (기본값: 10)
     * @return 검색 결과 목록
     */
    @Operation(
            summary = "문서 검색",
            description = "프로젝트 내 문서를 검색합니다. 검색 모드: keyword (키워드), semantic (의미 검색), hybrid (하이브리드)"
    )
    @ApiResponse(responseCode = "200", description = "검색 성공")
    @GetMapping
    public ResponseEntity<List<SearchResultResponse>> search(
            @Parameter(description = "프로젝트 ID") @PathVariable UUID projectId,
            @Parameter(description = "검색어") @RequestParam(name = "q") String query,
            @Parameter(description = "검색 모드 (keyword, semantic, hybrid)") @RequestParam(required = false, defaultValue = "keyword") String mode,
            @Parameter(description = "결과 개수 제한 (기본값: 10)") @RequestParam(required = false, defaultValue = "10") Integer topK
    ) {
        // 검색 모드에 따라 다른 서비스 호출
        List<SearchResult> results = switch (mode.toLowerCase()) {
            case "semantic" -> semanticSearchService.searchSemantic(projectId, query, topK);
            case "hybrid" -> hybridSearchService.hybridSearch(projectId, query, topK);
            default -> searchService.searchByKeyword(projectId, query, topK);
        };

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
}
