# Phase 16-C: 관리 도구

## 개요

레포지토리 상세 정보와 대시보드 통계를 조회하는 관리용 MCP 도구를 제공한다. 우선순위가 낮지만 완전한 MCP 인터페이스를 위해 필요하다.

## McpProjectTools.java 추가 확장

### 파일 위치
```
backend/src/main/java/com/docst/mcp/tools/McpProjectTools.java
```

기존 `list_projects` 도구가 있는 클래스에 추가한다.

---

## 도구 명세

### 1. get_repository

레포지토리의 상세 정보를 조회한다.

```java
@Tool(name = "get_repository",
      description = "Get detailed information about a specific repository. " +
                    "Returns metadata including clone URL, default branch, and local mirror path.")
public GetRepositoryResult getRepository(
    @ToolParam(description = "Repository ID (UUID format)")
    String repositoryId
)
```

**반환 타입:**
```java
public record GetRepositoryResult(
    UUID id,
    UUID projectId,
    String owner,
    String name,
    String fullName,
    String provider,         // GITHUB, LOCAL
    String cloneUrl,
    String defaultBranch,
    String localMirrorPath,
    boolean active,
    Instant createdAt
) {}
```

---

### 2. get_stats

시스템 전체 또는 프로젝트별 통계를 조회한다.

```java
@Tool(name = "get_stats",
      description = "Get dashboard statistics. " +
                    "Returns total counts of projects, repositories, and documents. " +
                    "If projectId is provided, returns stats for that project only.")
public GetStatsResult getStats(
    @ToolParam(description = "Project ID for project-specific stats (optional)", required = false)
    String projectId
)
```

**반환 타입:**
```java
public record GetStatsResult(
    long totalProjects,
    long totalRepositories,
    long totalDocuments,
    String scope           // "system" or "project"
) {}
```

---

## 구현 체크리스트

- [ ] McpProjectTools.java에 get_repository 추가
- [ ] McpProjectTools.java에 get_stats 추가
- [ ] McpModels.java에 Result 레코드 추가
- [ ] 단위 테스트 작성

---

## 복잡도 평가

| 도구 | 복잡도 | 비고 |
|------|--------|------|
| get_repository | LOW | RepositoryService.findById() 직접 매핑 |
| get_stats | LOW | StatsService 메서드 조합 |

---

## McpModels.java 전체 추가 레코드 요약

Phase 16 전체에서 McpModels.java에 추가해야 할 레코드:

### Graph 관련 (Phase 16-A)
```java
// get_document_graph
public record GetDocumentGraphResult(
    List<GraphNode> nodes,
    List<GraphEdge> edges,
    int nodeCount,
    int edgeCount
) {}

public record GraphNode(
    UUID id,
    String path,
    String title,
    String docType,
    int outgoingLinks,
    int incomingLinks
) {}

public record GraphEdge(
    UUID id,
    UUID source,
    UUID target,
    String linkType,
    String anchorText
) {}

// analyze_impact
public record AnalyzeImpactResult(
    UUID documentId,
    int totalImpacted,
    List<ImpactedDocument> directImpact,
    List<ImpactedDocument> indirectImpact
) {}

public record ImpactedDocument(
    UUID id,
    String path,
    String title,
    int depth,
    String linkType,
    String anchorText
) {}

// get_document_links
public record GetDocumentLinksResult(
    UUID documentId,
    List<DocumentLinkInfo> outgoingLinks,
    List<DocumentLinkInfo> incomingLinks
) {}

public record DocumentLinkInfo(
    UUID linkId,
    UUID targetDocumentId,
    String targetPath,
    String targetTitle,
    String linkType,
    String anchorText,
    int lineNumber,
    boolean isBroken
) {}

// get_broken_links
public record GetBrokenLinksResult(
    UUID repositoryId,
    int totalBrokenLinks,
    List<BrokenLinkInfo> brokenLinks
) {}

public record BrokenLinkInfo(
    UUID sourceDocumentId,
    String sourceDocumentPath,
    String brokenLinkText,
    int lineNumber
) {}
```

### Repository 관련 (Phase 16-A, 16-C)
```java
// list_repositories
public record ListRepositoriesResult(List<RepositorySummary> repositories) {}

public record RepositorySummary(
    UUID id,
    String owner,
    String name,
    String fullName,
    String provider,
    String defaultBranch,
    boolean active,
    Instant createdAt
) {}

// get_repository
public record GetRepositoryResult(
    UUID id,
    UUID projectId,
    String owner,
    String name,
    String fullName,
    String provider,
    String cloneUrl,
    String defaultBranch,
    String localMirrorPath,
    boolean active,
    Instant createdAt
) {}

// get_folder_tree
public record GetFolderTreeResult(UUID repositoryId, List<FolderNode> folders) {}

public record FolderNode(String path, String name, List<FolderNode> children) {}
```

### Branch 관련 (Phase 16-B)
```java
public record ListBranchesResult(UUID repositoryId, String currentBranch, List<String> branches) {}

public record GetCurrentBranchResult(UUID repositoryId, String branch) {}

public record CreateBranchResult(
    UUID repositoryId,
    String branchName,
    String fromBranch,
    boolean success,
    String message
) {}

public record SwitchBranchResult(
    UUID repositoryId,
    String branch,
    boolean success,
    String message
) {}
```

### Commit 관련 (Phase 16-B)
```java
public record ListCommitsResult(
    UUID repositoryId,
    String branch,
    int page,
    int size,
    List<CommitSummary> commits
) {}

public record CommitSummary(
    String sha,
    String shortSha,
    String shortMessage,
    String authorName,
    String authorEmail,
    Instant committedAt
) {}

public record GetCommitChangesResult(
    UUID repositoryId,
    String commitSha,
    List<ChangedFileInfo> changedFiles
) {}

public record ChangedFileInfo(String path, String changeType, String oldPath) {}

public record DiffCommitsResult(
    UUID repositoryId,
    String fromSha,
    String toSha,
    List<ChangedFileInfo> changedFiles
) {}

public record ListUnpushedCommitsResult(
    UUID repositoryId,
    String branch,
    int count,
    List<CommitSummary> commits
) {}
```

### Stats 관련 (Phase 16-C)
```java
public record GetStatsResult(
    long totalProjects,
    long totalRepositories,
    long totalDocuments,
    String scope
) {}
```

---

## McpServerConfig.java 최종 형태

```java
@Configuration
@RequiredArgsConstructor
@Slf4j
public class McpServerConfig {

    private final McpDocumentTools documentTools;
    private final McpGitTools gitTools;
    private final McpProjectTools projectTools;
    private final McpGraphTools graphTools;      // Phase 16-A
    private final McpBranchTools branchTools;    // Phase 16-B
    private final McpCommitTools commitTools;    // Phase 16-B

    @Bean
    public ToolCallbackProvider mcpToolCallbackProvider() {
        log.info("Registering MCP tools: DocumentTools, GitTools, ProjectTools, " +
                 "GraphTools, BranchTools, CommitTools");
        return MethodToolCallbackProvider.builder()
            .toolObjects(
                documentTools,
                gitTools,
                projectTools,
                graphTools,
                branchTools,
                commitTools
            )
            .build();
    }
}
```

---

## 검증 방법

### MCP 클라이언트 연결 테스트

```bash
# mcp-remote로 연결
npx mcp-remote http://localhost:8342/sse --header "X-API-Key: YOUR_API_KEY"
```

### 도구 목록 확인

MCP Inspector 또는 Claude Desktop에서 사용 가능한 도구 목록 확인:
- 기존 10개 + 신규 16개 = 총 26개 도구

### 기능 테스트 시나리오

1. **그래프 조회**: `get_document_graph` → 노드/엣지 반환 확인
2. **영향 분석**: `analyze_impact` → 직접/간접 영향 문서 확인
3. **브랜치 생성**: `create_branch` → `list_branches`로 확인
4. **커밋 조회**: `list_commits` → 페이지네이션 동작 확인
5. **통계**: `get_stats` → 카운트 정확성 확인