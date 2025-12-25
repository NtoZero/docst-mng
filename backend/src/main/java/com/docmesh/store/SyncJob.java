package com.docmesh.store;

import java.time.Instant;
import java.util.UUID;

public record SyncJob(
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
