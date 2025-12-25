package com.docst.mcp;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class McpModels {
    private McpModels() {}

    // Common response wrapper
    public record McpResponse<T>(T result, McpError error) {
        public static <T> McpResponse<T> success(T result) {
            return new McpResponse<>(result, null);
        }
        public static <T> McpResponse<T> error(String message) {
            return new McpResponse<>(null, new McpError(message));
        }
    }

    public record McpError(String message) {}

    // list_documents
    public record ListDocumentsInput(UUID repositoryId, UUID projectId, String pathPrefix, String type) {}
    public record ListDocumentsResult(List<DocumentSummary> documents) {}
    public record DocumentSummary(
            UUID id,
            UUID repositoryId,
            String path,
            String title,
            String docType,
            String latestCommitSha
    ) {}

    // get_document
    public record GetDocumentInput(UUID documentId, String commitSha) {}
    public record GetDocumentResult(
            UUID id,
            UUID repositoryId,
            String path,
            String title,
            String docType,
            String commitSha,
            String content,
            String authorName,
            Instant committedAt
    ) {}

    // list_document_versions
    public record ListDocumentVersionsInput(UUID documentId) {}
    public record ListDocumentVersionsResult(List<VersionSummary> versions) {}
    public record VersionSummary(
            String commitSha,
            String authorName,
            String authorEmail,
            Instant committedAt,
            String message
    ) {}

    // diff_document
    public record DiffDocumentInput(UUID documentId, String fromCommitSha, String toCommitSha, String format) {}
    public record DiffDocumentResult(String diff) {}

    // search_documents
    public record SearchDocumentsInput(UUID projectId, String query, String mode, Integer topK) {}
    public record SearchDocumentsResult(List<SearchHit> results, SearchMetadata metadata) {}
    public record SearchHit(
            UUID documentId,
            String path,
            String title,
            String headingPath,
            double score,
            String snippet,
            String content
    ) {}
    public record SearchMetadata(String mode, int totalResults, String queryTime) {}

    // sync_repository
    public record SyncRepositoryInput(UUID repositoryId, String branch) {}
    public record SyncRepositoryResult(UUID jobId, String status) {}
}
