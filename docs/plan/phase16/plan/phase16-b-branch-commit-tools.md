# Phase 16-B: 브랜치 및 커밋 도구

## 개요

Git 브랜치 관리와 커밋 이력 조회 기능을 MCP 도구로 제공한다. AI 에이전트가 브랜치 기반 작업 흐름을 수행하고 변경 이력을 추적할 수 있게 한다.

## 새 클래스: McpBranchTools.java

### 파일 위치
```
backend/src/main/java/com/docst/mcp/tools/McpBranchTools.java
```

### 의존성
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class McpBranchTools {
    private final BranchService branchService;
}
```

---

## 브랜치 도구 명세

### 1. list_branches

레포지토리의 모든 브랜치를 조회한다.

```java
@Tool(name = "list_branches",
      description = "List all branches in a repository. " +
                    "Returns branch names and indicates which is currently checked out.")
public ListBranchesResult listBranches(
    @ToolParam(description = "Repository ID (UUID format)")
    String repositoryId
)
```

**반환 타입:**
```java
public record ListBranchesResult(
    UUID repositoryId,
    String currentBranch,
    List<String> branches
) {}
```

---

### 2. get_current_branch

현재 체크아웃된 브랜치를 조회한다.

```java
@Tool(name = "get_current_branch",
      description = "Get the currently checked-out branch for a repository.")
public GetCurrentBranchResult getCurrentBranch(
    @ToolParam(description = "Repository ID (UUID format)")
    String repositoryId
)
```

**반환 타입:**
```java
public record GetCurrentBranchResult(
    UUID repositoryId,
    String branch
) {}
```

---

### 3. create_branch

새 브랜치를 생성한다. 기존 브랜치에서 분기한다.

```java
@Tool(name = "create_branch",
      description = "Create a new branch in a repository. " +
                    "The new branch will be created from the specified source branch.")
public CreateBranchResult createBranch(
    @ToolParam(description = "Repository ID (UUID format)")
    String repositoryId,

    @ToolParam(description = "Name for the new branch")
    String branchName,

    @ToolParam(description = "Source branch to create from (default: main)", required = false)
    String fromBranch
)
```

**반환 타입:**
```java
public record CreateBranchResult(
    UUID repositoryId,
    String branchName,
    String fromBranch,
    boolean success,
    String message
) {}
```

---

### 4. switch_branch

다른 브랜치로 전환한다. 이후 문서 조회/수정은 해당 브랜치 기준으로 수행된다.

```java
@Tool(name = "switch_branch",
      description = "Switch to a different branch in a repository. " +
                    "This changes which version of documents are visible.")
public SwitchBranchResult switchBranch(
    @ToolParam(description = "Repository ID (UUID format)")
    String repositoryId,

    @ToolParam(description = "Branch name to switch to")
    String branchName
)
```

**반환 타입:**
```java
public record SwitchBranchResult(
    UUID repositoryId,
    String branch,
    boolean success,
    String message
) {}
```

---

## 새 클래스: McpCommitTools.java

### 파일 위치
```
backend/src/main/java/com/docst/mcp/tools/McpCommitTools.java
```

### 의존성
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class McpCommitTools {
    private final CommitService commitService;
    private final GitService gitService;
}
```

---

## 커밋 도구 명세

### 5. list_commits

커밋 이력을 페이지네이션으로 조회한다.

```java
@Tool(name = "list_commits",
      description = "List commits in a repository with pagination. " +
                    "Returns commit history for the specified branch.")
public ListCommitsResult listCommits(
    @ToolParam(description = "Repository ID (UUID format)")
    String repositoryId,

    @ToolParam(description = "Branch name (default: main)", required = false)
    String branch,

    @ToolParam(description = "Page number, 0-indexed (default: 0)", required = false)
    Integer page,

    @ToolParam(description = "Page size (default: 20, max: 100)", required = false)
    Integer size
)
```

**반환 타입:**
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
```

---

### 6. get_commit_changes

특정 커밋에서 변경된 파일 목록을 조회한다.

```java
@Tool(name = "get_commit_changes",
      description = "Get the files changed in a specific commit. " +
                    "Shows which files were added, modified, deleted, or renamed.")
public GetCommitChangesResult getCommitChanges(
    @ToolParam(description = "Repository ID (UUID format)")
    String repositoryId,

    @ToolParam(description = "Commit SHA (full or short)")
    String commitSha
)
```

**반환 타입:**
```java
public record GetCommitChangesResult(
    UUID repositoryId,
    String commitSha,
    List<ChangedFileInfo> changedFiles
) {}

public record ChangedFileInfo(
    String path,
    String changeType,  // ADD, MODIFY, DELETE, RENAME, COPY
    String oldPath      // only for RENAME/COPY
) {}
```

---

### 7. diff_commits

두 커밋 사이에서 변경된 파일 목록을 조회한다.

```java
@Tool(name = "diff_commits",
      description = "Get files changed between two commits. " +
                    "Useful for understanding what changed over a range of commits.")
public DiffCommitsResult diffCommits(
    @ToolParam(description = "Repository ID (UUID format)")
    String repositoryId,

    @ToolParam(description = "Starting commit SHA")
    String fromSha,

    @ToolParam(description = "Ending commit SHA")
    String toSha
)
```

**반환 타입:**
```java
public record DiffCommitsResult(
    UUID repositoryId,
    String fromSha,
    String toSha,
    List<ChangedFileInfo> changedFiles
) {}
```

---

### 8. list_unpushed_commits

아직 원격에 푸시되지 않은 로컬 커밋을 조회한다.

```java
@Tool(name = "list_unpushed_commits",
      description = "List commits that haven't been pushed to remote yet. " +
                    "Useful to check what local changes are pending before push_to_remote.")
public ListUnpushedCommitsResult listUnpushedCommits(
    @ToolParam(description = "Repository ID (UUID format)")
    String repositoryId,

    @ToolParam(description = "Branch name (default: main)", required = false)
    String branch
)
```

**반환 타입:**
```java
public record ListUnpushedCommitsResult(
    UUID repositoryId,
    String branch,
    int count,
    List<CommitSummary> commits
) {}
```

---

## 구현 체크리스트

### McpBranchTools.java
- [ ] 클래스 생성
- [ ] list_branches 구현
- [ ] get_current_branch 구현
- [ ] create_branch 구현
- [ ] switch_branch 구현

### McpCommitTools.java
- [ ] 클래스 생성
- [ ] list_commits 구현
- [ ] get_commit_changes 구현
- [ ] diff_commits 구현
- [ ] list_unpushed_commits 구현

### 공통
- [ ] McpModels.java에 Result 레코드 추가
- [ ] McpServerConfig.java에 도구 클래스 등록
- [ ] 단위 테스트 작성
- [ ] 통합 테스트 작성

---

## 복잡도 평가

| 도구 | 복잡도 | 비고 |
|------|--------|------|
| list_branches | LOW | BranchService.listBranches() 직접 사용 |
| get_current_branch | LOW | BranchService.getCurrentBranch() 직접 사용 |
| create_branch | LOW | BranchService.createBranch() 직접 사용 |
| switch_branch | LOW | BranchService.switchBranch() 직접 사용 |
| list_commits | MEDIUM | 페이지네이션 처리 필요 |
| get_commit_changes | LOW | CommitService.getChangedFiles() 직접 사용 |
| diff_commits | LOW | CommitService.getChangedFilesBetween() 직접 사용 |
| list_unpushed_commits | LOW | GitService.hasUnpushedCommits() 활용 |

---

## 사용 시나리오

### 1. 브랜치 기반 문서 작업

```
1. list_branches로 현재 브랜치 목록 확인
2. create_branch로 feature/docs-update 브랜치 생성
3. switch_branch로 새 브랜치로 전환
4. update_document로 문서 수정
5. push_to_remote로 원격에 푸시
```

### 2. 변경 이력 추적

```
1. list_commits로 최근 커밋 이력 조회
2. get_commit_changes로 특정 커밋의 변경 파일 확인
3. diff_document로 해당 파일의 상세 변경 내용 확인
```

### 3. 푸시 전 검토

```
1. list_unpushed_commits로 미푸시 커밋 확인
2. 각 커밋에 대해 get_commit_changes로 변경 내용 확인
3. 확인 후 push_to_remote 실행
```