package com.docst.mcp.tools;

import com.docst.auth.SecurityUtils;
import com.docst.auth.UserPrincipal;
import com.docst.glossary.GlossaryTerm;
import com.docst.glossary.service.GlossaryService;
import com.docst.glossary.service.GlossaryService.GlossaryTermWithScore;
import com.docst.project.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * MCP Glossary Tools.
 * Spring AI 1.1.0+ @Tool annotation 기반 용어 사전 관련 MCP 도구.
 *
 * 제공 도구:
 * - list_glossary_terms: 프로젝트 용어 목록 조회
 * - search_glossary: 키워드/시맨틱 용어 검색
 * - get_glossary_term: 용어 상세 정보 조회
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class McpGlossaryTools {

    private final GlossaryService glossaryService;
    private final ProjectService projectService;

    /**
     * 프로젝트의 용어 목록을 조회한다.
     */
    @Tool(name = "list_glossary_terms", description = "List glossary terms in a project. " +
          "Returns all terms or filters by category. " +
          "Use this to discover available terminology in a project.")
    public ListGlossaryTermsResult listGlossaryTerms(
            @ToolParam(description = "Project ID to list glossary terms from") String projectId,
            @ToolParam(description = "Optional category filter", required = false) String category
    ) {
        log.info("MCP Tool: listGlossaryTerms - projectId={}, category={}", projectId, category);

        UserPrincipal principal = SecurityUtils.getCurrentUserPrincipal();
        if (principal == null) {
            throw new IllegalStateException("Authentication required. Please provide an API key.");
        }

        UUID projId = UUID.fromString(projectId);

        // 프로젝트 접근 권한 확인
        if (projectService.findMember(projId, principal.id()).isEmpty()) {
            throw new IllegalArgumentException("Project not found or access denied: " + projectId);
        }

        List<GlossaryTerm> terms = category != null
            ? glossaryService.findByProjectIdAndCategory(projId, category)
            : glossaryService.findByProjectId(projId);

        List<GlossaryTermSummary> summaries = terms.stream()
            .map(this::toSummary)
            .toList();

        log.info("Found {} glossary terms in project {}", summaries.size(), projectId);
        return new ListGlossaryTermsResult(summaries);
    }

    /**
     * 용어를 검색한다.
     */
    @Tool(name = "search_glossary", description = "Search glossary terms using keyword or semantic search. " +
          "Returns matching terms with relevance scores. " +
          "Use 'keyword' mode for exact matches, 'semantic' for meaning-based search.")
    public SearchGlossaryResult searchGlossary(
            @ToolParam(description = "Project ID to search within") String projectId,
            @ToolParam(description = "Search query (keywords or natural language question)") String query,
            @ToolParam(description = "Search mode: 'keyword' or 'semantic' (default: keyword)", required = false) String mode,
            @ToolParam(description = "Maximum number of results (default: 10)", required = false) Integer topK
    ) {
        log.info("MCP Tool: searchGlossary - projectId={}, query='{}', mode={}, topK={}",
            projectId, query, mode, topK);

        UserPrincipal principal = SecurityUtils.getCurrentUserPrincipal();
        if (principal == null) {
            throw new IllegalStateException("Authentication required. Please provide an API key.");
        }

        UUID projId = UUID.fromString(projectId);

        // 프로젝트 접근 권한 확인
        if (projectService.findMember(projId, principal.id()).isEmpty()) {
            throw new IllegalArgumentException("Project not found or access denied: " + projectId);
        }

        int limit = topK != null && topK > 0 ? topK : 10;
        String searchMode = mode != null ? mode : "keyword";

        List<GlossarySearchHit> results;
        if ("semantic".equals(searchMode)) {
            results = glossaryService.searchSemantic(projId, query, limit).stream()
                .map(r -> new GlossarySearchHit(
                    r.term().getId().toString(),
                    r.term().getName(),
                    r.term().getDefinition(),
                    r.term().getCategory(),
                    r.term().getAbbreviation(),
                    r.score()
                ))
                .toList();
        } else {
            results = glossaryService.searchByKeyword(projId, query).stream()
                .limit(limit)
                .map(term -> new GlossarySearchHit(
                    term.getId().toString(),
                    term.getName(),
                    term.getDefinition(),
                    term.getCategory(),
                    term.getAbbreviation(),
                    1.0
                ))
                .toList();
        }

        log.info("Search returned {} glossary terms", results.size());
        return new SearchGlossaryResult(results, new SearchMetadata(searchMode, results.size()));
    }

    /**
     * 용어 상세 정보를 조회한다.
     */
    @Tool(name = "get_glossary_term", description = "Get detailed information about a specific glossary term. " +
          "Returns the term definition, synonyms, abbreviation, and related terms.")
    public GetGlossaryTermResult getGlossaryTerm(
            @ToolParam(description = "Glossary term ID") String termId
    ) {
        log.info("MCP Tool: getGlossaryTerm - termId={}", termId);

        UserPrincipal principal = SecurityUtils.getCurrentUserPrincipal();
        if (principal == null) {
            throw new IllegalStateException("Authentication required. Please provide an API key.");
        }

        UUID id = UUID.fromString(termId);
        GlossaryTerm term = glossaryService.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Term not found: " + termId));

        UUID projectId = term.getProject().getId();

        // 프로젝트 접근 권한 확인
        if (projectService.findMember(projectId, principal.id()).isEmpty()) {
            throw new IllegalArgumentException("Term not found or access denied: " + termId);
        }

        return new GetGlossaryTermResult(
            term.getId().toString(),
            projectId.toString(),
            term.getName(),
            term.getDefinition(),
            term.getSynonyms(),
            term.getCategory(),
            term.getAbbreviation(),
            term.getRelatedTerms() != null
                ? term.getRelatedTerms().stream().map(UUID::toString).toList()
                : List.of(),
            term.getCreatedAt(),
            term.getUpdatedAt()
        );
    }

    // ===== Result Records =====

    /**
     * list_glossary_terms 결과.
     */
    public record ListGlossaryTermsResult(List<GlossaryTermSummary> terms) {}

    /**
     * 용어 요약 정보.
     */
    public record GlossaryTermSummary(
            String id,
            String name,
            String definition,
            String category,
            String abbreviation
    ) {}

    /**
     * search_glossary 결과.
     */
    public record SearchGlossaryResult(List<GlossarySearchHit> results, SearchMetadata metadata) {}

    /**
     * 검색 결과 항목.
     */
    public record GlossarySearchHit(
            String termId,
            String name,
            String definition,
            String category,
            String abbreviation,
            double score
    ) {}

    /**
     * 검색 메타데이터.
     */
    public record SearchMetadata(String mode, int totalResults) {}

    /**
     * get_glossary_term 결과.
     */
    public record GetGlossaryTermResult(
            String id,
            String projectId,
            String name,
            String definition,
            List<String> synonyms,
            String category,
            String abbreviation,
            List<String> relatedTermIds,
            Instant createdAt,
            Instant updatedAt
    ) {}

    // ===== Mappers =====

    private GlossaryTermSummary toSummary(GlossaryTerm term) {
        return new GlossaryTermSummary(
            term.getId().toString(),
            term.getName(),
            truncate(term.getDefinition(), 200),
            term.getCategory(),
            term.getAbbreviation()
        );
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
