package com.docst.mcp.tools;

import com.docst.auth.SecurityUtils;
import com.docst.domain.Document;
import com.docst.mcp.McpModels.*;
import com.docst.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * MCP Document Tools.
 * Spring AI 1.1.0+ @Tool annotation 기반 문서 관련 MCP 도구.
 *
 * 제공 도구:
 * - list_documents: 문서 목록 조회
 * - get_document: 문서 내용 조회
 * - list_document_versions: 버전 목록 조회
 * - diff_document: 두 버전 비교
 * - search_documents: 문서 검색 (keyword/semantic/hybrid)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class McpDocumentTools {

    private final DocumentService documentService;
    private final SearchService searchService;
    private final SemanticSearchService semanticSearchService;
    private final HybridSearchService hybridSearchService;
    private final ProjectService projectService;

    /**
     * 문서 목록 조회.
     * repositoryId 또는 projectId 중 하나 필수.
     */
    @Tool(name = "list_documents", description = "List documents in a project or repository. " +
          "Either repositoryId or projectId is required. " +
          "Can filter by path prefix (e.g., 'docs/') and document type (MD, ADOC, OPENAPI, ADR).")
    public ListDocumentsResult listDocuments(
        @ToolParam(description = "Repository ID to list documents from", required = false)
        String repositoryId,
        @ToolParam(description = "Project ID to list documents from", required = false)
        String projectId,
        @ToolParam(description = "Path prefix filter (e.g., 'docs/')", required = false)
        String pathPrefix,
        @ToolParam(description = "Document type filter: MD, ADOC, OPENAPI, ADR", required = false)
        String type
    ) {
        log.info("MCP Tool: listDocuments - repositoryId={}, projectId={}, pathPrefix={}, type={}",
            repositoryId, projectId, pathPrefix, type);

        UUID repoId = repositoryId != null ? UUID.fromString(repositoryId) : null;
        UUID projId = projectId != null ? UUID.fromString(projectId) : resolveDefaultProjectId(null);

        List<Document> documents;
        if (repoId != null) {
            documents = documentService.findByRepositoryId(repoId, pathPrefix, type);
        } else if (projId != null) {
            documents = documentService.findByProjectId(projId);
        } else {
            throw new IllegalArgumentException("Either repositoryId or projectId is required");
        }

        var summaries = documents.stream()
            .map(doc -> new DocumentSummary(
                doc.getId(),
                doc.getRepository().getId(),
                doc.getPath(),
                doc.getTitle(),
                doc.getDocType().name(),
                doc.getLatestCommitSha()
            ))
            .toList();

        return new ListDocumentsResult(summaries);
    }

    /**
     * 문서 내용 조회.
     * 특정 커밋 SHA 지정 가능 (기본: 최신).
     */
    @Tool(name = "get_document", description = "Get document content by ID. " +
          "Returns the full content of the document. " +
          "Optionally specify a commit SHA to get a specific version (defaults to latest).")
    public GetDocumentResult getDocument(
        @ToolParam(description = "Document ID (UUID format)") String documentId,
        @ToolParam(description = "Specific commit SHA to retrieve (optional, defaults to latest)", required = false)
        String commitSha
    ) {
        log.info("MCP Tool: getDocument - documentId={}, commitSha={}", documentId, commitSha);

        UUID docId = UUID.fromString(documentId);
        var doc = documentService.findById(docId)
            .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));

        var version = commitSha != null
            ? documentService.findVersion(docId, commitSha)
            : documentService.findLatestVersion(docId);

        var ver = version.orElseThrow(() ->
            new IllegalArgumentException("Version not found for document: " + documentId));

        return new GetDocumentResult(
            doc.getId(),
            doc.getRepository().getId(),
            doc.getPath(),
            doc.getTitle(),
            doc.getDocType().name(),
            ver.getCommitSha(),
            ver.getContent(),
            ver.getAuthorName(),
            ver.getCommittedAt()
        );
    }

    /**
     * 문서 버전 목록 조회.
     * 최신 버전부터 정렬.
     */
    @Tool(name = "list_document_versions", description = "List all versions (commits) of a document. " +
          "Returns commit history ordered by commit time (newest first).")
    public ListDocumentVersionsResult listDocumentVersions(
        @ToolParam(description = "Document ID (UUID format)") String documentId
    ) {
        log.info("MCP Tool: listDocumentVersions - documentId={}", documentId);

        UUID docId = UUID.fromString(documentId);
        var versions = documentService.findVersions(docId);

        var summaries = versions.stream()
            .map(v -> new VersionSummary(
                v.getCommitSha(),
                v.getAuthorName(),
                v.getAuthorEmail(),
                v.getCommittedAt(),
                v.getMessage()
            ))
            .toList();

        return new ListDocumentVersionsResult(summaries);
    }

    /**
     * 두 버전 비교 (diff).
     */
    @Tool(name = "diff_document", description = "Compare two versions of a document and return the diff. " +
          "Shows line-by-line changes between fromCommitSha and toCommitSha.")
    public DiffDocumentResult diffDocument(
        @ToolParam(description = "Document ID (UUID format)") String documentId,
        @ToolParam(description = "Starting commit SHA for comparison") String fromCommitSha,
        @ToolParam(description = "Ending commit SHA for comparison") String toCommitSha
    ) {
        log.info("MCP Tool: diffDocument - documentId={}, from={}, to={}",
            documentId, fromCommitSha, toCommitSha);

        UUID docId = UUID.fromString(documentId);
        var fromOpt = documentService.findVersion(docId, fromCommitSha);
        var toOpt = documentService.findVersion(docId, toCommitSha);

        if (fromOpt.isEmpty() || toOpt.isEmpty()) {
            throw new IllegalArgumentException("Version not found");
        }

        String diff = buildDiff(
            fromOpt.get().getContent(),
            toOpt.get().getContent(),
            fromCommitSha,
            toCommitSha
        );

        return new DiffDocumentResult(diff);
    }

    /**
     * 문서 검색.
     * keyword, semantic, hybrid 모드 지원.
     */
    @Tool(name = "search_documents", description = "Search documents in a project. " +
          "Supports three search modes: 'keyword' (full-text), 'semantic' (vector similarity), " +
          "and 'hybrid' (combined). Returns matching documents with snippets and relevance scores.")
    public SearchDocumentsResult searchDocuments(
        @ToolParam(description = "Project ID to search within (UUID format)") String projectId,
        @ToolParam(description = "Search query (keywords or natural language question)") String query,
        @ToolParam(description = "Search mode: 'keyword', 'semantic', or 'hybrid' (default: keyword)", required = false)
        String mode,
        @ToolParam(description = "Maximum number of results to return (default: 10)", required = false)
        Integer topK
    ) {
        log.info("MCP Tool: searchDocuments - projectId={}, query={}, mode={}, topK={}",
            projectId, query, mode, topK);

        UUID projId = projectId != null ? UUID.fromString(projectId) : resolveDefaultProjectId(null);
        if (projId == null) {
            throw new IllegalArgumentException("projectId is required. Use list_projects to find available projects.");
        }

        long startTime = System.currentTimeMillis();
        int limit = topK != null && topK > 0 ? topK : 10;
        String searchMode = mode != null ? mode.toLowerCase() : "keyword";

        var results = switch (searchMode) {
            case "semantic" -> semanticSearchService.searchSemantic(projId, query, limit);
            case "hybrid" -> hybridSearchService.hybridSearch(projId, query, limit);
            default -> searchService.searchByKeyword(projId, query, limit);
        };

        var hits = results.stream()
            .map(r -> new SearchHit(
                r.documentId(),
                r.path(),
                null,  // title
                r.headingPath(),
                r.score(),
                r.snippet(),
                null   // content
            ))
            .toList();

        long elapsed = System.currentTimeMillis() - startTime;
        var metadata = new SearchMetadata(searchMode, hits.size(), elapsed + "ms");

        return new SearchDocumentsResult(hits, metadata);
    }

    // ===== Private Helper Methods =====

    /**
     * 기본 프로젝트 ID 해결.
     * API Key의 defaultProjectId 또는 사용자의 첫 번째 프로젝트 사용.
     */
    private UUID resolveDefaultProjectId(UUID inputProjectId) {
        if (inputProjectId != null) {
            return inputProjectId;
        }

        var principal = SecurityUtils.getCurrentUserPrincipal();
        if (principal != null && principal.defaultProjectId() != null) {
            log.debug("Using API Key default project: {}", principal.defaultProjectId());
            return principal.defaultProjectId();
        }

        return null;
    }

    /**
     * 간단한 diff 생성.
     */
    private String buildDiff(String from, String to, String fromSha, String toSha) {
        var fromLines = from == null ? List.<String>of() : List.of(from.split("\\n", -1));
        var toLines = to == null ? List.<String>of() : List.of(to.split("\\n", -1));

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
