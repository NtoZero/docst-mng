package com.docst.llm.tools;

import com.docst.domain.Document;
import com.docst.domain.DocumentVersion;
import com.docst.rag.config.RagConfigService;
import com.docst.rag.config.ResolvedRagConfig;
import com.docst.service.DocumentService;
import com.docst.service.SearchService;
import com.docst.service.SemanticSearchService;
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
    private final SemanticSearchService semanticSearchService;
    private final RagConfigService ragConfigService;

    /**
     * 문서 검색 Tool
     *
     * 프로젝트 내 문서를 의미 검색(벡터 검색)으로 검색.
     * 키워드 매칭이 아닌 의미적 유사도로 검색하여 더 정확한 결과 제공.
     */
    @Tool(description = "Search documents in a project using semantic search (vector similarity). " +
          "Returns top matching documents with snippets and relevance scores based on meaning, not just keywords.")
    public List<DocumentSearchResult> searchDocuments(
        @ToolParam(description = "The search query (natural language question or keywords)") String query,
        @ToolParam(description = "The project ID to search within") String projectId,
        @ToolParam(description = "Maximum number of results to return (default: 10)", required = false) Integer topK
    ) {
        log.info("Tool: searchDocuments (semantic) - query={}, projectId={}, topK={}", query, projectId, topK);

        UUID projId = UUID.fromString(projectId);
        int limit = (topK != null && topK > 0) ? topK : 10;

        // RAG 설정에서 similarity threshold 가져오기
        ResolvedRagConfig config = ragConfigService.resolve(projId, null);
        double threshold = config.getSimilarityThreshold();

        // 의미 검색 사용 (벡터 유사도)
        List<SearchResult> results = semanticSearchService.searchSemantic(projId, query, limit, threshold);

        // 결과가 없으면 키워드 검색으로 폴백
        if (results.isEmpty()) {
            log.info("No semantic search results, falling back to keyword search");
            results = searchService.searchByKeyword(projId, query, limit);
        }

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

    /**
     * 문서 내용 업데이트 Tool
     *
     * 기존 문서의 내용을 새 내용으로 업데이트.
     */
    @Tool(description = "Update the content of an existing document. Creates a new version with the updated content.")
    public DocumentUpdateResult updateDocument(
        @ToolParam(description = "The document ID to update") String documentId,
        @ToolParam(description = "The new content for the document") String content
    ) {
        log.info("Tool: updateDocument - documentId={}, contentLength={}", documentId, content.length());

        UUID docId = UUID.fromString(documentId);
        Document doc = documentService.findById(docId)
            .orElseThrow(() -> new RuntimeException("Document not found: " + documentId));

        // LLM이 생성한 버전임을 명시
        String commitSha = "llm-" + UUID.randomUUID().toString().substring(0, 8);

        DocumentVersion newVersion = documentService.upsertDocument(
            doc.getRepository().getId(),
            doc.getPath(),
            commitSha,
            content,
            "LLM Assistant",
            "llm@docst.ai",
            java.time.Instant.now(),
            "Updated by LLM"
        );

        if (newVersion != null) {
            return new DocumentUpdateResult(
                doc.getId().toString(),
                doc.getPath(),
                newVersion.getCommitSha(),
                "Document updated successfully"
            );
        } else {
            return new DocumentUpdateResult(
                doc.getId().toString(),
                doc.getPath(),
                commitSha,
                "No changes detected (content identical)"
            );
        }
    }

    /**
     * 새 문서 생성 Tool
     *
     * 레포지토리에 새 문서를 생성.
     */
    @Tool(description = "Create a new document in a repository. Provide the repository ID, file path, and initial content.")
    public DocumentCreateResult createDocument(
        @ToolParam(description = "The repository ID where the document will be created") String repositoryId,
        @ToolParam(description = "The file path for the new document (e.g., 'docs/new-guide.md')") String path,
        @ToolParam(description = "The initial content of the document") String content
    ) {
        log.info("Tool: createDocument - repositoryId={}, path={}, contentLength={}",
            repositoryId, path, content.length());

        UUID repoId = UUID.fromString(repositoryId);

        // LLM이 생성한 버전임을 명시
        String commitSha = "llm-" + UUID.randomUUID().toString().substring(0, 8);

        DocumentVersion newVersion = documentService.upsertDocument(
            repoId,
            path,
            commitSha,
            content,
            "LLM Assistant",
            "llm@docst.ai",
            java.time.Instant.now(),
            "Created by LLM"
        );

        // upsertDocument는 항상 Document를 생성하므로, 다시 조회
        Document doc = documentService.findById(newVersion.getDocument().getId())
            .orElseThrow(() -> new RuntimeException("Failed to retrieve created document"));

        return new DocumentCreateResult(
            doc.getId().toString(),
            doc.getPath(),
            doc.getTitle(),
            newVersion.getCommitSha(),
            "Document created successfully"
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

    /**
     * 문서 업데이트 결과
     */
    public record DocumentUpdateResult(
        String documentId,
        String path,
        String commitSha,
        String message
    ) {}

    /**
     * 문서 생성 결과
     */
    public record DocumentCreateResult(
        String documentId,
        String path,
        String title,
        String commitSha,
        String message
    ) {}
}
