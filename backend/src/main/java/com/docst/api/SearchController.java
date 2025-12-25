package com.docst.api;

import com.docst.api.ApiModels.SearchResultResponse;
import com.docst.service.SearchService;
import com.docst.service.SearchService.SearchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 검색 컨트롤러.
 * 프로젝트 내 문서 검색 기능을 제공한다.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    /**
     * 프로젝트 내 문서를 검색한다.
     * Phase 1에서는 키워드 검색만 지원하며, Phase 2에서 시맨틱 검색이 추가될 예정이다.
     *
     * @param projectId 프로젝트 ID
     * @param query 검색어
     * @param mode 검색 모드 (keyword, semantic)
     * @param topK 결과 개수 제한 (기본값: 10)
     * @return 검색 결과 목록
     */
    @GetMapping
    public ResponseEntity<List<SearchResultResponse>> search(
            @PathVariable UUID projectId,
            @RequestParam(name = "q") String query,
            @RequestParam String mode,
            @RequestParam(required = false, defaultValue = "10") Integer topK
    ) {
        // TODO: Implement semantic search in Phase 2
        List<SearchResult> results = searchService.searchByKeyword(projectId, query, topK);

        List<SearchResultResponse> response = results.stream()
                .map(r -> new SearchResultResponse(
                        r.documentId(),
                        r.repositoryId(),
                        r.path(),
                        r.commitSha(),
                        r.chunkId(),
                        r.score(),
                        r.snippet(),
                        r.highlightedSnippet()
                ))
                .toList();

        return ResponseEntity.ok(response);
    }
}
