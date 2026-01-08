# MCP 고급 기능 - API Key 기본 프로젝트 & list_projects 도구

## 개요

Claude Desktop에서 MCP 도구 호출 시 `projectId`가 NULL로 전달되어 문서를 찾지 못하는 문제를 해결합니다.

### 문제 상황

```sql
-- 실제 실행된 쿼리
WHERE r.project_id = NULL   -- 항상 0건 반환
```

Claude가 `projectId`를 알지 못하기 때문에 전달하지 못함.

### 해결 방안

1. **API Key에 기본 프로젝트 연결** - MCP 호출 시 projectId 없으면 자동 사용
2. **list_projects 도구 추가** - Claude가 프로젝트 목록을 조회하여 선택 가능

---

## 구현 계획

### Step 1: DB 마이그레이션

**파일**: `backend/src/main/resources/db/migration/V11__add_api_key_default_project.sql`

```sql
-- API Key에 기본 프로젝트 연결
ALTER TABLE dm_api_key
ADD COLUMN default_project_id UUID REFERENCES dm_project(id);

-- 인덱스 추가
CREATE INDEX idx_api_key_default_project ON dm_api_key(default_project_id);

COMMENT ON COLUMN dm_api_key.default_project_id IS 'MCP 호출 시 projectId가 없으면 사용되는 기본 프로젝트';
```

### Step 2: ApiKey 엔티티 수정

**파일**: `backend/src/main/java/com/docst/domain/ApiKey.java`

```java
/**
 * Default project for MCP tool calls.
 * When projectId is not provided in MCP requests, this project will be used.
 */
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "default_project_id")
private Project defaultProject;

// Setter 추가
public void setDefaultProject(Project defaultProject) {
    this.defaultProject = defaultProject;
}
```

### Step 3: UserPrincipal에 defaultProjectId 추가

**파일**: `backend/src/main/java/com/docst/auth/UserPrincipal.java`

```java
public record UserPrincipal(
        UUID id,
        String email,
        String displayName,
        UUID defaultProjectId  // 추가: MCP 기본 프로젝트
) implements Principal {

    /**
     * User 엔티티에서 UserPrincipal 생성 (기본 프로젝트 없음)
     */
    public static UserPrincipal from(User user) {
        return new UserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                null
        );
    }

    /**
     * User 엔티티와 기본 프로젝트 ID로 UserPrincipal 생성
     */
    public static UserPrincipal from(User user, UUID defaultProjectId) {
        return new UserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                defaultProjectId
        );
    }

    @Override
    public String getName() {
        return email;
    }
}
```

### Step 4: ApiKeyService 수정

**파일**: `backend/src/main/java/com/docst/service/ApiKeyService.java`

`authenticateByApiKey()` 메서드가 `ApiKey` 엔티티를 반환하도록 수정:

```java
/**
 * API Key로 인증하고 ApiKey 엔티티 반환.
 * Filter에서 defaultProjectId를 추출할 수 있도록 함.
 */
@Transactional
public Optional<ApiKey> authenticateByApiKey(String apiKey) {
    // ... 기존 검증 로직

    return Optional.of(key);  // User 대신 ApiKey 반환
}
```

### Step 5: ApiKeyAuthenticationFilter 수정

**파일**: `backend/src/main/java/com/docst/auth/ApiKeyAuthenticationFilter.java`

```java
Optional<ApiKey> apiKeyOpt = apiKeyService.authenticateByApiKey(apiKey);

if (apiKeyOpt.isPresent()) {
    ApiKey key = apiKeyOpt.get();
    User user = key.getUser();

    // defaultProjectId 추출
    UUID defaultProjectId = key.getDefaultProject() != null
            ? key.getDefaultProject().getId()
            : null;

    UserPrincipal principal = UserPrincipal.from(user, defaultProjectId);

    // ... 인증 토큰 생성
}
```

### Step 6: list_projects MCP 도구 추가

**McpModels.java**:

```java
// ===== list_projects =====

/**
 * list_projects 도구 입력 (파라미터 없음)
 */
public record ListProjectsInput() {}

/**
 * list_projects 도구 결과
 */
public record ListProjectsResult(
        List<ProjectSummary> projects,
        UUID defaultProjectId  // 현재 API Key의 기본 프로젝트
) {}

/**
 * 프로젝트 요약 정보
 */
public record ProjectSummary(
        UUID id,
        String name,
        String description,
        String role,           // OWNER, ADMIN, EDITOR, VIEWER
        int documentCount,     // 문서 수
        int repositoryCount    // 레포지토리 수
) {}
```

**McpTool.java**:

```java
// 맨 위에 추가 (READ 도구)
LIST_PROJECTS(
        "list_projects",
        "List all projects the authenticated user has access to. Returns project details including the default project for this API key.",
        ListProjectsInput.class,
        ToolCategory.READ
),
```

**McpToolDispatcher.java**:

```java
// registerHandlers()에 추가
registerHandler(McpTool.LIST_PROJECTS, this::handleListProjects);

// 핸들러 구현
private ListProjectsResult handleListProjects(ListProjectsInput input) {
    UserPrincipal principal = getCurrentUserPrincipal();

    List<Project> projects = projectService.findByMemberUserId(principal.id());

    var summaries = projects.stream()
            .map(p -> {
                var membership = projectService.findMember(p.getId(), principal.id());
                String role = membership.map(m -> m.getRole().name()).orElse("VIEWER");
                int docCount = documentService.countByProjectId(p.getId());
                int repoCount = repositoryService.countByProjectId(p.getId());

                return new ProjectSummary(
                        p.getId(),
                        p.getName(),
                        p.getDescription(),
                        role,
                        docCount,
                        repoCount
                );
            })
            .toList();

    return new ListProjectsResult(summaries, principal.defaultProjectId());
}

private UserPrincipal getCurrentUserPrincipal() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
        return principal;
    }
    throw new IllegalStateException("User not authenticated");
}
```

### Step 7: MCP 핸들러에서 기본 프로젝트 자동 사용

**McpToolDispatcher.java** - `handleListDocuments`, `handleSearchDocuments` 수정:

```java
private ListDocumentsResult handleListDocuments(ListDocumentsInput input) {
    UUID projectId = resolveProjectId(input.projectId(), input.repositoryId());

    var documents = input.repositoryId() != null
            ? documentService.findByRepositoryId(input.repositoryId(), input.pathPrefix(), input.type())
            : documentService.findByProjectId(projectId);

    // ... 기존 로직
}

private SearchDocumentsResult handleSearchDocuments(SearchDocumentsInput input) {
    UUID projectId = resolveProjectId(input.projectId(), null);

    // ... 기존 로직 (input.projectId() 대신 projectId 사용)
}

/**
 * projectId를 결정한다.
 * 1. 명시적으로 전달된 projectId 사용
 * 2. repositoryId가 있으면 projectId 불필요
 * 3. 둘 다 없으면 기본 프로젝트 사용
 * 4. 기본 프로젝트도 없으면 오류
 */
private UUID resolveProjectId(UUID projectId, UUID repositoryId) {
    if (projectId != null) {
        return projectId;
    }
    if (repositoryId != null) {
        return null;  // repositoryId로 조회 가능
    }

    UserPrincipal principal = getCurrentUserPrincipal();
    if (principal.defaultProjectId() != null) {
        return principal.defaultProjectId();
    }

    throw new IllegalArgumentException(
            "projectId is required. Use 'list_projects' tool to see available projects, " +
            "or set a default project in your API Key settings at Settings > API Keys."
    );
}
```

---

## API Key 기본 프로젝트 설정 UI

### 프론트엔드 변경 사항

**Settings > API Keys 페이지**:
- API Key 생성/수정 시 "Default Project" 선택 드롭다운 추가
- 사용자가 접근 가능한 프로젝트 목록 표시
- 선택된 프로젝트가 MCP 호출의 기본값으로 사용됨을 안내

### 백엔드 API 변경

**PATCH /api/auth/api-keys/{id}**:
```json
{
  "defaultProjectId": "550e8400-e29b-41d4-a716-446655440000"
}
```

---

## 테스트 시나리오

| 시나리오 | 입력 | 기대 결과 |
|---------|------|----------|
| list_projects 호출 | (없음) | 사용자 접근 가능 프로젝트 목록 + 기본 프로젝트 ID |
| list_documents (projectId 있음) | projectId=xxx | 해당 프로젝트 문서 반환 |
| list_documents (기본 프로젝트 설정됨) | (없음) | 기본 프로젝트 문서 반환 |
| list_documents (기본 프로젝트 없음) | (없음) | 오류: "projectId is required..." |
| search_documents (기본 프로젝트 설정됨) | query="test" | 기본 프로젝트에서 검색 |

---

## 수정 파일 목록

| 파일 | 변경 내용 |
|------|----------|
| `db/migration/V11__add_api_key_default_project.sql` | default_project_id 컬럼 추가 |
| `domain/ApiKey.java` | defaultProject 필드 추가 |
| `auth/UserPrincipal.java` | defaultProjectId 필드 추가 |
| `service/ApiKeyService.java` | ApiKey 반환하도록 수정 |
| `auth/ApiKeyAuthenticationFilter.java` | defaultProjectId 포함하여 인증 |
| `mcp/McpTool.java` | LIST_PROJECTS 도구 추가 |
| `mcp/McpModels.java` | ListProjectsInput/Result 추가 |
| `mcp/McpToolDispatcher.java` | list_projects 핸들러 + resolveProjectId 로직 |

---

## 관련 문서

- [MCP 연동 가이드](../../mcp/mcp-connect.md)
- [MCP 트러블슈팅](../mcp-troubleshootings.md)
