package com.docst.api;

import com.docst.api.ApiModels.*;
import com.docst.auth.SecurityUtils;
import com.docst.auth.UserPrincipal;
import com.docst.domain.Document;
import com.docst.domain.DocumentVersion;
import com.docst.mcp.McpModels.UpdateDocumentInput;
import com.docst.mcp.McpModels.UpdateDocumentResult;
import com.docst.service.DocumentService;
import com.docst.service.DocumentWriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 문서 컨트롤러.
 * 문서 조회, 버전 관리, diff 기능을 제공한다.
 */
@Tag(name = "Documents", description = "문서 조회 및 버전 관리 API")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DocumentsController {

    private final DocumentService documentService;
    private final DocumentWriteService documentWriteService;

    /**
     * 레포지토리의 문서 목록을 조회한다.
     *
     * @param repoId 레포지토리 ID
     * @param pathPrefix 경로 접두사 필터 (선택)
     * @param type 문서 타입 필터 (선택)
     * @return 문서 목록
     */
    @Operation(summary = "문서 목록 조회", description = "레포지토리의 문서 목록을 조회합니다. 경로 접두사와 타입으로 필터링할 수 있습니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/repositories/{repoId}/documents")
    public List<DocumentResponse> listDocuments(
            @Parameter(description = "레포지토리 ID") @PathVariable UUID repoId,
            @Parameter(description = "경로 접두사 필터") @RequestParam(required = false) String pathPrefix,
            @Parameter(description = "문서 타입 필터 (MD, ADOC, OPENAPI, ADR, OTHER)") @RequestParam(required = false) String type
    ) {
        return documentService.findByRepositoryId(repoId, pathPrefix, type).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 문서 상세 정보를 조회한다 (최신 버전 내용 포함).
     *
     * @param docId 문서 ID
     * @return 문서 상세 정보 (없으면 404)
     */
    @Operation(summary = "문서 상세 조회", description = "문서 ID로 문서 상세 정보를 조회합니다. 최신 버전의 내용을 포함합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "문서를 찾을 수 없음")
    })
    @GetMapping("/documents/{docId}")
    public ResponseEntity<DocumentDetailResponse> getDocument(
            @Parameter(description = "문서 ID") @PathVariable UUID docId) {
        Optional<Document> docOpt = documentService.findById(docId);
        if (docOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Document doc = docOpt.get();
        Optional<DocumentVersion> versionOpt = documentService.findLatestVersion(docId);

        DocumentVersion version = versionOpt.orElse(null);
        DocumentDetailResponse response = new DocumentDetailResponse(
                doc.getId(),
                doc.getRepository().getId(),
                doc.getPath(),
                doc.getTitle(),
                doc.getDocType().name(),
                doc.getLatestCommitSha(),
                doc.getCreatedAt(),
                version != null ? version.getContent() : null,
                version != null ? version.getAuthorName() : null,
                version != null ? version.getAuthorEmail() : null,
                version != null ? version.getCommittedAt() : null
        );
        return ResponseEntity.ok(response);
    }

    /**
     * 문서 내용을 수정하고 Git 커밋을 생성한다.
     *
     * @param docId 문서 ID
     * @param request 수정 요청 (content, commitMessage, branch)
     * @return 수정 결과 (문서를 찾을 수 없으면 404, 권한이 없으면 403)
     */
    @Operation(summary = "문서 수정", description = "문서 내용을 수정하고 Git 커밋을 생성합니다. EDITOR 이상 권한이 필요합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "404", description = "문서를 찾을 수 없음"),
            @ApiResponse(responseCode = "403", description = "수정 권한 없음")
    })
    @PutMapping("/documents/{docId}")
    public ResponseEntity<UpdateDocumentResponse> updateDocument(
            @Parameter(description = "문서 ID") @PathVariable UUID docId,
            @RequestBody UpdateDocumentRequest request
    ) {
        // 인증된 사용자 확인
        UserPrincipal principal = SecurityUtils.getCurrentUserPrincipal();
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }

        // 문서 존재 확인
        Optional<Document> docOpt = documentService.findById(docId);
        if (docOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        // MCP Input으로 변환 (항상 커밋 생성)
        UpdateDocumentInput input = new UpdateDocumentInput(
                docId,
                request.content(),
                request.commitMessage(),
                request.branch(),
                true  // createCommit = true
        );

        try {
            // DocumentWriteService 호출
            UpdateDocumentResult result = documentWriteService.updateDocument(
                    input,
                    principal.id(),
                    principal.displayName()
            );

            return ResponseEntity.ok(new UpdateDocumentResponse(
                    result.documentId(),
                    result.path(),
                    result.newCommitSha(),
                    result.message()
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 문서의 버전 이력을 조회한다.
     *
     * @param docId 문서 ID
     * @return 버전 목록 (최신순)
     */
    @Operation(summary = "문서 버전 목록 조회", description = "문서의 모든 버전 이력을 최신순으로 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/documents/{docId}/versions")
    public List<DocumentVersionResponse> listVersions(
            @Parameter(description = "문서 ID") @PathVariable UUID docId) {
        return documentService.findVersions(docId).stream()
                .map(this::toVersionResponse)
                .toList();
    }

    /**
     * 문서의 특정 버전을 조회한다.
     *
     * @param docId 문서 ID
     * @param commitSha 커밋 SHA
     * @return 버전 상세 정보 (없으면 404)
     */
    @Operation(summary = "문서 버전 상세 조회", description = "문서의 특정 버전을 조회합니다. 버전의 전체 내용을 포함합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "버전을 찾을 수 없음")
    })
    @GetMapping("/documents/{docId}/versions/{commitSha}")
    public ResponseEntity<DocumentVersionDetailResponse> getVersion(
            @Parameter(description = "문서 ID") @PathVariable UUID docId,
            @Parameter(description = "커밋 SHA") @PathVariable String commitSha
    ) {
        return documentService.findVersion(docId, commitSha)
                .map(this::toVersionDetailResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 두 버전 간의 문서 차이를 비교한다.
     *
     * @param docId 문서 ID
     * @param from 시작 커밋 SHA
     * @param to 종료 커밋 SHA
     * @param format diff 형식 (기본값: unified)
     * @return diff 문자열 (버전이 없으면 404)
     */
    @Operation(summary = "문서 버전 비교", description = "두 버전 간의 문서 차이를 unified diff 형식으로 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "버전을 찾을 수 없음")
    })
    @GetMapping(value = "/documents/{docId}/diff", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> diffDocument(
            @Parameter(description = "문서 ID") @PathVariable UUID docId,
            @Parameter(description = "시작 커밋 SHA") @RequestParam String from,
            @Parameter(description = "종료 커밋 SHA") @RequestParam String to,
            @Parameter(description = "diff 형식 (기본값: unified)") @RequestParam(required = false, defaultValue = "unified") String format
    ) {
        Optional<DocumentVersion> fromVersion = documentService.findVersion(docId, from);
        Optional<DocumentVersion> toVersion = documentService.findVersion(docId, to);

        if (fromVersion.isEmpty() || toVersion.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String diff = buildUnifiedDiff(
                fromVersion.get().getContent(),
                toVersion.get().getContent(),
                from,
                to
        );
        return ResponseEntity.ok(diff);
    }

    /**
     * 두 버전의 내용을 비교하여 unified diff를 생성한다.
     */
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

    /**
     * Document 엔티티를 요약 응답 DTO로 변환한다.
     */
    private DocumentResponse toResponse(Document doc) {
        return new DocumentResponse(
                doc.getId(),
                doc.getRepository().getId(),
                doc.getPath(),
                doc.getTitle(),
                doc.getDocType().name(),
                doc.getLatestCommitSha(),
                doc.getCreatedAt()
        );
    }

    /**
     * DocumentVersion 엔티티를 요약 응답 DTO로 변환한다.
     */
    private DocumentVersionResponse toVersionResponse(DocumentVersion version) {
        return new DocumentVersionResponse(
                version.getId(),
                version.getDocument().getId(),
                version.getCommitSha(),
                version.getAuthorName(),
                version.getAuthorEmail(),
                version.getCommittedAt(),
                version.getMessage(),
                version.getContentHash()
        );
    }

    /**
     * DocumentVersion 엔티티를 상세 응답 DTO로 변환한다.
     */
    private DocumentVersionDetailResponse toVersionDetailResponse(DocumentVersion version) {
        return new DocumentVersionDetailResponse(
                version.getId(),
                version.getDocument().getId(),
                version.getCommitSha(),
                version.getAuthorName(),
                version.getAuthorEmail(),
                version.getCommittedAt(),
                version.getMessage(),
                version.getContentHash(),
                version.getContent()
        );
    }
}
