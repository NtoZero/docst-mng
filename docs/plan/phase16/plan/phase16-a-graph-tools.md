# Phase 16-A: 그래프 및 링크 도구

## 개요

문서 간 관계를 분석하고 영향도를 파악하는 MCP 도구를 제공한다. AI 에이전트가 문서 수정 전 영향 범위를 파악하거나, 관련 문서를 탐색하는 데 활용된다.

## 새 클래스: McpGraphTools.java

### 파일 위치
```
backend/src/main/java/com/docst/mcp/tools/McpGraphTools.java
```

### 의존성
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class McpGraphTools {
    private final GraphService graphService;
    private final DocumentLinkService documentLinkService;
}
```

---

## 도구 명세

### 1. get_document_graph

문서 관계 그래프를 조회한다. 프로젝트, 레포지토리, 또는 특정 문서 중심으로 범위를 지정할 수 있다.

```java
@Tool(name = "get_document_graph",
      description = "Get document relationship graph showing how documents link to each other. " +
                    "Use scope='project' for all documents in a project, " +
                    "'repository' for a single repo, " +
                    "or 'document' for a specific document and its neighbors.")
public GetDocumentGraphResult getDocumentGraph(
    @ToolParam(description = "Scope of graph: 'project', 'repository', or 'document'")
    String scope,

    @ToolParam(description = "ID for the scope (projectId, repositoryId, or documentId)", required = false)
    String scopeId,

    @ToolParam(description = "Traversal depth for 'document' scope (default: 2, max: 5)", required = false)
    Integer depth
)
```

**사용 예시:**
```json
// 프로젝트 전체 그래프
{ "scope": "project", "scopeId": "uuid-here" }

// 특정 문서 주변 그래프 (깊이 3)
{ "scope": "document", "scopeId": "doc-uuid", "depth": 3 }
```

**반환 타입:**
```java
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
```

---

### 2. analyze_impact

문서 변경 시 영향받는 문서들을 분석한다. 직접 참조(depth 1)와 간접 참조(depth 2) 문서를 구분하여 반환한다.

```java
@Tool(name = "analyze_impact",
      description = "Analyze the impact of changing a document. " +
                    "Returns documents that directly reference this document (depth 1) " +
                    "and documents that reference those (depth 2). " +
                    "Useful before modifying or deleting a document.")
public AnalyzeImpactResult analyzeImpact(
    @ToolParam(description = "Document ID to analyze impact for (UUID format)")
    String documentId
)
```

**반환 타입:**
```java
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
```

---

### 3. get_document_links

특정 문서의 링크를 조회한다. 나가는 링크, 들어오는 링크, 또는 양방향 모두 조회 가능하다.

```java
@Tool(name = "get_document_links",
      description = "Get links for a specific document. " +
                    "Direction 'outgoing' returns links from this document to others. " +
                    "Direction 'incoming' returns links from other documents to this one (backlinks). " +
                    "Direction 'both' returns both.")
public GetDocumentLinksResult getDocumentLinks(
    @ToolParam(description = "Document ID (UUID format)")
    String documentId,

    @ToolParam(description = "Link direction: 'outgoing', 'incoming', or 'both' (default: both)", required = false)
    String direction
)
```

**반환 타입:**
```java
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
    String linkType,      // INTERNAL, EXTERNAL, ANCHOR
    String anchorText,
    int lineNumber,
    boolean isBroken
) {}
```

---

### 4. get_broken_links

레포지토리 내 깨진 링크를 모두 조회한다. 문서 품질 관리에 활용된다.

```java
@Tool(name = "get_broken_links",
      description = "Find all broken links in a repository. " +
                    "Broken links are references to documents that don't exist. " +
                    "Useful for documentation quality checks.")
public GetBrokenLinksResult getBrokenLinks(
    @ToolParam(description = "Repository ID (UUID format)")
    String repositoryId
)
```

**반환 타입:**
```java
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

---

## McpProjectTools.java 확장

### 5. list_repositories

프로젝트에 속한 레포지토리 목록을 조회한다.

```java
@Tool(name = "list_repositories",
      description = "List all repositories in a project. " +
                    "Returns repository metadata including name, provider, and sync status.")
public ListRepositoriesResult listRepositories(
    @ToolParam(description = "Project ID (UUID format)")
    String projectId
)
```

**반환 타입:**
```java
public record ListRepositoriesResult(List<RepositorySummary> repositories) {}

public record RepositorySummary(
    UUID id,
    String owner,
    String name,
    String fullName,        // owner/name
    String provider,        // GITHUB, LOCAL
    String defaultBranch,
    boolean active,
    Instant createdAt
) {}
```

---

### 6. get_folder_tree

레포지토리의 폴더 구조를 조회한다. 문서 탐색 전 전체 구조를 파악하는 데 활용된다.

```java
@Tool(name = "get_folder_tree",
      description = "Get the folder structure of a repository. " +
                    "Returns a hierarchical tree of directories. " +
                    "Useful for understanding repository layout before navigating documents.")
public GetFolderTreeResult getFolderTree(
    @ToolParam(description = "Repository ID (UUID format)")
    String repositoryId,

    @ToolParam(description = "Maximum depth to traverse (1-6, default: 4)", required = false)
    Integer maxDepth
)
```

**반환 타입:**
```java
public record GetFolderTreeResult(
    UUID repositoryId,
    List<FolderNode> folders
) {}

public record FolderNode(
    String path,
    String name,
    List<FolderNode> children
) {}
```

---

## 구현 체크리스트

- [ ] McpGraphTools.java 생성
  - [ ] get_document_graph 구현
  - [ ] analyze_impact 구현
  - [ ] get_document_links 구현
  - [ ] get_broken_links 구현
- [ ] McpProjectTools.java 확장
  - [ ] list_repositories 추가
  - [ ] get_folder_tree 추가
- [ ] McpModels.java에 Result 레코드 추가
- [ ] McpServerConfig.java에 McpGraphTools 등록
- [ ] 단위 테스트 작성
- [ ] 통합 테스트 작성

## 복잡도 평가

| 도구 | 복잡도 | 비고 |
|------|--------|------|
| get_document_graph | MEDIUM | GraphService 결과 변환 필요 |
| analyze_impact | LOW | GraphService.analyzeImpact() 직접 매핑 |
| get_document_links | LOW | DocumentLinkService 메서드 조합 |
| get_broken_links | LOW | DocumentLinkService.getBrokenLinks() 직접 사용 |
| list_repositories | LOW | RepositoryService.findByProjectId() 직접 사용 |
| get_folder_tree | MEDIUM | 재귀 구조 변환 필요 |