package com.docst.mcp;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * MCP (Model Context Protocol) 도구용 모델 클래스.
 * Spring AI 1.1.0+ @Tool annotation 기반 MCP 도구의 결과 DTO를 정의한다.
 *
 * Note: Input record들은 @ToolParam annotation으로 대체되었으므로 제거됨.
 * CreateDocumentInput, UpdateDocumentInput은 DocumentWriteService에서 사용하므로 유지.
 *
 * @see com.docst.mcp.tools.McpDocumentTools
 * @see com.docst.mcp.tools.McpGitTools
 * @see com.docst.mcp.tools.McpProjectTools
 */
public final class McpModels {
    private McpModels() {}

    // ===== list_projects =====

    /**
     * list_projects 도구 결과.
     *
     * @param projects 프로젝트 요약 목록
     */
    public record ListProjectsResult(List<ProjectSummary> projects) {}

    /**
     * 프로젝트 요약 정보.
     *
     * @param id 프로젝트 ID
     * @param name 프로젝트 이름
     * @param description 프로젝트 설명
     * @param role 사용자의 역할 (OWNER, ADMIN, EDITOR, VIEWER)
     */
    public record ProjectSummary(
            UUID id,
            String name,
            String description,
            String role
    ) {}

    // ===== list_documents =====

    /**
     * list_documents 도구 결과.
     *
     * @param documents 문서 요약 목록
     */
    public record ListDocumentsResult(List<DocumentSummary> documents) {}

    /**
     * 문서 요약 정보.
     *
     * @param id 문서 ID
     * @param repositoryId 레포지토리 ID
     * @param path 파일 경로
     * @param title 문서 제목
     * @param docType 문서 타입
     * @param latestCommitSha 최신 커밋 SHA
     */
    public record DocumentSummary(
            UUID id,
            UUID repositoryId,
            String path,
            String title,
            String docType,
            String latestCommitSha
    ) {}

    // ===== get_document =====

    /**
     * get_document 도구 결과.
     *
     * @param id 문서 ID
     * @param repositoryId 레포지토리 ID
     * @param path 파일 경로
     * @param title 문서 제목
     * @param docType 문서 타입
     * @param commitSha 커밋 SHA
     * @param content 문서 내용
     * @param authorName 작성자 이름
     * @param committedAt 커밋 시각
     */
    public record GetDocumentResult(
            UUID id,
            UUID repositoryId,
            String path,
            String title,
            String docType,
            String commitSha,
            String content,
            String authorName,
            Instant committedAt
    ) {}

    // ===== list_document_versions =====

    /**
     * list_document_versions 도구 결과.
     *
     * @param versions 버전 요약 목록
     */
    public record ListDocumentVersionsResult(List<VersionSummary> versions) {}

    /**
     * 버전 요약 정보.
     *
     * @param commitSha 커밋 SHA
     * @param authorName 작성자 이름
     * @param authorEmail 작성자 이메일
     * @param committedAt 커밋 시각
     * @param message 커밋 메시지
     */
    public record VersionSummary(
            String commitSha,
            String authorName,
            String authorEmail,
            Instant committedAt,
            String message
    ) {}

    // ===== diff_document =====

    /**
     * diff_document 도구 결과.
     *
     * @param diff diff 문자열
     */
    public record DiffDocumentResult(String diff) {}

    // ===== search_documents =====

    /**
     * search_documents 도구 결과.
     *
     * @param results 검색 결과 목록
     * @param metadata 검색 메타데이터
     */
    public record SearchDocumentsResult(List<SearchHit> results, SearchMetadata metadata) {}

    /**
     * 검색 결과 항목.
     *
     * @param documentId 문서 ID
     * @param path 파일 경로
     * @param title 문서 제목
     * @param headingPath 헤딩 경로
     * @param score 관련도 점수
     * @param snippet 스니펫
     * @param content 전체 내용 (선택)
     */
    public record SearchHit(
            UUID documentId,
            String path,
            String title,
            String headingPath,
            double score,
            String snippet,
            String content
    ) {}

    /**
     * 검색 메타데이터.
     *
     * @param mode 검색 모드
     * @param totalResults 총 결과 수
     * @param queryTime 검색 소요 시간
     */
    public record SearchMetadata(String mode, int totalResults, String queryTime) {}

    // ===== sync_repository =====

    /**
     * sync_repository 도구 결과.
     *
     * @param jobId 동기화 작업 ID
     * @param status 작업 상태
     */
    public record SyncRepositoryResult(UUID jobId, String status) {}

    // ===== create_document =====

    /**
     * create_document 도구 입력.
     * DocumentWriteService에서 사용하므로 유지.
     *
     * @param repositoryId 레포지토리 ID
     * @param path 파일 경로
     * @param content 문서 내용
     * @param message 커밋 메시지 (선택)
     * @param branch 대상 브랜치 (선택, 기본: main)
     * @param createCommit 즉시 커밋 여부 (true: 즉시 커밋, false: 스테이징만)
     */
    public record CreateDocumentInput(
            UUID repositoryId,
            String path,
            String content,
            String message,
            String branch,
            Boolean createCommit
    ) {}

    /**
     * create_document 도구 결과.
     *
     * @param documentId 생성된 문서 ID
     * @param path 파일 경로
     * @param newCommitSha 커밋된 경우 커밋 SHA
     * @param committed 커밋 여부
     * @param message 결과 메시지
     */
    public record CreateDocumentResult(
            UUID documentId,
            String path,
            String newCommitSha,
            boolean committed,
            String message
    ) {}

    // ===== update_document =====

    /**
     * update_document 도구 입력.
     * DocumentWriteService에서 사용하므로 유지.
     *
     * @param documentId 문서 ID
     * @param content 수정된 내용
     * @param message 커밋 메시지 (선택)
     * @param branch 대상 브랜치 (선택, 기본: main)
     * @param createCommit 즉시 커밋 여부 (true: 즉시 커밋, false: 스테이징만)
     */
    public record UpdateDocumentInput(
            UUID documentId,
            String content,
            String message,
            String branch,
            Boolean createCommit
    ) {}

    /**
     * update_document 도구 결과.
     *
     * @param documentId 문서 ID
     * @param path 파일 경로
     * @param newCommitSha 커밋된 경우 커밋 SHA
     * @param committed 커밋 여부
     * @param message 결과 메시지
     */
    public record UpdateDocumentResult(
            UUID documentId,
            String path,
            String newCommitSha,
            boolean committed,
            String message
    ) {}

    // ===== push_to_remote =====

    /**
     * push_to_remote 도구 결과.
     *
     * @param repositoryId 레포지토리 ID
     * @param branch 푸시한 브랜치
     * @param success 성공 여부
     * @param message 결과 메시지
     */
    public record PushToRemoteResult(
            UUID repositoryId,
            String branch,
            boolean success,
            String message
    ) {}
}
