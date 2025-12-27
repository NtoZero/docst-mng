package com.docst.domain;

import com.docst.api.ApiModels.SyncMode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * 동기화 작업 엔티티.
 * 레포지토리 동기화 작업의 상태와 결과를 추적한다.
 */
@Entity
@Table(name = "dm_sync_job")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SyncJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** 대상 레포지토리 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false)
    private Repository repository;

    /** 작업 상태 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SyncStatus status;

    /** 대상 브랜치 */
    @Column(name = "target_branch")
    private String targetBranch;

    /** 동기화 모드 */
    @Enumerated(EnumType.STRING)
    @Column(name = "sync_mode", nullable = false)
    private SyncMode syncMode = SyncMode.FULL_SCAN;

    /** 특정 커밋 SHA (SPECIFIC_COMMIT 모드에서 사용) */
    @Column(name = "target_commit_sha")
    private String targetCommitSha;

    /** 마지막 동기화된 커밋 SHA */
    @Column(name = "last_synced_commit")
    private String lastSyncedCommit;

    /** 에러 메시지 (실패 시) */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** 작업 시작 시각 */
    @Column(name = "started_at")
    private Instant startedAt;

    /** 작업 완료 시각 */
    @Column(name = "finished_at")
    private Instant finishedAt;

    /** 레코드 생성 시각 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 진행률 추적용 - 전체 문서 수 */
    @Transient
    private int totalDocuments;

    /** 진행률 추적용 - 처리된 문서 수 */
    @Transient
    private int processedDocuments;

    /**
     * 동기화 작업 생성자.
     *
     * @param repository 대상 레포지토리
     * @param targetBranch 대상 브랜치
     */
    public SyncJob(Repository repository, String targetBranch) {
        this(repository, targetBranch, SyncMode.FULL_SCAN, null);
    }

    /**
     * 동기화 작업 생성자 (모드 지정).
     *
     * @param repository 대상 레포지토리
     * @param targetBranch 대상 브랜치
     * @param syncMode 동기화 모드
     * @param targetCommitSha 특정 커밋 SHA (SPECIFIC_COMMIT 모드에서 사용)
     */
    public SyncJob(Repository repository, String targetBranch, SyncMode syncMode, String targetCommitSha) {
        this.repository = repository;
        this.targetBranch = targetBranch;
        this.syncMode = syncMode != null ? syncMode : SyncMode.FULL_SCAN;
        this.targetCommitSha = targetCommitSha;
        this.status = SyncStatus.PENDING;
        this.createdAt = Instant.now();
    }

    /**
     * 작업을 시작 상태로 전환한다.
     */
    public void start() {
        this.status = SyncStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    /**
     * 작업을 성공 상태로 완료한다.
     *
     * @param lastCommit 마지막 동기화된 커밋 SHA
     */
    public void complete(String lastCommit) {
        this.status = SyncStatus.SUCCEEDED;
        this.lastSyncedCommit = lastCommit;
        this.finishedAt = Instant.now();
    }

    /**
     * 작업을 실패 상태로 종료한다.
     *
     * @param errorMessage 에러 메시지
     */
    public void fail(String errorMessage) {
        this.status = SyncStatus.FAILED;
        this.errorMessage = errorMessage;
        this.finishedAt = Instant.now();
    }

    /**
     * 진행률을 업데이트한다.
     *
     * @param total 전체 문서 수
     * @param processed 처리된 문서 수
     */
    public void updateProgress(int total, int processed) {
        this.totalDocuments = total;
        this.processedDocuments = processed;
    }

    /** 동기화 작업 상태 */
    public enum SyncStatus {
        /** 대기 중 */
        PENDING,
        /** 실행 중 */
        RUNNING,
        /** 성공 */
        SUCCEEDED,
        /** 실패 */
        FAILED
    }
}
