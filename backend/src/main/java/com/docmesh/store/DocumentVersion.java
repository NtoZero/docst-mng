package com.docmesh.store;

import java.time.Instant;
import java.util.UUID;

public record DocumentVersion(
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
