package com.docst.api;

import java.time.Instant;
import java.util.UUID;

/**
 * REST API용 모델 클래스.
 * 클라이언트와 통신에 사용되는 요청/응답 DTO를 정의한다.
 */
public final class ApiModels {
    private ApiModels() {}

    // ===== Auth =====

    /**
     * 인증 토큰 응답.
     *
     * @param accessToken 액세스 토큰
     * @param tokenType 토큰 타입 (Bearer)
     * @param expiresIn 만료 시간 (초)
     */
    public record AuthTokenResponse(String accessToken, String tokenType, long expiresIn) {}

    /**
     * 사용자 정보 응답.
     *
     * @param id 사용자 ID
     * @param provider 인증 제공자
     * @param providerUserId 제공자 사용자 ID
     * @param email 이메일
     * @param displayName 표시 이름
     * @param createdAt 생성 시각
     */
    public record UserResponse(UUID id, String provider, String providerUserId, String email, String displayName, Instant createdAt) {}

    // ===== Project =====

    /**
     * 프로젝트 응답.
     *
     * @param id 프로젝트 ID
     * @param name 프로젝트 이름
     * @param description 프로젝트 설명
     * @param active 활성화 상태
     * @param createdAt 생성 시각
     */
    public record ProjectResponse(UUID id, String name, String description, boolean active, Instant createdAt) {}

    /**
     * 프로젝트 생성 요청.
     *
     * @param name 프로젝트 이름
     * @param description 프로젝트 설명
     */
    public record CreateProjectRequest(String name, String description) {}

    /**
     * 프로젝트 업데이트 요청.
     *
     * @param name 새 이름 (선택)
     * @param description 새 설명 (선택)
     * @param active 활성화 상태 (선택)
     */
    public record UpdateProjectRequest(String name, String description, Boolean active) {}

    // ===== Repository =====

    /**
     * 레포지토리 응답.
     *
     * @param id 레포지토리 ID
     * @param projectId 프로젝트 ID
     * @param provider 레포 제공자
     * @param externalId 외부 ID
     * @param owner 소유자
     * @param name 레포 이름
     * @param cloneUrl clone URL
     * @param defaultBranch 기본 브랜치
     * @param localMirrorPath 로컬 미러 경로
     * @param active 활성화 상태
     * @param createdAt 생성 시각
     */
    public record RepositoryResponse(
            UUID id,
            UUID projectId,
            String provider,
            String externalId,
            String owner,
            String name,
            String cloneUrl,
            String defaultBranch,
            String localMirrorPath,
            boolean active,
            Instant createdAt
    ) {}

    /**
     * 레포지토리 생성 요청.
     *
     * @param provider 레포 제공자 (GITHUB, LOCAL)
     * @param owner 소유자 이름
     * @param name 레포 이름
     * @param defaultBranch 기본 브랜치 (선택)
     * @param localPath 로컬 경로 (LOCAL 제공자일 경우)
     */
    public record CreateRepositoryRequest(String provider, String owner, String name, String defaultBranch, String localPath) {}

    /**
     * 레포지토리 업데이트 요청.
     *
     * @param active 활성화 상태 (선택)
     * @param defaultBranch 기본 브랜치 (선택)
     */
    public record UpdateRepositoryRequest(Boolean active, String defaultBranch) {}

    // ===== Document =====

    /**
     * 문서 요약 응답.
     *
     * @param id 문서 ID
     * @param repositoryId 레포지토리 ID
     * @param path 파일 경로
     * @param title 문서 제목
     * @param docType 문서 타입
     * @param latestCommitSha 최신 커밋 SHA
     * @param createdAt 생성 시각
     */
    public record DocumentResponse(UUID id, UUID repositoryId, String path, String title, String docType, String latestCommitSha, Instant createdAt) {}

    /**
     * 문서 상세 응답 (내용 포함).
     *
     * @param id 문서 ID
     * @param repositoryId 레포지토리 ID
     * @param path 파일 경로
     * @param title 문서 제목
     * @param docType 문서 타입
     * @param latestCommitSha 최신 커밋 SHA
     * @param createdAt 생성 시각
     * @param content 문서 내용
     * @param authorName 작성자 이름
     * @param authorEmail 작성자 이메일
     * @param committedAt 커밋 시각
     */
    public record DocumentDetailResponse(
            UUID id,
            UUID repositoryId,
            String path,
            String title,
            String docType,
            String latestCommitSha,
            Instant createdAt,
            String content,
            String authorName,
            String authorEmail,
            Instant committedAt
    ) {}

    /**
     * 문서 버전 요약 응답.
     *
     * @param id 버전 ID
     * @param documentId 문서 ID
     * @param commitSha 커밋 SHA
     * @param authorName 작성자 이름
     * @param authorEmail 작성자 이메일
     * @param committedAt 커밋 시각
     * @param message 커밋 메시지
     * @param contentHash 내용 해시
     */
    public record DocumentVersionResponse(
            UUID id,
            UUID documentId,
            String commitSha,
            String authorName,
            String authorEmail,
            Instant committedAt,
            String message,
            String contentHash
    ) {}

    /**
     * 문서 버전 상세 응답 (내용 포함).
     *
     * @param id 버전 ID
     * @param documentId 문서 ID
     * @param commitSha 커밋 SHA
     * @param authorName 작성자 이름
     * @param authorEmail 작성자 이메일
     * @param committedAt 커밋 시각
     * @param message 커밋 메시지
     * @param contentHash 내용 해시
     * @param content 문서 내용
     */
    public record DocumentVersionDetailResponse(
            UUID id,
            UUID documentId,
            String commitSha,
            String authorName,
            String authorEmail,
            Instant committedAt,
            String message,
            String contentHash,
            String content
    ) {}

    // ===== Search =====

    /**
     * 검색 결과 응답.
     *
     * @param documentId 문서 ID
     * @param repositoryId 레포지토리 ID
     * @param path 파일 경로
     * @param commitSha 커밋 SHA
     * @param chunkId 청크 ID (Phase 2)
     * @param score 관련도 점수
     * @param snippet 스니펫
     * @param highlightedSnippet 하이라이트된 스니펫
     */
    public record SearchResultResponse(
            UUID documentId,
            UUID repositoryId,
            String path,
            String commitSha,
            UUID chunkId,
            double score,
            String snippet,
            String highlightedSnippet
    ) {}

    // ===== Sync =====

    /**
     * 동기화 작업 응답.
     *
     * @param id 작업 ID
     * @param repositoryId 레포지토리 ID
     * @param status 작업 상태
     * @param targetBranch 대상 브랜치
     * @param lastSyncedCommit 마지막 동기화 커밋
     * @param errorMessage 오류 메시지
     * @param startedAt 시작 시각
     * @param finishedAt 종료 시각
     * @param createdAt 생성 시각
     */
    public record SyncJobResponse(
            UUID id,
            UUID repositoryId,
            String status,
            String targetBranch,
            String lastSyncedCommit,
            String errorMessage,
            Instant startedAt,
            Instant finishedAt,
            Instant createdAt
    ) {}

    /**
     * 동기화 시작 요청.
     *
     * @param branch 대상 브랜치 (선택)
     */
    public record SyncRequest(String branch) {}

    /**
     * 검색 요청.
     *
     * @param q 검색어
     * @param mode 검색 모드 (keyword, semantic)
     * @param topK 결과 개수 제한
     */
    public record SearchRequest(String q, String mode, Integer topK) {}

    // ===== Credential =====

    /**
     * 자격증명 응답.
     *
     * @param id 자격증명 ID
     * @param name 자격증명 이름
     * @param type 자격증명 타입
     * @param username 사용자명
     * @param description 설명
     * @param active 활성화 상태
     * @param createdAt 생성 시각
     * @param updatedAt 수정 시각
     */
    public record CredentialResponse(
            UUID id,
            String name,
            String type,
            String username,
            String description,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {}

    /**
     * 자격증명 생성 요청.
     *
     * @param name 자격증명 이름
     * @param type 자격증명 타입 (GITHUB_PAT, BASIC_AUTH)
     * @param username 사용자명 (선택)
     * @param secret 비밀 (PAT 또는 비밀번호)
     * @param description 설명 (선택)
     */
    public record CreateCredentialRequest(
            String name,
            String type,
            String username,
            String secret,
            String description
    ) {}

    /**
     * 자격증명 수정 요청.
     *
     * @param username 새 사용자명 (선택)
     * @param secret 새 비밀 (선택)
     * @param description 새 설명 (선택)
     * @param active 활성화 상태 (선택)
     */
    public record UpdateCredentialRequest(
            String username,
            String secret,
            String description,
            Boolean active
    ) {}

    /**
     * 레포지토리에 자격증명 연결 요청.
     *
     * @param credentialId 자격증명 ID (null이면 연결 해제)
     */
    public record SetCredentialRequest(UUID credentialId) {}
}
