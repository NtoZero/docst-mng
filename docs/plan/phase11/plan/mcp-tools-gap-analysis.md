# Phase 11: MCP Tools Gap 분석 및 확장 계획

## 개요

Docst 프로젝트에서 Backend에 구현되었지만 MCP Tools로 노출되지 않거나, 보완이 필요한 항목을 분석하고 확장 계획을 수립합니다.

---

## 1. 현재 MCP Tools 현황 (10개)

### READ Tools (7개)

| Tool | 설명 | Input | 권한 |
|------|------|-------|------|
| `list_projects` | 프로젝트 목록 조회 | - | VIEWER |
| `list_documents` | 문서 목록 조회 | repositoryId/projectId, pathPrefix, type | VIEWER |
| `get_document` | 문서 내용 조회 | documentId, commitSha? | VIEWER |
| `list_document_versions` | 버전 목록 조회 | documentId | VIEWER |
| `diff_document` | 두 버전 비교 | documentId, fromCommitSha, toCommitSha | VIEWER |
| `search_documents` | 문서 검색 | projectId, query, mode, topK | VIEWER |
| `sync_repository` | 동기화 실행 | repositoryId, branch? | ADMIN |

### WRITE Tools (3개)

| Tool | 설명 | Input | 권한 |
|------|------|-------|------|
| `create_document` | 문서 생성 | repositoryId, path, content, message?, branch? | EDITOR |
| `update_document` | 문서 수정 | documentId, content, message?, branch? | EDITOR |
| `push_to_remote` | 원격 푸시 | repositoryId, branch? | ADMIN |

---

## 2. Gap 분석 결과

### 2.1 REST API에는 있지만 MCP에 없는 기능

#### A. Graph & Link Analysis (높음)

**관련 Controller**: `GraphController`
**관련 Service**: `GraphService`, `DocumentLinkService`

| 기능 | REST API | MCP | AI 활용 시나리오 |
|------|----------|-----|-----------------|
| 문서 관계 그래프 | `GET /documents/{id}/graph` | ❌ | "이 문서와 연결된 문서들 보여줘" |
| Outgoing Links | `GET /documents/{id}/links/outgoing` | ❌ | "이 문서가 참조하는 문서들은?" |
| Incoming Links (백링크) | `GET /documents/{id}/links/incoming` | ❌ | "이 문서를 참조하는 문서들은?" |
| Impact Analysis | `GET /documents/{id}/impact` | ❌ | "이 문서 수정 시 영향받는 문서들?" |
| Broken Links | `GET /repositories/{id}/links/broken` | ❌ | "깨진 링크 찾아줘" |

---

#### B. Commit History (높음)

**관련 Controller**: `CommitController`
**관련 Service**: `CommitService`

| 기능 | REST API | MCP | AI 활용 시나리오 |
|------|----------|-----|-----------------|
| 커밋 히스토리 | `GET /repositories/{id}/commits` | ❌ | "최근 변경 이력 보여줘" |
| 커밋 상세 | `GET /repositories/{id}/commits/{sha}` | ❌ | "이 커밋에서 뭐가 바뀌었어?" |
| 두 커밋 간 변경 | `GET /repositories/{id}/commits/diff` | ❌ | "지난주 이후 변경된 파일들?" |
| Unpushed 커밋 | `GET /repositories/{id}/commits/unpushed` | ❌ | "아직 푸시 안 된 커밋 있어?" |

---

#### C. Branch Operations (높음)

**관련 Controller**: `RepositoriesController`
**관련 Service**: `GitService` (via JGit)

| 기능 | REST API | MCP | AI 활용 시나리오 |
|------|----------|-----|-----------------|
| 브랜치 목록 | `GET /repositories/{id}/branches` | ❌ | "브랜치 목록 보여줘" |
| 브랜치 생성 | `POST /repositories/{id}/branches` | ❌ | "새 브랜치 만들어줘" |
| 브랜치 전환 | `POST /repositories/{id}/branches/{name}/switch` | ❌ | "develop 브랜치로 전환해줘" |
| 현재 브랜치 | `GET /repositories/{id}/branches/current` | ❌ | "지금 어떤 브랜치야?" |

---

#### D. Stats (낮음)

**관련 Controller**: `StatsController`
**관련 Service**: `StatsService`

| 기능 | REST API | MCP | AI 활용 시나리오 |
|------|----------|-----|-----------------|
| 대시보드 통계 | `GET /stats` | ❌ | "프로젝트 현황 요약해줘" |

---

### 2.2 기존 MCP Tool 보완 필요 사항

#### A. `search_documents` - 검색 모드 확장

**현재 지원**: `keyword`, `semantic`, `hybrid`
**REST API 지원**: `keyword`, `semantic`, `hybrid`, `graph`, `auto`

```java
// 현재 MCP
record SearchDocumentsInput(UUID projectId, String query, String mode, Integer topK) {}
// mode: "keyword" | "semantic" | "hybrid"

// 개선 필요
// mode: "keyword" | "semantic" | "hybrid" | "graph" | "auto"
```

**누락 모드**:
- `graph`: Neo4j Graph RAG 기반 검색
- `auto`: QueryRouter가 자동으로 최적 모드 선택 (향후)

---

#### B. `sync_repository` - 옵션 확장

**현재 MCP Input**:
```java
record SyncRepositoryInput(UUID repositoryId, String branch) {}
```

**REST API 지원 파라미터**:
- `branch` ✅
- `mode` ❌ (FULL_SCAN, INCREMENTAL)
- `targetCommitSha` ❌
- `enableEmbedding` ❌

**개선 필요**:
```java
record SyncRepositoryInput(
    UUID repositoryId,
    String branch,
    String mode,            // "FULL_SCAN" | "INCREMENTAL"
    Boolean enableEmbedding // true: 임베딩 생성, false: 스킵
) {}
```

---

## 3. 우선순위별 구현 계획

### 🔴 Priority 1: 높음 (AI Agent 핵심 기능)

| # | Tool | 설명 | 카테고리 | 예상 난이도 |
|---|------|------|---------|-----------|
| 1 | `get_document_links` | 문서의 Outgoing/Incoming 링크 조회 | READ | 낮음 |
| 2 | `analyze_document_impact` | 문서 수정 시 영향 분석 | READ | 낮음 |
| 3 | `list_commits` | 커밋 히스토리 조회 (페이지네이션) | READ | 낮음 |
| 4 | `get_unpushed_commits` | 푸시되지 않은 커밋 조회 | READ | 낮음 |
| 5 | `list_branches` | 브랜치 목록 조회 | READ | 낮음 |
| 6 | `get_current_branch` | 현재 브랜치 조회 | READ | 낮음 |
| 7 | `switch_branch` | 브랜치 전환 | WRITE | 낮음 |

### 🟡 Priority 2: 중간 (기능 완성도)

| # | Tool | 설명 | 카테고리 | 예상 난이도 |
|---|------|------|---------|-----------|
| 8 | `search_documents` 확장 | graph, auto 모드 추가 | READ | 중간 |
| 9 | `sync_repository` 확장 | mode, enableEmbedding 옵션 | WRITE | 낮음 |
| 10 | `create_branch` | 새 브랜치 생성 | WRITE | 낮음 |
| 11 | `get_broken_links` | 깨진 링크 조회 | READ | 낮음 |
| 12 | `get_commit_detail` | 특정 커밋의 변경 파일 목록 | READ | 낮음 |

### 🟢 Priority 3: 낮음 (선택적)

| # | Tool | 설명 | 카테고리 | 예상 난이도 |
|---|------|------|---------|-----------|
| 13 | `get_stats` | 대시보드 통계 | READ | 낮음 |
| 14 | `get_document_graph` | 문서 중심 그래프 시각화 데이터 | READ | 중간 |

---

## 4. 신규 MCP Tool 정의

### 4.1 McpTool Enum 추가

```java
// McpTool.java에 추가

// Priority 1 - READ
GET_DOCUMENT_LINKS("get_document_links",
    "Get outgoing and incoming links for a document",
    GetDocumentLinksInput.class, ToolCategory.READ),

ANALYZE_DOCUMENT_IMPACT("analyze_document_impact",
    "Analyze which documents would be affected if this document changes",
    AnalyzeDocumentImpactInput.class, ToolCategory.READ),

LIST_COMMITS("list_commits",
    "List commit history for a repository with pagination",
    ListCommitsInput.class, ToolCategory.READ),

GET_UNPUSHED_COMMITS("get_unpushed_commits",
    "Get commits that haven't been pushed to remote",
    GetUnpushedCommitsInput.class, ToolCategory.READ),

LIST_BRANCHES("list_branches",
    "List all branches in a repository",
    ListBranchesInput.class, ToolCategory.READ),

GET_CURRENT_BRANCH("get_current_branch",
    "Get the currently checked out branch",
    GetCurrentBranchInput.class, ToolCategory.READ),

// Priority 1 - WRITE
SWITCH_BRANCH("switch_branch",
    "Switch to a different branch",
    SwitchBranchInput.class, ToolCategory.WRITE),

// Priority 2 - READ
GET_BROKEN_LINKS("get_broken_links",
    "Find broken links in repository documents",
    GetBrokenLinksInput.class, ToolCategory.READ),

GET_COMMIT_DETAIL("get_commit_detail",
    "Get details of a specific commit including changed files",
    GetCommitDetailInput.class, ToolCategory.READ),

// Priority 2 - WRITE
CREATE_BRANCH("create_branch",
    "Create a new branch from current HEAD or specified commit",
    CreateBranchInput.class, ToolCategory.WRITE),
```

### 4.2 Input/Output Records

```java
// McpModels.java에 추가

// === GET_DOCUMENT_LINKS ===
public record GetDocumentLinksInput(
    @JsonProperty("documentId") UUID documentId
) {}

public record GetDocumentLinksResult(
    List<DocumentLink> outgoing,
    List<DocumentLink> incoming,
    int totalOutgoing,
    int totalIncoming
) {}

public record DocumentLink(
    UUID documentId,
    String path,
    String title,
    String linkText,
    boolean isBroken
) {}

// === ANALYZE_DOCUMENT_IMPACT ===
public record AnalyzeDocumentImpactInput(
    @JsonProperty("documentId") UUID documentId,
    @JsonProperty("depth") Integer depth  // default: 2
) {}

public record AnalyzeDocumentImpactResult(
    UUID documentId,
    String path,
    List<ImpactedDocument> directlyAffected,
    List<ImpactedDocument> indirectlyAffected,
    int totalAffected
) {}

public record ImpactedDocument(
    UUID documentId,
    String path,
    String title,
    int depth
) {}

// === LIST_COMMITS ===
public record ListCommitsInput(
    @JsonProperty("repositoryId") UUID repositoryId,
    @JsonProperty("branch") String branch,
    @JsonProperty("page") Integer page,    // default: 0
    @JsonProperty("size") Integer size     // default: 20, max: 100
) {}

public record ListCommitsResult(
    List<CommitSummary> commits,
    int page,
    int size,
    int totalPages,
    long totalElements
) {}

public record CommitSummary(
    String sha,
    String shortSha,
    String message,
    String authorName,
    String authorEmail,
    Instant committedAt
) {}

// === GET_UNPUSHED_COMMITS ===
public record GetUnpushedCommitsInput(
    @JsonProperty("repositoryId") UUID repositoryId,
    @JsonProperty("branch") String branch
) {}

public record GetUnpushedCommitsResult(
    List<CommitSummary> commits,
    int count
) {}

// === LIST_BRANCHES ===
public record ListBranchesInput(
    @JsonProperty("repositoryId") UUID repositoryId
) {}

public record ListBranchesResult(
    List<BranchInfo> branches,
    String currentBranch
) {}

public record BranchInfo(
    String name,
    boolean isRemote,
    boolean isCurrent
) {}

// === GET_CURRENT_BRANCH ===
public record GetCurrentBranchInput(
    @JsonProperty("repositoryId") UUID repositoryId
) {}

public record GetCurrentBranchResult(
    String branch,
    String latestCommitSha
) {}

// === SWITCH_BRANCH ===
public record SwitchBranchInput(
    @JsonProperty("repositoryId") UUID repositoryId,
    @JsonProperty("branch") String branch
) {}

public record SwitchBranchResult(
    String previousBranch,
    String currentBranch,
    boolean success,
    String message
) {}

// === CREATE_BRANCH ===
public record CreateBranchInput(
    @JsonProperty("repositoryId") UUID repositoryId,
    @JsonProperty("branchName") String branchName,
    @JsonProperty("startPoint") String startPoint  // optional: commit SHA or branch name
) {}

public record CreateBranchResult(
    String branchName,
    String startCommitSha,
    boolean success,
    String message
) {}

// === GET_BROKEN_LINKS ===
public record GetBrokenLinksInput(
    @JsonProperty("repositoryId") UUID repositoryId
) {}

public record GetBrokenLinksResult(
    List<BrokenLink> brokenLinks,
    int totalCount
) {}

public record BrokenLink(
    UUID sourceDocumentId,
    String sourcePath,
    String linkText,
    String targetPath,
    String reason  // "NOT_FOUND", "DELETED", etc.
) {}

// === GET_COMMIT_DETAIL ===
public record GetCommitDetailInput(
    @JsonProperty("repositoryId") UUID repositoryId,
    @JsonProperty("commitSha") String commitSha
) {}

public record GetCommitDetailResult(
    String sha,
    String message,
    String authorName,
    String authorEmail,
    Instant committedAt,
    List<ChangedFile> changedFiles
) {}

public record ChangedFile(
    String path,
    String changeType,  // "ADD", "MODIFY", "DELETE", "RENAME"
    boolean isDocument
) {}
```

---

## 5. 구현 체크리스트

### Phase 11-A: Priority 1 Tools (7개)

- [ ] `McpTool` enum에 7개 Tool 추가
- [ ] `McpModels.java`에 Input/Output records 추가
- [ ] `McpToolDispatcher`에 handler 등록
  - [ ] `handleGetDocumentLinks()`
  - [ ] `handleAnalyzeDocumentImpact()`
  - [ ] `handleListCommits()`
  - [ ] `handleGetUnpushedCommits()`
  - [ ] `handleListBranches()`
  - [ ] `handleGetCurrentBranch()`
  - [ ] `handleSwitchBranch()`
- [ ] `McpController`에 REST endpoint 추가 (optional)
- [ ] 테스트 작성

### Phase 11-B: Priority 2 Tools (5개)

- [ ] `search_documents` graph/auto 모드 추가
- [ ] `sync_repository` mode/enableEmbedding 파라미터 추가
- [ ] `create_branch` Tool 추가
- [ ] `get_broken_links` Tool 추가
- [ ] `get_commit_detail` Tool 추가
- [ ] 테스트 작성

### Phase 11-C: Priority 3 Tools (2개)

- [ ] `get_stats` Tool 추가
- [ ] `get_document_graph` Tool 추가
- [ ] 테스트 작성

---

## 6. 관련 파일

### 수정 대상

| 파일 | 변경 내용 |
|------|----------|
| `backend/src/main/java/com/docst/mcp/McpTool.java` | Enum에 신규 Tool 추가 |
| `backend/src/main/java/com/docst/mcp/McpModels.java` | Input/Output records 추가 |
| `backend/src/main/java/com/docst/mcp/McpToolDispatcher.java` | Handler 메서드 구현 |
| `backend/src/main/java/com/docst/mcp/McpController.java` | REST endpoint 추가 (optional) |

### 참조 대상 (기존 서비스 활용)

| 파일 | 활용 기능 |
|------|----------|
| `GraphService.java` | `analyzeImpact()` |
| `DocumentLinkService.java` | `getOutgoingLinks()`, `getIncomingLinks()`, `getBrokenLinks()` |
| `CommitService.java` | `listCommits()`, `listUnpushedCommits()`, `getChangedFiles()` |
| `GitService.java` | `listBranches()`, `createBranch()`, `switchBranch()`, `getCurrentBranch()` |
| `StatsService.java` | `countProjects()`, `countRepositories()`, `countDocuments()` |

---

## 7. 예상 일정

| Phase | 항목 | 예상 규모 |
|-------|------|----------|
| 11-A | Priority 1 Tools (7개) | ~300 LOC |
| 11-B | Priority 2 Tools (5개) | ~200 LOC |
| 11-C | Priority 3 Tools (2개) | ~100 LOC |
| **Total** | **14개 Tool 추가/확장** | **~600 LOC** |

---

## 8. 검증 방법

### 단위 테스트
- 각 Handler 메서드별 테스트
- Input validation 테스트
- 권한 체크 테스트

### 통합 테스트
- MCP JSON-RPC 프로토콜 테스트
- Claude Desktop/Claude Code 연동 테스트

### E2E 시나리오 테스트
1. "이 문서를 수정하면 영향받는 문서들 알려줘" → `analyze_document_impact`
2. "최근 커밋 이력 보여줘" → `list_commits`
3. "feature 브랜치 만들고 전환해줘" → `create_branch` + `switch_branch`
4. "푸시 안 된 커밋 있어?" → `get_unpushed_commits`