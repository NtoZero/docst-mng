package com.docst.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.docst.api.ApiModels.DocumentDetailResponse;
import com.docst.api.ApiModels.DocumentResponse;
import com.docst.api.ApiModels.DocumentVersionDetailResponse;
import com.docst.api.ApiModels.DocumentVersionResponse;
import com.docst.store.DocumentVersion;
import com.docst.store.InMemoryStore;

@RestController
@RequestMapping("/api")
public class DocumentsController {
  private final InMemoryStore store;

  public DocumentsController(InMemoryStore store) {
    this.store = store;
  }

  @GetMapping("/repositories/{repoId}/documents")
  public List<DocumentResponse> listDocuments(
      @PathVariable UUID repoId,
      @RequestParam(required = false) String pathPrefix,
      @RequestParam(required = false) String type
  ) {
    return store.listDocuments(repoId, pathPrefix, type).stream()
        .map(doc -> new DocumentResponse(
            doc.id(),
            doc.repositoryId(),
            doc.path(),
            doc.title(),
            doc.docType(),
            doc.latestCommitSha(),
            doc.createdAt()
        ))
        .toList();
  }

  @GetMapping("/documents/{docId}")
  public ResponseEntity<DocumentDetailResponse> getDocument(@PathVariable UUID docId) {
    Optional<DocumentVersion> version = store.getLatestVersion(docId);
    return store.getDocument(docId)
        .map(doc -> {
          DocumentVersion latest = version.orElse(null);
          return new DocumentDetailResponse(
              doc.id(),
              doc.repositoryId(),
              doc.path(),
              doc.title(),
              doc.docType(),
              doc.latestCommitSha(),
              doc.createdAt(),
              latest != null ? latest.content() : null,
              latest != null ? latest.authorName() : null,
              latest != null ? latest.authorEmail() : null,
              latest != null ? latest.committedAt() : null
          );
        })
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @GetMapping("/documents/{docId}/versions")
  public List<DocumentVersionResponse> listVersions(@PathVariable UUID docId) {
    return store.listDocumentVersions(docId).stream()
        .map(version -> new DocumentVersionResponse(
            version.id(),
            version.documentId(),
            version.commitSha(),
            version.authorName(),
            version.authorEmail(),
            version.committedAt(),
            version.message(),
            version.contentHash()
        ))
        .toList();
  }

  @GetMapping("/documents/{docId}/versions/{commitSha}")
  public ResponseEntity<DocumentVersionDetailResponse> getVersion(
      @PathVariable UUID docId,
      @PathVariable String commitSha
  ) {
    return store.getDocumentVersion(docId, commitSha)
        .map(version -> new DocumentVersionDetailResponse(
            version.id(),
            version.documentId(),
            version.commitSha(),
            version.authorName(),
            version.authorEmail(),
            version.committedAt(),
            version.message(),
            version.contentHash(),
            version.content()
        ))
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @GetMapping(value = "/documents/{docId}/diff", produces = MediaType.TEXT_PLAIN_VALUE)
  public ResponseEntity<String> diffDocument(
      @PathVariable UUID docId,
      @RequestParam String from,
      @RequestParam String to,
      @RequestParam(required = false, defaultValue = "unified") String format
  ) {
    Optional<DocumentVersion> fromVersion = store.getDocumentVersion(docId, from);
    Optional<DocumentVersion> toVersion = store.getDocumentVersion(docId, to);
    if (fromVersion.isEmpty() || toVersion.isEmpty()) {
      return ResponseEntity.notFound().build();
    }
    String diff = buildUnifiedDiff(fromVersion.get().content(), toVersion.get().content(), from, to);
    return ResponseEntity.ok(diff);
  }

  private String buildUnifiedDiff(String fromContent, String toContent, String fromSha, String toSha) {
    List<String> fromLines = fromContent == null ? List.of() : List.of(fromContent.split("\\n", -1));
    List<String> toLines = toContent == null ? List.of() : List.of(toContent.split("\\n", -1));
    StringBuilder builder = new StringBuilder();
    builder.append("--- ").append(fromSha).append("\n");
    builder.append("+++ ").append(toSha).append("\n");
    int max = Math.max(fromLines.size(), toLines.size());
    for (int i = 0; i < max; i++) {
      String fromLine = i < fromLines.size() ? fromLines.get(i) : null;
      String toLine = i < toLines.size() ? toLines.get(i) : null;
      if (fromLine == null) {
        builder.append("+").append(toLine).append("\n");
      } else if (toLine == null) {
        builder.append("-").append(fromLine).append("\n");
      } else if (fromLine.equals(toLine)) {
        builder.append(" ").append(fromLine).append("\n");
      } else {
        builder.append("-").append(fromLine).append("\n");
        builder.append("+").append(toLine).append("\n");
      }
    }
    return builder.toString();
  }
}
