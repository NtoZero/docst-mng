package com.docst.store;

import java.time.Instant;
import java.util.UUID;

public record Repository(
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
