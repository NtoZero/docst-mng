package com.docst.mcp;

import com.docst.domain.Document;
import com.docst.domain.DocumentVersion;
import com.docst.domain.SyncJob;
import com.docst.mcp.McpModels.*;
import com.docst.service.DocumentService;
import com.docst.service.SearchService;
import com.docst.service.SemanticSearchService;
import com.docst.service.HybridSearchService;
import com.docst.service.SyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * MCP (Model Context Protocol) 도구 컨트롤러.
 * AI 에이전트가 문서 관리 기능에 접근하기 위한 API 엔드포인트를 제공한다.
 */
@RestController
@RequestMapping("/mcp/tools")
@RequiredArgsConstructor
public class McpController {

    private final DocumentService documentService;
    private final SearchService searchService;
    private final SemanticSearchService semanticSearchService;
    private final HybridSearchService hybridSearchService;
    private final SyncService syncService;
    private final McpToolDispatcher dispatcher;

    /**
     * 문서 목록을 조회한다.
     * repositoryId 또는 projectId 중 하나는 필수이다.
     *
     * @param input 조회 조건
     * @return 문서 요약 목록
     */
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

    /**
     * 문서 상세 정보와 내용을 조회한다.
     *
     * @param input 문서 ID와 선택적 커밋 SHA
     * @return 문서 상세 정보
     */
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

    /**
     * 문서의 버전 이력을 조회한다.
     *
     * @param input 문서 ID
     * @return 버전 요약 목록
     */
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

    /**
     * 두 버전 간의 문서 차이를 비교한다.
     *
     * @param input 문서 ID와 비교할 커밋 SHA 쌍
     * @return diff 문자열
     */
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

    /**
     * 프로젝트 내 문서를 검색한다.
     * Phase 2-C: 키워드, 의미, 하이브리드 검색 지원
     *
     * @param input 검색 조건
     * @return 검색 결과 및 메타데이터
     */
    @PostMapping("/search_documents")
    public ResponseEntity<McpResponse<SearchDocumentsResult>> searchDocuments(
            @RequestBody SearchDocumentsInput input
    ) {
        try {
            long startTime = System.currentTimeMillis();
            int topK = input.topK() != null ? input.topK() : 10;
            String mode = input.mode() != null ? input.mode() : "keyword";

            // 검색 모드에 따라 다른 서비스 호출
            List<SearchService.SearchResult> results = switch (mode.toLowerCase()) {
                case "semantic" -> semanticSearchService.searchSemantic(input.projectId(), input.query(), topK);
                case "hybrid" -> hybridSearchService.hybridSearch(input.projectId(), input.query(), topK);
                default -> searchService.searchByKeyword(input.projectId(), input.query(), topK);
            };

            List<SearchHit> hits = results.stream()
                    .map(r -> new SearchHit(
                            r.documentId(),
                            r.path(),
                            null, // title - could be fetched
                            r.headingPath(),
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

    /**
     * 레포지토리 동기화를 시작한다.
     *
     * @param input 레포지토리 ID와 선택적 브랜치
     * @return 동기화 작업 정보
     */
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

    /**
     * 새 문서를 생성하고 선택적으로 커밋한다.
     * Phase 5: WRITE 도구
     *
     * @param input 생성 요청
     * @return 생성 결과
     */
    @PostMapping("/create_document")
    public ResponseEntity<McpResponse<CreateDocumentResult>> createDocument(
            @RequestBody CreateDocumentInput input
    ) {
        return ResponseEntity.ok(dispatcher.dispatch("create_document", input));
    }

    /**
     * 기존 문서를 수정하고 선택적으로 커밋한다.
     * Phase 5: WRITE 도구
     *
     * @param input 수정 요청
     * @return 수정 결과
     */
    @PostMapping("/update_document")
    public ResponseEntity<McpResponse<UpdateDocumentResult>> updateDocument(
            @RequestBody UpdateDocumentInput input
    ) {
        return ResponseEntity.ok(dispatcher.dispatch("update_document", input));
    }

    /**
     * 로컬 커밋을 원격 레포지토리로 푸시한다.
     * Phase 5: WRITE 도구
     *
     * @param input 푸시 요청
     * @return 푸시 결과
     */
    @PostMapping("/push_to_remote")
    public ResponseEntity<McpResponse<PushToRemoteResult>> pushToRemote(
            @RequestBody PushToRemoteInput input
    ) {
        return ResponseEntity.ok(dispatcher.dispatch("push_to_remote", input));
    }

    /**
     * 두 버전의 내용을 비교하여 간단한 diff를 생성한다.
     */
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
