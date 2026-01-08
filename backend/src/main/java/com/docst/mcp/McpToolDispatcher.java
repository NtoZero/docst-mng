package com.docst.mcp;

import com.docst.auth.UserPrincipal;
import com.docst.domain.ProjectMember;
import com.docst.mcp.McpModels.*;
import com.docst.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * MCP Tool 디스패처.
 * Enum 기반으로 도구를 라우팅하고 적절한 핸들러를 호출한다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class McpToolDispatcher {

    private final DocumentService documentService;
    private final SearchService searchService;
    private final SemanticSearchService semanticSearchService;
    private final HybridSearchService hybridSearchService;
    private final SyncService syncService;
    private final ProjectService projectService;

    // Phase 5 쓰기 서비스들
    private final DocumentWriteService documentWriteService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 도구별 핸들러 맵.
     * Key: McpTool Enum
     * Value: McpToolHandler 구현체
     */
    private final Map<McpTool, McpToolHandler<?, ?>> handlers = new HashMap<>();

    /**
     * 서비스 시작 시 핸들러를 등록한다.
     */
    @PostConstruct
    public void registerHandlers() {
        // READ 도구들
        registerHandler(McpTool.LIST_PROJECTS, this::handleListProjects);
        registerHandler(McpTool.LIST_DOCUMENTS, this::handleListDocuments);
        registerHandler(McpTool.GET_DOCUMENT, this::handleGetDocument);
        registerHandler(McpTool.LIST_DOCUMENT_VERSIONS, this::handleListDocumentVersions);
        registerHandler(McpTool.DIFF_DOCUMENT, this::handleDiffDocument);
        registerHandler(McpTool.SEARCH_DOCUMENTS, this::handleSearchDocuments);
        registerHandler(McpTool.SYNC_REPOSITORY, this::handleSyncRepository);

        // WRITE 도구들 (Phase 5)
        registerHandler(McpTool.CREATE_DOCUMENT, this::handleCreateDocument);
        registerHandler(McpTool.UPDATE_DOCUMENT, this::handleUpdateDocument);
        registerHandler(McpTool.PUSH_TO_REMOTE, this::handlePushToRemote);

        log.info("Registered {} MCP tool handlers", handlers.size());
    }

    /**
     * 핸들러를 등록한다.
     *
     * @param tool MCP 도구
     * @param handler 핸들러
     * @param <I> 입력 타입
     * @param <R> 결과 타입
     */
    private <I, R> void registerHandler(McpTool tool, McpToolHandler<I, R> handler) {
        handlers.put(tool, handler);
    }

    /**
     * 도구를 디스패치하여 실행한다.
     *
     * @param toolName 도구 이름
     * @param input 입력 객체 (Map 또는 POJO)
     * @param <T> 결과 타입
     * @return MCP 응답
     */
    @SuppressWarnings("unchecked")
    public <T> McpResponse<T> dispatch(String toolName, Object input) {
        try {
            // 1. 도구 조회
            McpTool tool = McpTool.fromName(toolName)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown tool: " + toolName));

            // 2. 핸들러 조회
            McpToolHandler<Object, T> handler = (McpToolHandler<Object, T>) handlers.get(tool);
            if (handler == null) {
                return McpResponse.error("Handler not registered for tool: " + toolName);
            }

            // 3. 입력 변환
            Object convertedInput = objectMapper.convertValue(input, tool.getInputClass());

            // 4. 핸들러 실행
            T result = handler.handle(convertedInput);

            return McpResponse.success(result);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid input for tool {}: {}", toolName, e.getMessage());
            return McpResponse.error("Invalid input: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error executing tool {}: {}", toolName, e.getMessage(), e);
            return McpResponse.error("Execution error: " + e.getMessage());
        }
    }

    // ===== READ 도구 핸들러들 =====

    private ListProjectsResult handleListProjects(ListProjectsInput input) throws Exception {
        UserPrincipal principal = getCurrentUserPrincipal();
        if (principal == null) {
            throw new IllegalStateException("인증되지 않은 사용자입니다");
        }

        var members = projectService.findByMemberUserId(principal.id()).stream()
                .map(project -> {
                    // 해당 프로젝트에서 사용자의 역할 조회
                    String role = projectService.findMember(project.getId(), principal.id())
                            .map(m -> m.getRole().name())
                            .orElse("VIEWER");
                    return new ProjectSummary(
                            project.getId(),
                            project.getName(),
                            project.getDescription(),
                            role
                    );
                })
                .toList();

        return new ListProjectsResult(members);
    }

    private ListDocumentsResult handleListDocuments(ListDocumentsInput input) throws Exception {
        // repositoryId가 없을 때만 projectId 해결 (기본 프로젝트 폴백)
        UUID projectId = input.repositoryId() == null
                ? resolveProjectId(input.projectId())
                : input.projectId();

        var documents = input.repositoryId() != null
                ? documentService.findByRepositoryId(input.repositoryId(), input.pathPrefix(), input.type())
                : documentService.findByProjectId(projectId);

        var summaries = documents.stream()
                .map(doc -> new DocumentSummary(
                        doc.getId(),
                        doc.getRepository().getId(),
                        doc.getPath(),
                        doc.getTitle(),
                        doc.getDocType().name(),
                        doc.getLatestCommitSha()
                ))
                .toList();

        return new ListDocumentsResult(summaries);
    }

    private GetDocumentResult handleGetDocument(GetDocumentInput input) throws Exception {
        var doc = documentService.findById(input.documentId())
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));

        var version = input.commitSha() != null
                ? documentService.findVersion(input.documentId(), input.commitSha())
                : documentService.findLatestVersion(input.documentId());

        var ver = version.orElseThrow(() -> new IllegalArgumentException("Version not found"));

        return new GetDocumentResult(
                doc.getId(),
                doc.getRepository().getId(),
                doc.getPath(),
                doc.getTitle(),
                doc.getDocType().name(),
                ver.getCommitSha(),
                ver.getContent(),
                ver.getAuthorName(),
                ver.getCommittedAt()
        );
    }

    private ListDocumentVersionsResult handleListDocumentVersions(ListDocumentVersionsInput input) throws Exception {
        var versions = documentService.findVersions(input.documentId());
        var summaries = versions.stream()
                .map(v -> new VersionSummary(
                        v.getCommitSha(),
                        v.getAuthorName(),
                        v.getAuthorEmail(),
                        v.getCommittedAt(),
                        v.getMessage()
                ))
                .toList();

        return new ListDocumentVersionsResult(summaries);
    }

    private DiffDocumentResult handleDiffDocument(DiffDocumentInput input) throws Exception {
        var fromOpt = documentService.findVersion(input.documentId(), input.fromCommitSha());
        var toOpt = documentService.findVersion(input.documentId(), input.toCommitSha());

        if (fromOpt.isEmpty() || toOpt.isEmpty()) {
            throw new IllegalArgumentException("Version not found");
        }

        String diff = buildDiff(
                fromOpt.get().getContent(),
                toOpt.get().getContent(),
                input.fromCommitSha(),
                input.toCommitSha()
        );

        return new DiffDocumentResult(diff);
    }

    private SearchDocumentsResult handleSearchDocuments(SearchDocumentsInput input) throws Exception {
        long startTime = System.currentTimeMillis();
        int topK = input.topK() != null ? input.topK() : 10;
        String mode = input.mode() != null ? input.mode() : "keyword";

        // projectId 없으면 기본 프로젝트로 폴백
        UUID projectId = resolveProjectId(input.projectId());

        var results = switch (mode.toLowerCase()) {
            case "semantic" -> semanticSearchService.searchSemantic(projectId, input.query(), topK);
            case "hybrid" -> hybridSearchService.hybridSearch(projectId, input.query(), topK);
            default -> searchService.searchByKeyword(projectId, input.query(), topK);
        };

        var hits = results.stream()
                .map(r -> new SearchHit(
                        r.documentId(),
                        r.path(),
                        null,
                        r.headingPath(),
                        r.score(),
                        r.snippet(),
                        null
                ))
                .toList();

        long elapsed = System.currentTimeMillis() - startTime;
        var metadata = new SearchMetadata(mode, hits.size(), elapsed + "ms");

        return new SearchDocumentsResult(hits, metadata);
    }

    private SyncRepositoryResult handleSyncRepository(SyncRepositoryInput input) throws Exception {
        var job = syncService.startSync(input.repositoryId(), input.branch());
        return new SyncRepositoryResult(job.getId(), job.getStatus().name());
    }

    // ===== WRITE 도구 핸들러들 =====

    private CreateDocumentResult handleCreateDocument(CreateDocumentInput input) throws Exception {
        UserPrincipal principal = getCurrentUserPrincipal();
        if (principal == null) {
            throw new IllegalStateException("인증되지 않은 사용자입니다");
        }
        return documentWriteService.createDocument(input, principal.id(), principal.displayName());
    }

    private UpdateDocumentResult handleUpdateDocument(UpdateDocumentInput input) throws Exception {
        UserPrincipal principal = getCurrentUserPrincipal();
        if (principal == null) {
            throw new IllegalStateException("인증되지 않은 사용자입니다");
        }
        return documentWriteService.updateDocument(input, principal.id(), principal.displayName());
    }

    private PushToRemoteResult handlePushToRemote(PushToRemoteInput input) throws Exception {
        try {
            documentWriteService.pushToRemote(input.repositoryId(), input.branch());
            return new PushToRemoteResult(
                    input.repositoryId(),
                    input.branch() != null ? input.branch() : "main",
                    true,
                    "Successfully pushed to remote"
            );
        } catch (Exception e) {
            return new PushToRemoteResult(
                    input.repositoryId(),
                    input.branch() != null ? input.branch() : "main",
                    false,
                    "Push failed: " + e.getMessage()
            );
        }
    }

    // ===== 유틸리티 메서드 =====

    /**
     * 현재 인증된 사용자의 UserPrincipal을 반환한다.
     *
     * @return UserPrincipal (인증되지 않은 경우 null)
     */
    private UserPrincipal getCurrentUserPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal;
        }
        return null;
    }

    /**
     * 현재 인증된 사용자의 기본 프로젝트 ID를 반환한다.
     * API Key에 설정된 defaultProjectId를 사용한다.
     *
     * @return 기본 프로젝트 ID (없으면 null)
     */
    private UUID getCurrentUserDefaultProjectId() {
        UserPrincipal principal = getCurrentUserPrincipal();
        if (principal != null) {
            return principal.defaultProjectId();
        }
        return null;
    }

    /**
     * projectId를 해결한다.
     * 입력값이 있으면 그대로 사용하고, 없으면 기본 프로젝트를 사용한다.
     *
     * @param inputProjectId 입력 projectId (nullable)
     * @return 해결된 projectId
     * @throws IllegalArgumentException projectId가 없고 기본 프로젝트도 없는 경우
     */
    private UUID resolveProjectId(UUID inputProjectId) {
        if (inputProjectId != null) {
            return inputProjectId;
        }

        UUID defaultProjectId = getCurrentUserDefaultProjectId();
        if (defaultProjectId != null) {
            log.debug("API Key의 기본 프로젝트 사용: {}", defaultProjectId);
            return defaultProjectId;
        }

        throw new IllegalArgumentException(
                "projectId가 필요합니다. list_projects로 사용 가능한 프로젝트를 확인하거나, " +
                "API Key 설정에서 기본 프로젝트를 지정하세요.");
    }

    private String buildDiff(String from, String to, String fromSha, String toSha) {
        var fromLines = from == null ? java.util.List.<String>of() : java.util.List.of(from.split("\\n", -1));
        var toLines = to == null ? java.util.List.<String>of() : java.util.List.of(to.split("\\n", -1));

        StringBuilder sb = new StringBuilder();
        sb.append("--- ").append(fromSha).append("\n");
        sb.append("+++ ").append(toSha).append("\n");

        int max = Math.max(fromLines.size(), toLines.size());
        for (int i = 0; i < max; i++) {
            String fromLine = i < fromLines.size() ? fromLines.get(i) : null;
            String toLine = i < toLines.size() ? toLines.get(i) : null;

            if (fromLine == null) {
                sb.append("+").append(toLine).append("\n");
            } else if (toLine == null) {
                sb.append("-").append(fromLine).append("\n");
            } else if (fromLine.equals(toLine)) {
                sb.append(" ").append(fromLine).append("\n");
            } else {
                sb.append("-").append(fromLine).append("\n");
                sb.append("+").append(toLine).append("\n");
            }
        }
        return sb.toString();
    }
}
