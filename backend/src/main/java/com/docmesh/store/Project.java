package com.docmesh.store;

import java.time.Instant;
import java.util.UUID;

public record Project(
    UUID id,
    String name,
    String description,
    boolean active,
    Instant createdAt
) {}
