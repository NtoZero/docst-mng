# Phase 17-D: MCP 확장

**상태**: 🔲 예정

## 개요

AI 에이전트가 용어를 생성/수정/삭제하고 일괄 등록할 수 있는 MCP 도구 추가.

## 현재 구현된 MCP Tools

| Tool | Description | Role |
|------|-------------|------|
| `list_glossary_terms` | 용어 목록 조회 | VIEWER |
| `search_glossary` | 키워드/시맨틱 검색 | VIEWER |
| `get_glossary_term` | 용어 상세 조회 | VIEWER |

## 추가할 MCP Tools

| Tool | Description | Role |
|------|-------------|------|
| `create_glossary_term` | 새 용어 생성 | EDITOR |
| `update_glossary_term` | 용어 수정 | EDITOR |
| `delete_glossary_term` | 용어 삭제 | EDITOR |
| `batch_import_glossary` | 용어 일괄 등록 | EDITOR |
| `list_glossary_categories` | 카테고리 목록 조회 | VIEWER |

## Spring AI Annotation Pattern

현재 프로젝트는 **Spring AI 1.1.0+ `@Tool` / `@ToolParam` annotation** 패턴을 사용합니다.

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class McpGlossaryTools {

    @Tool(name = "create_glossary_term", description = "...")
    public CreateResult createGlossaryTerm(
        @ToolParam(description = "...") String projectId,
        @ToolParam(description = "...") String name,
        // ... more params
    ) {
        // implementation
    }
}
```

## 구현 상세

### 1. create_glossary_term

```java
@Tool(name = "create_glossary_term",
      description = "Create a new glossary term in a project. " +
          "The term name must be unique within the project. " +
          "Requires EDITOR role.")
public CreateGlossaryTermResult createGlossaryTerm(
    @ToolParam(description = "Project ID") String projectId,
    @ToolParam(description = "Term name (must be unique within project)") String name,
    @ToolParam(description = "Term definition") String definition,
    @ToolParam(description = "Category (e.g., 'Architecture', 'API', 'Domain')", required = false) String category,
    @ToolParam(description = "Abbreviation (e.g., 'SSO' for 'Single Sign-On')", required = false) String abbreviation,
    @ToolParam(description = "Comma-separated synonyms (e.g., 'API Key,api-key')", required = false) String synonyms
) {
    log.info("MCP Tool: createGlossaryTerm - projectId={}, name={}", projectId, name);

    UserPrincipal principal = SecurityUtils.requireCurrentUserPrincipal();
    UUID projId = UUID.fromString(projectId);

    // EDITOR 권한 확인
    projectService.findMember(projId, principal.id())
        .filter(m -> m.getRole().canEdit())
        .orElseThrow(() -> new IllegalArgumentException("EDITOR role required"));

    List<String> synonymList = synonyms != null && !synonyms.isBlank()
        ? Arrays.asList(synonyms.split(","))
        : null;

    GlossaryTerm term = glossaryService.create(projId, principal.id(),
        new CreateGlossaryTermRequest(name, definition, category, abbreviation, synonymList, null));

    return new CreateGlossaryTermResult(term.getId().toString(), name, "Term created successfully");
}

public record CreateGlossaryTermResult(String termId, String name, String message) {}
```

### 2. update_glossary_term

```java
@Tool(name = "update_glossary_term",
      description = "Update an existing glossary term. " +
          "Only provided fields will be updated (partial update). " +
          "Requires EDITOR role.")
public UpdateGlossaryTermResult updateGlossaryTerm(
    @ToolParam(description = "Glossary term ID to update") String termId,
    @ToolParam(description = "New term name (optional)", required = false) String name,
    @ToolParam(description = "New definition (optional)", required = false) String definition,
    @ToolParam(description = "New category (optional)", required = false) String category,
    @ToolParam(description = "New abbreviation (optional)", required = false) String abbreviation,
    @ToolParam(description = "Comma-separated synonyms (optional, replaces existing)", required = false) String synonyms
) {
    log.info("MCP Tool: updateGlossaryTerm - termId={}", termId);

    UserPrincipal principal = SecurityUtils.requireCurrentUserPrincipal();
    UUID id = UUID.fromString(termId);

    GlossaryTerm existing = glossaryService.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Term not found: " + termId));

    UUID projectId = existing.getProject().getId();

    // EDITOR 권한 확인
    projectService.findMember(projectId, principal.id())
        .filter(m -> m.getRole().canEdit())
        .orElseThrow(() -> new IllegalArgumentException("EDITOR role required"));

    List<String> synonymList = synonyms != null && !synonyms.isBlank()
        ? Arrays.asList(synonyms.split(","))
        : null;

    GlossaryTerm updated = glossaryService.update(projectId, id,
        new UpdateGlossaryTermRequest(name, definition, category, abbreviation, synonymList, null));

    return new UpdateGlossaryTermResult(termId, updated.getName(), "Term updated successfully");
}

public record UpdateGlossaryTermResult(String termId, String name, String message) {}
```

### 3. delete_glossary_term

```java
@Tool(name = "delete_glossary_term",
      description = "Delete a glossary term permanently. " +
          "This action cannot be undone. " +
          "Requires EDITOR role.")
public DeleteGlossaryTermResult deleteGlossaryTerm(
    @ToolParam(description = "Glossary term ID to delete") String termId
) {
    log.info("MCP Tool: deleteGlossaryTerm - termId={}", termId);

    UserPrincipal principal = SecurityUtils.requireCurrentUserPrincipal();
    UUID id = UUID.fromString(termId);

    GlossaryTerm existing = glossaryService.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Term not found: " + termId));

    UUID projectId = existing.getProject().getId();
    String termName = existing.getName();

    // EDITOR 권한 확인
    projectService.findMember(projectId, principal.id())
        .filter(m -> m.getRole().canEdit())
        .orElseThrow(() -> new IllegalArgumentException("EDITOR role required"));

    glossaryService.delete(projectId, id);

    return new DeleteGlossaryTermResult(termId, termName, "Term deleted successfully");
}

public record DeleteGlossaryTermResult(String termId, String name, String message) {}
```

### 4. batch_import_glossary

```java
@Tool(name = "batch_import_glossary",
      description = "Import multiple glossary terms at once. " +
          "Provide terms as JSON array. " +
          "Set updateExisting=true to update existing terms with same name. " +
          "Requires EDITOR role.")
public BatchImportGlossaryResult batchImportGlossary(
    @ToolParam(description = "Project ID") String projectId,
    @ToolParam(description = "JSON array of terms: [{\"name\":\"...\",\"definition\":\"...\",\"category\":\"...\"}]") String termsJson,
    @ToolParam(description = "Update existing terms with same name (default: false)", required = false) Boolean updateExisting
) {
    log.info("MCP Tool: batchImportGlossary - projectId={}", projectId);

    UserPrincipal principal = SecurityUtils.requireCurrentUserPrincipal();
    UUID projId = UUID.fromString(projectId);

    // EDITOR 권한 확인
    projectService.findMember(projId, principal.id())
        .filter(m -> m.getRole().canEdit())
        .orElseThrow(() -> new IllegalArgumentException("EDITOR role required"));

    // JSON 파싱
    ObjectMapper mapper = new ObjectMapper();
    List<GlossaryTermImportItem> items;
    try {
        items = mapper.readValue(termsJson,
            new TypeReference<List<GlossaryTermImportItem>>() {});
    } catch (JsonProcessingException e) {
        throw new IllegalArgumentException("Invalid JSON format: " + e.getMessage());
    }

    boolean update = updateExisting != null && updateExisting;
    BatchImportResponse result = glossaryService.batchImport(projId, principal.id(), items, update);

    return new BatchImportGlossaryResult(
        result.totalCount(),
        result.createdCount(),
        result.updatedCount(),
        result.skippedCount(),
        result.errors().stream()
            .map(e -> e.termName() + ": " + e.errorMessage())
            .toList()
    );
}

public record BatchImportGlossaryResult(
    int totalCount,
    int createdCount,
    int updatedCount,
    int skippedCount,
    List<String> errors
) {}
```

### 5. list_glossary_categories

```java
@Tool(name = "list_glossary_categories",
      description = "Get list of all categories used in the project's glossary. " +
          "Useful for filtering or organizing terms.")
public ListGlossaryCategoriesResult listGlossaryCategories(
    @ToolParam(description = "Project ID") String projectId
) {
    log.info("MCP Tool: listGlossaryCategories - projectId={}", projectId);

    UserPrincipal principal = SecurityUtils.getCurrentUserPrincipal();
    if (principal == null) {
        throw new IllegalStateException("Authentication required");
    }

    UUID projId = UUID.fromString(projectId);

    // 프로젝트 접근 권한 확인
    if (projectService.findMember(projId, principal.id()).isEmpty()) {
        throw new IllegalArgumentException("Project not found or access denied");
    }

    List<String> categories = glossaryService.getDistinctCategories(projId);

    return new ListGlossaryCategoriesResult(categories);
}

public record ListGlossaryCategoriesResult(List<String> categories) {}
```

## McpServerConfig 등록

```java
// McpServerConfig.java
@Bean
public MethodToolCallbackProvider toolProvider(
    McpDocumentTools documentTools,
    McpGitTools gitTools,
    McpProjectTools projectTools,
    McpGlossaryTools glossaryTools  // 이미 등록됨
) {
    return MethodToolCallbackProvider.builder()
        .toolObjects(documentTools, gitTools, projectTools, glossaryTools)
        .build();
}
```

## 수정 파일

### Backend 수정
- `McpGlossaryTools.java` - 5개 Tool 메서드 추가
- `GlossaryService.java` - batchImport 메서드 (Phase 17-C와 공유)

### 의존성 (이미 존재)
- Jackson ObjectMapper (Spring Boot 기본 포함)

## MCP 사용 예시

### Claude Desktop에서 사용

```
User: 프로젝트 XXX에 "마이크로서비스" 용어를 추가해줘

Claude: [create_glossary_term 호출]
- projectId: XXX
- name: "마이크로서비스"
- definition: "독립적으로 배포 가능한 작은 서비스..."
- category: "Architecture"
```

```
User: 아래 용어들을 일괄 등록해줘:
- SSO: Single Sign-On, 한 번의 로그인으로 여러 시스템 접근
- MFA: Multi-Factor Authentication, 다중 인증

Claude: [batch_import_glossary 호출]
- projectId: XXX
- termsJson: [{"name":"SSO","definition":"...","abbreviation":"SSO"},...]
```

## 의존성

- Phase 17-A 완료 (✅)
- Phase 17-C 완료 (batchImport 메서드 공유)

## 구현 순서

1. `list_glossary_categories` Tool 추가
2. `create_glossary_term` Tool 추가
3. `update_glossary_term` Tool 추가
4. `delete_glossary_term` Tool 추가
5. `batch_import_glossary` Tool 추가 (Phase 17-C 이후)
6. 통합 테스트 (Claude Desktop)

## 보안 고려사항

- 모든 쓰기 작업은 EDITOR 이상 권한 필요
- 프로젝트 멤버십 검증 필수
- API Key 인증 지원 (MCP 클라이언트용)
