package com.docst.api;

import com.docst.api.ApiModels.SearchResultResponse;
import com.docst.service.SearchService;
import com.docst.service.SearchService.SearchResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

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
