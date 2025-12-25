package com.docst.api;

import com.docst.api.ApiModels.*;
import com.docst.domain.Document;
import com.docst.domain.DocumentVersion;
import com.docst.service.DocumentService;
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
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DocumentsController {

    private final DocumentService documentService;

    /**
     * 레포지토리의 문서 목록을 조회한다.
     *
     * @param repoId 레포지토리 ID
     * @param pathPrefix 경로 접두사 필터 (선택)
     * @param type 문서 타입 필터 (선택)
     * @return 문서 목록
     */
    @GetMapping("/repositories/{repoId}/documents")
    public List<DocumentResponse> listDocuments(
            @PathVariable UUID repoId,
            @RequestParam(required = false) String pathPrefix,
            @RequestParam(required = false) String type
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
    @GetMapping("/documents/{docId}")
    public ResponseEntity<DocumentDetailResponse> getDocument(@PathVariable UUID docId) {
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
     * 문서의 버전 이력을 조회한다.
     *
     * @param docId 문서 ID
     * @return 버전 목록 (최신순)
     */
    @GetMapping("/documents/{docId}/versions")
    public List<DocumentVersionResponse> listVersions(@PathVariable UUID docId) {
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
    @GetMapping("/documents/{docId}/versions/{commitSha}")
    public ResponseEntity<DocumentVersionDetailResponse> getVersion(
            @PathVariable UUID docId,
            @PathVariable String commitSha
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
    @GetMapping(value = "/documents/{docId}/diff", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> diffDocument(
            @PathVariable UUID docId,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false, defaultValue = "unified") String format
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
