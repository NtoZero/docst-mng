package com.docst.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.docst.api.ApiModels.SearchResultResponse;
import com.docst.store.Document;
import com.docst.store.DocumentVersion;
import com.docst.store.InMemoryStore;

@RestController
@RequestMapping("/api/projects/{projectId}/search")
public class SearchController {
  private final InMemoryStore store;

  public SearchController(InMemoryStore store) {
    this.store = store;
  }

  @GetMapping
  public ResponseEntity<List<SearchResultResponse>> search(
      @PathVariable UUID projectId,
      @RequestParam(name = "q") String query,
      @RequestParam String mode,
      @RequestParam(required = false) Integer topK
  ) {
    List<DocumentVersion> matches = store.searchDocuments(projectId, query);
    List<SearchResultResponse> results = matches.stream()
        .limit(topK != null ? topK : 10)
        .map(version -> {
          Document doc = store.getDocument(version.documentId()).orElse(null);
          String snippet = buildSnippet(version.content(), query);
          return new SearchResultResponse(
              version.documentId(),
              doc != null ? doc.repositoryId() : null,
              doc != null ? doc.path() : null,
              version.commitSha(),
              UUID.randomUUID(),
              0.9,
              snippet,
              snippet.replace(query, "**" + query + "**")
          );
        })
        .toList();
    return ResponseEntity.ok(results);
  }

  private String buildSnippet(String content, String query) {
    if (content == null) {
      return "";
    }
    int index = content.toLowerCase().indexOf(query.toLowerCase());
    if (index < 0) {
      return content.substring(0, Math.min(content.length(), 120));
    }
    int start = Math.max(0, index - 30);
    int end = Math.min(content.length(), index + query.length() + 30);
    return content.substring(start, end);
  }
}
