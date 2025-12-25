package com.docst.api;

import java.time.Instant;
import java.util.UUID;

public final class ApiModels {
  private ApiModels() {}

  public record AuthTokenResponse(String accessToken, String tokenType, long expiresIn) {}

  public record UserResponse(UUID id, String provider, String providerUserId, String email, String displayName, Instant createdAt) {}

  public record ProjectResponse(UUID id, String name, String description, boolean active, Instant createdAt) {}

  public record CreateProjectRequest(String name, String description) {}

  public record UpdateProjectRequest(String name, String description, Boolean active) {}

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

  public record CreateRepositoryRequest(String provider, String owner, String name, String defaultBranch, String localPath) {}

  public record UpdateRepositoryRequest(Boolean active, String defaultBranch) {}

  public record DocumentResponse(UUID id, UUID repositoryId, String path, String title, String docType, String latestCommitSha, Instant createdAt) {}

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

  public record SyncRequest(String branch) {}

  public record SearchRequest(String q, String mode, Integer topK) {}
}
