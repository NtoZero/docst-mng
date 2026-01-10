package com.docst.api;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
     * @param credentialId 연결된 자격증명 ID (nullable)
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
            Instant createdAt,
            UUID credentialId
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

    /**
     * 레포지토리 이관 요청.
     *
     * @param targetProjectId 이관 대상 프로젝트 ID
     */
    public record MoveRepositoryRequest(UUID targetProjectId) {}

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

    /**
     * 문서 수정 요청.
     *
     * @param content 새 문서 내용
     * @param commitMessage Git 커밋 메시지
     * @param branch 대상 브랜치 (선택, null이면 기본 브랜치)
     */
    public record UpdateDocumentRequest(
            String content,
            String commitMessage,
            String branch
    ) {}

    /**
     * 문서 수정 응답.
     *
     * @param documentId 문서 ID
     * @param path 문서 경로
     * @param commitSha 새 커밋 SHA
     * @param message 상태 메시지
     */
    public record UpdateDocumentResponse(
            UUID documentId,
            String path,
            String commitSha,
            String message
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
     * @param headingPath 헤딩 경로 (Phase 2-C: "# Title > ## Section")
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
            String headingPath,
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
     * 동기화 요청.
     *
     * @param branch 대상 브랜치 (null이면 기본 브랜치)
     * @param mode 동기화 모드 (FULL_SCAN, INCREMENTAL, SPECIFIC_COMMIT)
     * @param targetCommitSha 특정 커밋 SHA (SPECIFIC_COMMIT 모드에서 사용)
     * @param enableEmbedding 임베딩 생성 여부 (기본값: true)
     */
    public record SyncRequest(String branch, SyncMode mode, String targetCommitSha, Boolean enableEmbedding) {}

    /**
     * 동기화 모드.
     */
    public enum SyncMode {
        /** 전체 파일 트리 스캔 */
        FULL_SCAN,
        /** 마지막 동기화 이후 변경분만 처리 */
        INCREMENTAL,
        /** 특정 커밋 기준 동기화 */
        SPECIFIC_COMMIT
    }

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

    // ===== Commit =====

    /**
     * 커밋 응답.
     *
     * @param sha 커밋 SHA (전체)
     * @param shortSha 커밋 SHA (7자리)
     * @param message 커밋 메시지 (첫 줄)
     * @param fullMessage 커밋 메시지 (전체)
     * @param authorName 작성자 이름
     * @param authorEmail 작성자 이메일
     * @param committedAt 커밋 시각
     * @param changedFilesCount 변경된 파일 수
     */
    public record CommitResponse(
            String sha,
            String shortSha,
            String message,
            String fullMessage,
            String authorName,
            String authorEmail,
            Instant committedAt,
            int changedFilesCount
    ) {}

    /**
     * 커밋 상세 응답 (변경된 파일 목록 포함).
     *
     * @param sha 커밋 SHA (전체)
     * @param shortSha 커밋 SHA (7자리)
     * @param message 커밋 메시지 (첫 줄)
     * @param fullMessage 커밋 메시지 (전체)
     * @param authorName 작성자 이름
     * @param authorEmail 작성자 이메일
     * @param committedAt 커밋 시각
     * @param changedFiles 변경된 파일 목록
     */
    public record CommitDetailResponse(
            String sha,
            String shortSha,
            String message,
            String fullMessage,
            String authorName,
            String authorEmail,
            Instant committedAt,
            List<ChangedFileResponse> changedFiles
    ) {}

    /**
     * 변경된 파일 응답.
     *
     * @param path 파일 경로
     * @param changeType 변경 타입 (ADDED, MODIFIED, DELETED, RENAMED)
     * @param oldPath 이전 경로 (RENAMED인 경우)
     * @param isDocument 문서 패턴에 매칭되는지
     */
    public record ChangedFileResponse(
            String path,
            String changeType,
            String oldPath,
            boolean isDocument
    ) {}

    // ===== Stats =====

    /**
     * 대시보드 통계 응답.
     *
     * @param totalProjects 전체 프로젝트 수
     * @param totalRepositories 전체 레포지토리 수
     * @param totalDocuments 전체 문서 수 (삭제되지 않은 것만)
     */
    public record StatsResponse(
            long totalProjects,
            long totalRepositories,
            long totalDocuments
    ) {}

    // ===== RAG Config (Phase 4-D) =====

    /**
     * 프로젝트 RAG 설정 응답.
     *
     * @param projectId 프로젝트 ID
     * @param embedding 임베딩 설정
     * @param pgvector PgVector 설정
     * @param neo4j Neo4j 설정
     * @param hybrid Hybrid 설정
     * @param updatedAt 마지막 수정 시각
     */
    public record ProjectRagConfigResponse(
            UUID projectId,
            EmbeddingConfigResponse embedding,
            PgVectorConfigResponse pgvector,
            Neo4jConfigResponse neo4j,
            HybridConfigResponse hybrid,
            Instant updatedAt
    ) {}

    /**
     * 임베딩 설정 응답.
     */
    public record EmbeddingConfigResponse(
            String provider,
            String model,
            Integer dimensions
    ) {}

    /**
     * PgVector 설정 응답.
     */
    public record PgVectorConfigResponse(
            Boolean enabled,
            Double similarityThreshold
    ) {}

    /**
     * Neo4j 설정 응답.
     */
    public record Neo4jConfigResponse(
            Boolean enabled,
            Integer maxHop,
            String entityExtractionModel
    ) {}

    /**
     * Hybrid 설정 응답.
     */
    public record HybridConfigResponse(
            String fusionStrategy,
            Integer rrfK,
            Double vectorWeight,
            Double graphWeight
    ) {}

    /**
     * RAG 설정 업데이트 요청.
     *
     * @param embedding 임베딩 설정 (선택)
     * @param pgvector PgVector 설정 (선택)
     * @param neo4j Neo4j 설정 (선택)
     * @param hybrid Hybrid 설정 (선택)
     */
    public record UpdateProjectRagConfigRequest(
            EmbeddingConfigRequest embedding,
            PgVectorConfigRequest pgvector,
            Neo4jConfigRequest neo4j,
            HybridConfigRequest hybrid
    ) {}

    /**
     * 임베딩 설정 요청.
     */
    public record EmbeddingConfigRequest(
            String provider,
            String model,
            Integer dimensions
    ) {}

    /**
     * PgVector 설정 요청.
     */
    public record PgVectorConfigRequest(
            Boolean enabled,
            Double similarityThreshold
    ) {}

    /**
     * Neo4j 설정 요청.
     */
    public record Neo4jConfigRequest(
            Boolean enabled,
            Integer maxHop,
            String entityExtractionModel
    ) {}

    /**
     * Hybrid 설정 요청.
     */
    public record HybridConfigRequest(
            String fusionStrategy,
            Integer rrfK,
            Double vectorWeight,
            Double graphWeight
    ) {}

    /**
     * RAG 설정 검증 응답.
     *
     * @param valid 유효 여부
     * @param errors 오류 목록
     * @param warnings 경고 목록
     */
    public record RagConfigValidationResponse(
            boolean valid,
            List<String> errors,
            List<String> warnings
    ) {}

    /**
     * RAG 설정 기본값 응답.
     *
     * @param embedding 임베딩 기본 설정
     * @param pgvector PgVector 기본 설정
     * @param neo4j Neo4j 기본 설정
     * @param hybrid Hybrid 기본 설정
     */
    public record RagConfigDefaultsResponse(
            EmbeddingConfigResponse embedding,
            PgVectorConfigResponse pgvector,
            Neo4jConfigResponse neo4j,
            HybridConfigResponse hybrid
    ) {}

    /**
     * 재임베딩 트리거 응답.
     *
     * @param projectId 프로젝트 ID
     * @param message 상태 메시지
     * @param inProgress 진행 중 여부
     */
    public record ReEmbeddingTriggerResponse(
            UUID projectId,
            String message,
            boolean inProgress
    ) {}

    /**
     * 재임베딩 상태 응답.
     *
     * @param projectId 프로젝트 ID
     * @param inProgress 진행 중 여부
     * @param totalVersions 총 문서 버전 수
     * @param processedVersions 처리된 문서 버전 수
     * @param progress 진행률 (0-100)
     * @param deletedEmbeddings 삭제된 임베딩 수
     * @param embeddedCount 생성된 임베딩 수
     * @param failedCount 실패한 문서 수
     * @param errorMessage 오류 메시지 (실패 시)
     */
    public record ReEmbeddingStatusResponse(
            UUID projectId,
            boolean inProgress,
            int totalVersions,
            int processedVersions,
            double progress,
            int deletedEmbeddings,
            int embeddedCount,
            int failedCount,
            String errorMessage
    ) {}

    // ===== System Config (Phase 4-E, ADMIN only) =====

    /**
     * 시스템 설정 응답.
     *
     * @param id 설정 ID
     * @param configKey 설정 키
     * @param configValue 설정 값
     * @param configType 설정 타입 (STRING, JSON, ENCRYPTED)
     * @param description 설명
     * @param createdAt 생성 시각
     * @param updatedAt 수정 시각
     */
    public record SystemConfigResponse(
            UUID id,
            String configKey,
            String configValue,
            String configType,
            String description,
            Instant createdAt,
            Instant updatedAt
    ) {}

    /**
     * 시스템 설정 업데이트 요청.
     *
     * @param configValue 새 설정 값
     * @param configType 설정 타입 (선택)
     * @param description 설명 (선택)
     */
    public record UpdateSystemConfigRequest(
            String configValue,
            String configType,
            String description
    ) {}

    // ===== System & Project Credentials (Phase 4-E) =====

    /**
     * 시스템 크리덴셜 응답.
     *
     * @param id 크리덴셜 ID
     * @param name 크리덴셜 이름
     * @param type 크리덴셜 타입 (OPENAI_API_KEY, NEO4J_AUTH, etc.)
     * @param scope 스코프 (SYSTEM)
     * @param description 설명
     * @param active 활성화 상태
     * @param createdAt 생성 시각
     * @param updatedAt 수정 시각
     */
    public record SystemCredentialResponse(
            UUID id,
            String name,
            String type,
            String scope,
            String description,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {}

    /**
     * 시스템 크리덴셜 생성 요청.
     *
     * @param name 크리덴셜 이름
     * @param type 크리덴셜 타입 (OPENAI_API_KEY, NEO4J_AUTH, ANTHROPIC_API_KEY, CUSTOM_API_KEY)
     * @param secret 비밀 (평문, 서버에서 암호화)
     * @param description 설명 (선택)
     */
    public record CreateSystemCredentialRequest(
            String name,
            String type,
            String secret,
            String description
    ) {}

    /**
     * 시스템 크리덴셜 업데이트 요청.
     *
     * @param secret 새 비밀 (선택, 평문)
     * @param description 새 설명 (선택)
     */
    public record UpdateSystemCredentialRequest(
            String secret,
            String description
    ) {}

    /**
     * 프로젝트 크리덴셜 응답.
     *
     * @param id 크리덴셜 ID
     * @param projectId 프로젝트 ID
     * @param name 크리덴셜 이름
     * @param type 크리덴셜 타입 (OPENAI_API_KEY, etc.)
     * @param scope 스코프 (PROJECT)
     * @param description 설명
     * @param active 활성화 상태
     * @param createdAt 생성 시각
     * @param updatedAt 수정 시각
     */
    public record ProjectCredentialResponse(
            UUID id,
            UUID projectId,
            String name,
            String type,
            String scope,
            String description,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {}

    /**
     * 프로젝트 크리덴셜 생성 요청.
     *
     * @param name 크리덴셜 이름
     * @param type 크리덴셜 타입 (OPENAI_API_KEY, etc.)
     * @param secret 비밀 (평문, 서버에서 암호화)
     * @param description 설명 (선택)
     */
    public record CreateProjectCredentialRequest(
            String name,
            String type,
            String secret,
            String description
    ) {}

    /**
     * 프로젝트 크리덴셜 업데이트 요청.
     *
     * @param secret 새 비밀 (선택, 평문)
     * @param description 새 설명 (선택)
     */
    public record UpdateProjectCredentialRequest(
            String secret,
            String description
    ) {}

    // ===== Health Check =====

    /**
     * 시스템 헬스 체크 응답.
     *
     * @param status 전체 상태 (UP, DEGRADED, DOWN)
     * @param timestamp 체크 시각
     * @param services 서비스별 헬스 상태
     */
    public record HealthCheckResponse(
            String status,
            LocalDateTime timestamp,
            Map<String, ServiceHealth> services
    ) {}

    /**
     * 개별 서비스 헬스 상태.
     *
     * @param status 상태 (UP, DOWN, UNKNOWN)
     * @param message 상태 메시지
     * @param details 상세 정보
     */
    public record ServiceHealth(
            String status,
            String message,
            Map<String, Object> details
    ) {}
}
