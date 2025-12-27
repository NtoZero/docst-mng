package com.docst.api;

import com.docst.api.ApiModels.SearchResultResponse;
import com.docst.service.SearchService;
import com.docst.service.SearchService.SearchResult;
import com.docst.service.SemanticSearchService;
import com.docst.service.HybridSearchService;
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
    @GetMapping
    public ResponseEntity<List<SearchResultResponse>> search(
            @PathVariable UUID projectId,
            @RequestParam(name = "q") String query,
            @RequestParam(required = false, defaultValue = "keyword") String mode,
            @RequestParam(required = false, defaultValue = "10") Integer topK
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
