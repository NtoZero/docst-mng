package com.docst.mcp;

import com.docst.mcp.McpModels.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

/**
 * MCP Tool 정의.
 * 모든 MCP 도구의 이름, 설명, 입력 스키마, 카테고리를 중앙 관리한다.
 */
@Getter
@RequiredArgsConstructor
public enum McpTool {
    // ===== READ 도구 =====
    LIST_PROJECTS(
            "list_projects",
            "List all projects the authenticated user has access to. Use this first to discover available projects before searching documents.",
            ListProjectsInput.class,
            ToolCategory.READ
    ),
    LIST_DOCUMENTS(
            "list_documents",
            "List documents in a project or repository. Can filter by path prefix and document type.",
            ListDocumentsInput.class,
            ToolCategory.READ
    ),
    GET_DOCUMENT(
            "get_document",
            "Get document content by ID. Optionally specify a commit SHA to get a specific version.",
            GetDocumentInput.class,
            ToolCategory.READ
    ),
    LIST_DOCUMENT_VERSIONS(
            "list_document_versions",
            "List all versions (commits) of a document, ordered by commit time (newest first).",
            ListDocumentVersionsInput.class,
            ToolCategory.READ
    ),
    DIFF_DOCUMENT(
            "diff_document",
            "Compare two versions of a document and return the diff.",
            DiffDocumentInput.class,
            ToolCategory.READ
    ),
    SEARCH_DOCUMENTS(
            "search_documents",
            "Search documents in a project. Supports keyword, semantic, and hybrid search modes.",
            SearchDocumentsInput.class,
            ToolCategory.READ
    ),
    SYNC_REPOSITORY(
            "sync_repository",
            "Trigger synchronization of a repository from remote Git. Returns a job ID to track progress.",
            SyncRepositoryInput.class,
            ToolCategory.READ
    ),

    // ===== WRITE 도구 =====
    CREATE_DOCUMENT(
            "create_document",
            "Create a new document in a repository. Optionally create a commit immediately.",
            CreateDocumentInput.class,
            ToolCategory.WRITE
    ),
    UPDATE_DOCUMENT(
            "update_document",
            "Update an existing document's content. Optionally create a commit immediately.",
            UpdateDocumentInput.class,
            ToolCategory.WRITE
    ),
    PUSH_TO_REMOTE(
            "push_to_remote",
            "Push local commits to the remote repository.",
            PushToRemoteInput.class,
            ToolCategory.WRITE
    );

    private final String name;
    private final String description;
    private final Class<?> inputClass;
    private final ToolCategory category;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 도구 이름으로 McpTool을 찾는다.
     *
     * @param name 도구 이름
     * @return McpTool (존재하지 않으면 empty)
     */
    public static Optional<McpTool> fromName(String name) {
        return Arrays.stream(values())
                .filter(tool -> tool.name.equals(name))
                .findFirst();
    }

    /**
     * 입력 스키마를 JSON Schema 형식으로 반환한다.
     * 실제 구현에서는 Jackson의 JSON Schema 생성 라이브러리를 사용하거나,
     * 수동으로 스키마를 정의할 수 있다.
     *
     * @return JSON Schema
     */
    public JsonNode getInputSchema() {
        // 간단한 스키마 생성 (실제로는 더 정교한 구현 필요)
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.put("title", inputClass.getSimpleName());
        schema.put("description", description);

        // TODO: Reflection을 통해 필드 정보 추출하여 properties 생성
        // 또는 @JsonSchemaProperty 같은 어노테이션 사용
        // 현재는 기본 스키마만 반환

        return schema;
    }

    /**
     * MCP Tool 정의 객체로 변환한다.
     * MCP Server의 tools/list 응답에 사용된다.
     *
     * @return MCP Tool 정의
     */
    public McpToolDefinition toDefinition() {
        return new McpToolDefinition(
                this.name,
                this.description,
                this.getInputSchema()
        );
    }

    /**
     * 도구 카테고리.
     */
    public enum ToolCategory {
        /**
         * 읽기 전용 도구 (부작용 없음)
         */
        READ,

        /**
         * 쓰기 도구 (Git 커밋, 파일 수정 등)
         */
        WRITE
    }

    /**
     * MCP Tool 정의 DTO.
     * MCP Server의 tools/list 응답에 사용된다.
     *
     * @param name 도구 이름
     * @param description 도구 설명
     * @param inputSchema 입력 JSON Schema
     */
    public record McpToolDefinition(
            String name,
            String description,
            JsonNode inputSchema
    ) {}
}
