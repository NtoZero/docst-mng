package com.docst.store;

import java.time.Instant;
import java.util.UUID;

public record Document(
    UUID id,
    UUID repositoryId,
    String path,
    String title,
    String docType,
    String latestCommitSha,
    Instant createdAt
) {}
