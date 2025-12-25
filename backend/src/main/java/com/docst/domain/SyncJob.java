package com.docst.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dm_sync_job")
public class SyncJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false)
    private Repository repository;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SyncStatus status;

    @Column(name = "target_branch")
    private String targetBranch;

    @Column(name = "last_synced_commit")
    private String lastSyncedCommit;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Sync progress tracking
    @Transient
    private int totalDocuments;
    @Transient
    private int processedDocuments;

    protected SyncJob() {}

    public SyncJob(Repository repository, String targetBranch) {
        this.repository = repository;
        this.targetBranch = targetBranch;
        this.status = SyncStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Repository getRepository() { return repository; }
    public SyncStatus getStatus() { return status; }
    public String getTargetBranch() { return targetBranch; }
    public String getLastSyncedCommit() { return lastSyncedCommit; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public int getTotalDocuments() { return totalDocuments; }
    public int getProcessedDocuments() { return processedDocuments; }

    public void start() {
        this.status = SyncStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    public void complete(String lastCommit) {
        this.status = SyncStatus.SUCCEEDED;
        this.lastSyncedCommit = lastCommit;
        this.finishedAt = Instant.now();
    }

    public void fail(String errorMessage) {
        this.status = SyncStatus.FAILED;
        this.errorMessage = errorMessage;
        this.finishedAt = Instant.now();
    }

    public void updateProgress(int total, int processed) {
        this.totalDocuments = total;
        this.processedDocuments = processed;
    }

    public enum SyncStatus {
        PENDING, RUNNING, SUCCEEDED, FAILED
    }
}
