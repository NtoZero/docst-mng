package com.docst.mcp;

import com.docst.domain.Document;
import com.docst.domain.DocumentVersion;
import com.docst.domain.SyncJob;
import com.docst.mcp.McpModels.*;
import com.docst.service.DocumentService;
import com.docst.service.SearchService;
import com.docst.service.SyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/mcp/tools")
public class McpController {

    private final DocumentService documentService;
    private final SearchService searchService;
    private final SyncService syncService;

    public McpController(DocumentService documentService,
                          SearchService searchService,
                          SyncService syncService) {
        this.documentService = documentService;
        this.searchService = searchService;
        this.syncService = syncService;
    }

    @PostMapping("/list_documents")
    public ResponseEntity<McpResponse<ListDocumentsResult>> listDocuments(
            @RequestBody ListDocumentsInput input
    ) {
        try {
            List<Document> documents;
            if (input.repositoryId() != null) {
                documents = documentService.findByRepositoryId(
                        input.repositoryId(),
                        input.pathPrefix(),
                        input.type()
                );
            } else if (input.projectId() != null) {
                documents = documentService.findByProjectId(input.projectId());
            } else {
                return ResponseEntity.badRequest()
                        .body(McpResponse.error("Either repositoryId or projectId is required"));
            }

            List<DocumentSummary> summaries = documents.stream()
                    .map(doc -> new DocumentSummary(
                            doc.getId(),
                            doc.getRepository().getId(),
                            doc.getPath(),
                            doc.getTitle(),
                            doc.getDocType().name(),
                            doc.getLatestCommitSha()
                    ))
                    .toList();

            return ResponseEntity.ok(McpResponse.success(new ListDocumentsResult(summaries)));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(McpResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/get_document")
    public ResponseEntity<McpResponse<GetDocumentResult>> getDocument(
            @RequestBody GetDocumentInput input
    ) {
        try {
            Optional<Document> docOpt = documentService.findById(input.documentId());
            if (docOpt.isEmpty()) {
                return ResponseEntity.ok(McpResponse.error("Document not found"));
            }

            Document doc = docOpt.get();
            Optional<DocumentVersion> versionOpt;

            if (input.commitSha() != null) {
                versionOpt = documentService.findVersion(input.documentId(), input.commitSha());
            } else {
                versionOpt = documentService.findLatestVersion(input.documentId());
            }

            if (versionOpt.isEmpty()) {
                return ResponseEntity.ok(McpResponse.error("Version not found"));
            }

            DocumentVersion version = versionOpt.get();
            GetDocumentResult result = new GetDocumentResult(
                    doc.getId(),
                    doc.getRepository().getId(),
                    doc.getPath(),
                    doc.getTitle(),
                    doc.getDocType().name(),
                    version.getCommitSha(),
                    version.getContent(),
                    version.getAuthorName(),
                    version.getCommittedAt()
            );

            return ResponseEntity.ok(McpResponse.success(result));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(McpResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/list_document_versions")
    public ResponseEntity<McpResponse<ListDocumentVersionsResult>> listDocumentVersions(
            @RequestBody ListDocumentVersionsInput input
    ) {
        try {
            List<DocumentVersion> versions = documentService.findVersions(input.documentId());
            List<VersionSummary> summaries = versions.stream()
                    .map(v -> new VersionSummary(
                            v.getCommitSha(),
                            v.getAuthorName(),
                            v.getAuthorEmail(),
                            v.getCommittedAt(),
                            v.getMessage()
                    ))
                    .toList();

            return ResponseEntity.ok(McpResponse.success(new ListDocumentVersionsResult(summaries)));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(McpResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/diff_document")
    public ResponseEntity<McpResponse<DiffDocumentResult>> diffDocument(
            @RequestBody DiffDocumentInput input
    ) {
        try {
            Optional<DocumentVersion> fromOpt = documentService.findVersion(
                    input.documentId(), input.fromCommitSha());
            Optional<DocumentVersion> toOpt = documentService.findVersion(
                    input.documentId(), input.toCommitSha());

            if (fromOpt.isEmpty() || toOpt.isEmpty()) {
                return ResponseEntity.ok(McpResponse.error("Version not found"));
            }

            String diff = buildDiff(
                    fromOpt.get().getContent(),
                    toOpt.get().getContent(),
                    input.fromCommitSha(),
                    input.toCommitSha()
            );

            return ResponseEntity.ok(McpResponse.success(new DiffDocumentResult(diff)));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(McpResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/search_documents")
    public ResponseEntity<McpResponse<SearchDocumentsResult>> searchDocuments(
            @RequestBody SearchDocumentsInput input
    ) {
        try {
            long startTime = System.currentTimeMillis();
            int topK = input.topK() != null ? input.topK() : 10;
            String mode = input.mode() != null ? input.mode() : "keyword";

            // TODO: Implement semantic search in Phase 2
            List<SearchService.SearchResult> results = searchService.searchByKeyword(
                    input.projectId(), input.query(), topK);

            List<SearchHit> hits = results.stream()
                    .map(r -> new SearchHit(
                            r.documentId(),
                            r.path(),
                            null, // title - could be fetched
                            null, // headingPath - Phase 2
                            r.score(),
                            r.snippet(),
                            null  // content - optional
                    ))
                    .toList();

            long elapsed = System.currentTimeMillis() - startTime;
            SearchMetadata metadata = new SearchMetadata(
                    mode,
                    hits.size(),
                    elapsed + "ms"
            );

            return ResponseEntity.ok(McpResponse.success(new SearchDocumentsResult(hits, metadata)));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(McpResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/sync_repository")
    public ResponseEntity<McpResponse<SyncRepositoryResult>> syncRepository(
            @RequestBody SyncRepositoryInput input
    ) {
        try {
            SyncJob job = syncService.startSync(input.repositoryId(), input.branch());
            SyncRepositoryResult result = new SyncRepositoryResult(
                    job.getId(),
                    job.getStatus().name()
            );
            return ResponseEntity.ok(McpResponse.success(result));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(McpResponse.error(e.getMessage()));
        }
    }

    private String buildDiff(String from, String to, String fromSha, String toSha) {
        List<String> fromLines = from == null ? List.of() : List.of(from.split("\\n", -1));
        List<String> toLines = to == null ? List.of() : List.of(to.split("\\n", -1));

        StringBuilder sb = new StringBuilder();
        sb.append("--- ").append(fromSha).append("\n");
        sb.append("+++ ").append(toSha).append("\n");

        int max = Math.max(fromLines.size(), toLines.size());
        for (int i = 0; i < max; i++) {
            String fromLine = i < fromLines.size() ? fromLines.get(i) : null;
            String toLine = i < toLines.size() ? toLines.get(i) : null;

            if (fromLine == null) {
                sb.append("+").append(toLine).append("\n");
            } else if (toLine == null) {
                sb.append("-").append(fromLine).append("\n");
            } else if (fromLine.equals(toLine)) {
                sb.append(" ").append(fromLine).append("\n");
            } else {
                sb.append("-").append(fromLine).append("\n");
                sb.append("+").append(toLine).append("\n");
            }
        }
        return sb.toString();
    }
}
