package com.docst.llm;

import com.docst.domain.Document;
import com.docst.domain.DocumentVersion;
import com.docst.service.DocumentService;
import com.docst.service.SearchService;
import com.docst.service.SearchService.SearchResult;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * LLM Tools Configuration - Spring AI Function Beans
 *
 * @deprecated This class is deprecated as of Phase 6 Week 3-4.
 *             Use {@link com.docst.llm.tools.DocumentTools} instead with @Tool annotation.
 *             This approach is replaced by the more modern @Tool annotation pattern
 *             which is the recommended way in Spring AI 1.1.0+.
 *
 * Spring AI 1.1.0+ 에서 Tool Calling을 위한 Function Bean 정의.
 * 각 Tool은 java.util.function.Function<Request, Response> 형태로 정의되며,
 * @Description 어노테이션으로 LLM에게 Tool의 용도를 설명.
 */
@Deprecated(since = "Phase 6 Week 3-4", forRemoval = true)
@Configuration
@ConditionalOnProperty(prefix = "docst.llm", name = "enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
@Slf4j
public class LlmToolsConfig {

    private final DocumentService documentService;
    private final SearchService searchService;

    /**
     * 문서 검색 Tool
     *
     * 프로젝트 내 문서를 키워드로 검색.
     */
    @Bean
    @Description("Search documents in a project using keywords. Returns top matching documents with snippets.")
    public Function<SearchDocumentsRequest, SearchDocumentsResponse> searchDocuments() {
        return request -> {
            log.info("Tool: searchDocuments - query={}, projectId={}, topK={}",
                request.query, request.projectId, request.topK);

            UUID projectId = UUID.fromString(request.projectId);
            int limit = request.topK != null ? request.topK : 10;

            List<SearchResult> results = searchService.searchByKeyword(projectId, request.query, limit);

            List<SearchDocumentsResponse.DocumentResult> documents = results.stream()
                .map(r -> new SearchDocumentsResponse.DocumentResult(
                    r.documentId().toString(),
                    r.path(),
                    r.snippet(),
                    r.score()
                ))
                .toList();

            return new SearchDocumentsResponse(documents, results.size());
        };
    }

    /**
     * 문서 목록 조회 Tool
     *
     * 프로젝트의 모든 문서 목록 반환.
     */
    @Bean
    @Description("List all documents in a project. Returns document IDs, paths, and titles.")
    public Function<ListDocumentsRequest, ListDocumentsResponse> listDocuments() {
        return request -> {
            log.info("Tool: listDocuments - projectId={}", request.projectId);

            UUID projectId = UUID.fromString(request.projectId);
            List<Document> documents = documentService.findByProjectId(projectId);

            List<ListDocumentsResponse.DocumentInfo> docList = documents.stream()
                .filter(d -> !d.isDeleted())
                .map(d -> new ListDocumentsResponse.DocumentInfo(
                    d.getId().toString(),
                    d.getPath(),
                    d.getTitle(),
                    d.getDocType().name()
                ))
                .toList();

            return new ListDocumentsResponse(docList, docList.size());
        };
    }

    /**
     * 문서 내용 조회 Tool
     *
     * 문서 ID로 최신 버전의 전체 내용 반환.
     */
    @Bean
    @Description("Get the full content of a document by its ID. Returns the latest version content.")
    public Function<GetDocumentRequest, GetDocumentResponse> getDocument() {
        return request -> {
            log.info("Tool: getDocument - documentId={}", request.documentId);

            UUID documentId = UUID.fromString(request.documentId);
            Document doc = documentService.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found: " + documentId));

            DocumentVersion latestVersion = documentService.findLatestVersion(documentId)
                .orElseThrow(() -> new RuntimeException("No version found for document: " + documentId));

            return new GetDocumentResponse(
                doc.getId().toString(),
                doc.getPath(),
                doc.getTitle(),
                latestVersion.getContent(),
                latestVersion.getCommitSha()
            );
        };
    }

    // ===== Request/Response DTOs =====

    @JsonClassDescription("Request to search documents by keywords")
    public record SearchDocumentsRequest(
        @JsonProperty(required = true)
        @JsonPropertyDescription("The search query keywords")
        String query,

        @JsonProperty(required = true)
        @JsonPropertyDescription("The project ID to search within")
        String projectId,

        @JsonProperty(required = false)
        @JsonPropertyDescription("Maximum number of results to return (default: 10)")
        Integer topK
    ) {}

    public record SearchDocumentsResponse(
        List<DocumentResult> documents,
        int totalCount
    ) {
        public record DocumentResult(
            String documentId,
            String path,
            String snippet,
            double score
        ) {}
    }

    @JsonClassDescription("Request to list all documents in a project")
    public record ListDocumentsRequest(
        @JsonProperty(required = true)
        @JsonPropertyDescription("The project ID")
        String projectId
    ) {}

    public record ListDocumentsResponse(
        List<DocumentInfo> documents,
        int totalCount
    ) {
        public record DocumentInfo(
            String documentId,
            String path,
            String title,
            String docType
        ) {}
    }

    @JsonClassDescription("Request to get document content")
    public record GetDocumentRequest(
        @JsonProperty(required = true)
        @JsonPropertyDescription("The document ID")
        String documentId
    ) {}

    public record GetDocumentResponse(
        String documentId,
        String path,
        String title,
        String content,
        String commitSha
    ) {}
}
