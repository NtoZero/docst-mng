package com.docst.llm.tools;

import com.docst.domain.Document;
import com.docst.domain.DocumentVersion;
import com.docst.service.DocumentService;
import com.docst.service.SearchService;
import com.docst.service.SearchService.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 문서 관련 LLM Tools.
 *
 * Spring AI 1.1.0+ @Tool annotation 기반 선언적 Tool 정의.
 * 문서 검색, 조회, 목록 기능을 LLM에 제공한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentTools {

    private final DocumentService documentService;
    private final SearchService searchService;

    /**
     * 문서 검색 Tool
     *
     * 프로젝트 내 문서를 키워드로 검색.
     */
    @Tool(description = "Search documents in a project using keywords. Returns top matching documents with snippets and relevance scores.")
    public List<DocumentSearchResult> searchDocuments(
        @ToolParam(description = "The search query keywords") String query,
        @ToolParam(description = "The project ID to search within") String projectId,
        @ToolParam(description = "Maximum number of results to return (default: 10)", required = false) Integer topK
    ) {
        log.info("Tool: searchDocuments - query={}, projectId={}, topK={}", query, projectId, topK);

        UUID projId = UUID.fromString(projectId);
        int limit = (topK != null && topK > 0) ? topK : 10;

        List<SearchResult> results = searchService.searchByKeyword(projId, query, limit);

        return results.stream()
            .map(r -> new DocumentSearchResult(
                r.documentId().toString(),
                r.path(),
                r.snippet(),
                r.score()
            ))
            .toList();
    }

    /**
     * 문서 목록 조회 Tool
     *
     * 프로젝트의 모든 문서 목록 반환.
     */
    @Tool(description = "List all documents in a project. Returns document IDs, paths, titles, and types.")
    public List<DocumentInfo> listDocuments(
        @ToolParam(description = "The project ID") String projectId
    ) {
        log.info("Tool: listDocuments - projectId={}", projectId);

        UUID projId = UUID.fromString(projectId);
        List<Document> documents = documentService.findByProjectId(projId);

        return documents.stream()
            .filter(d -> !d.isDeleted())
            .map(d -> new DocumentInfo(
                d.getId().toString(),
                d.getPath(),
                d.getTitle(),
                d.getDocType().name()
            ))
            .toList();
    }

    /**
     * 문서 내용 조회 Tool
     *
     * 문서 ID로 최신 버전의 전체 내용 반환.
     */
    @Tool(description = "Get the full content of a document by its ID. Returns the latest version with commit information.")
    public DocumentContent getDocument(
        @ToolParam(description = "The document ID") String documentId
    ) {
        log.info("Tool: getDocument - documentId={}", documentId);

        UUID docId = UUID.fromString(documentId);
        Document doc = documentService.findById(docId)
            .orElseThrow(() -> new RuntimeException("Document not found: " + documentId));

        DocumentVersion latestVersion = documentService.findLatestVersion(docId)
            .orElseThrow(() -> new RuntimeException("No version found for document: " + documentId));

        return new DocumentContent(
            doc.getId().toString(),
            doc.getPath(),
            doc.getTitle(),
            latestVersion.getContent(),
            latestVersion.getCommitSha()
        );
    }

    // ===== Response Records =====

    /**
     * 문서 검색 결과
     */
    public record DocumentSearchResult(
        String documentId,
        String path,
        String snippet,
        double score
    ) {}

    /**
     * 문서 정보
     */
    public record DocumentInfo(
        String documentId,
        String path,
        String title,
        String docType
    ) {}

    /**
     * 문서 내용
     */
    public record DocumentContent(
        String documentId,
        String path,
        String title,
        String content,
        String commitSha
    ) {}
}
