package com.docst.mcp;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * MCP (Model Context Protocol) 도구용 모델 클래스.
 * AI 에이전트와 통신에 사용되는 요청/응답 DTO를 정의한다.
 */
public final class McpModels {
    private McpModels() {}

    /**
     * 공통 MCP 응답 래퍼.
     *
     * @param <T> 결과 타입
     * @param result 성공 시 결과
     * @param error 실패 시 오류
     */
    public record McpResponse<T>(T result, McpError error) {
        /**
         * 성공 응답을 생성한다.
         *
         * @param result 결과
         * @return 성공 응답
         */
        public static <T> McpResponse<T> success(T result) {
            return new McpResponse<>(result, null);
        }

        /**
         * 오류 응답을 생성한다.
         *
         * @param message 오류 메시지
         * @return 오류 응답
         */
        public static <T> McpResponse<T> error(String message) {
            return new McpResponse<>(null, new McpError(message));
        }
    }

    /**
     * MCP 오류 정보.
     *
     * @param message 오류 메시지
     */
    public record McpError(String message) {}

    // ===== list_documents =====

    /**
     * list_documents 도구 입력.
     *
     * @param repositoryId 레포지토리 ID (선택)
     * @param projectId 프로젝트 ID (선택)
     * @param pathPrefix 경로 접두사 필터 (선택)
     * @param type 문서 타입 필터 (선택)
     */
    public record ListDocumentsInput(UUID repositoryId, UUID projectId, String pathPrefix, String type) {}

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
     * get_document 도구 입력.
     *
     * @param documentId 문서 ID
     * @param commitSha 특정 커밋 SHA (선택, 없으면 최신)
     */
    public record GetDocumentInput(UUID documentId, String commitSha) {}

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
     * list_document_versions 도구 입력.
     *
     * @param documentId 문서 ID
     */
    public record ListDocumentVersionsInput(UUID documentId) {}

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
     * diff_document 도구 입력.
     *
     * @param documentId 문서 ID
     * @param fromCommitSha 비교 시작 커밋 SHA
     * @param toCommitSha 비교 종료 커밋 SHA
     * @param format diff 형식 (unified 등)
     */
    public record DiffDocumentInput(UUID documentId, String fromCommitSha, String toCommitSha, String format) {}

    /**
     * diff_document 도구 결과.
     *
     * @param diff diff 문자열
     */
    public record DiffDocumentResult(String diff) {}

    // ===== search_documents =====

    /**
     * search_documents 도구 입력.
     *
     * @param projectId 프로젝트 ID
     * @param query 검색어
     * @param mode 검색 모드 (keyword, semantic, hybrid)
     * @param topK 결과 개수 제한
     */
    public record SearchDocumentsInput(UUID projectId, String query, String mode, Integer topK) {}

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
     * @param headingPath 헤딩 경로 (Phase 2)
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
     * sync_repository 도구 입력.
     *
     * @param repositoryId 레포지토리 ID
     * @param branch 대상 브랜치 (선택)
     */
    public record SyncRepositoryInput(UUID repositoryId, String branch) {}

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
     * push_to_remote 도구 입력.
     *
     * @param repositoryId 레포지토리 ID
     * @param branch 푸시할 브랜치 (선택, 기본: main)
     */
    public record PushToRemoteInput(
            UUID repositoryId,
            String branch
    ) {}

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
