# Documents Page Push Button Implementation

> **구현일**: 2026-01-10
> **상태**: 완료
> **관련 계획**: [Phase 8 Push Button Plan](../../plan/phase8/documents-page-push-button.md)

---

## 개요

문서 목록 페이지(`/projects/{projectId}/repositories/{repoId}/documents`)에 Push 버튼을 추가하여 unpushed commits을 확인하고 한 번에 원격 저장소로 푸시할 수 있는 기능을 구현했습니다.

### 구현된 기능
- Unpushed commits 조회 API
- Push 버튼 (Badge로 커밋 개수 표시)
- Popover로 커밋 목록 표시
- 한 번에 모든 커밋 Push
- Push 성공 시 자동 캐시 갱신

---

## Backend 구현

### 1. GitCommitWalker.java

**파일**: `backend/src/main/java/com/docst/git/GitCommitWalker.java`

Unpushed commits 조회 메서드 추가 (line 81-133):

```java
/**
 * 원격에 푸시되지 않은 커밋 목록을 조회한다.
 * git log origin/{branch}..HEAD 와 동일한 동작을 수행한다.
 */
public List<CommitInfo> listUnpushedCommits(Git git, String branch) throws IOException {
    List<CommitInfo> commits = new ArrayList<>();
    Repository repo = git.getRepository();

    try (RevWalk revWalk = new RevWalk(repo)) {
        // 로컬 브랜치의 최신 커밋
        Ref localRef = repo.findRef("refs/heads/" + branch);
        if (localRef == null) {
            log.warn("Local branch not found: {}", branch);
            return commits;
        }

        // 원격 브랜치의 최신 커밋
        Ref remoteRef = repo.findRef("refs/remotes/origin/" + branch);
        if (remoteRef == null) {
            // 원격 브랜치가 없으면 모든 로컬 커밋이 unpushed
            log.info("Remote branch not found, all local commits are unpushed: {}", branch);
            RevCommit start = revWalk.parseCommit(localRef.getObjectId());
            revWalk.markStart(start);
            for (RevCommit commit : revWalk) {
                commits.add(toCommitInfo(commit));
            }
            return commits;
        }

        // origin/branch..HEAD 범위의 커밋 조회
        RevCommit localHead = revWalk.parseCommit(localRef.getObjectId());
        RevCommit remoteHead = revWalk.parseCommit(remoteRef.getObjectId());

        revWalk.markStart(localHead);
        revWalk.markUninteresting(remoteHead);

        for (RevCommit commit : revWalk) {
            commits.add(toCommitInfo(commit));
        }
    }

    log.info("Found {} unpushed commits on branch {}", commits.size(), branch);
    return commits;
}
```

### 2. CommitService.java

**파일**: `backend/src/main/java/com/docst/service/CommitService.java`

서비스 메서드 추가 (line 71-106):

```java
/**
 * 푸시되지 않은 커밋 목록을 조회한다.
 */
public List<GitCommitWalker.CommitInfo> listUnpushedCommits(UUID repositoryId, String branch) {
    Repository repo = repositoryRepository.findById(repositoryId)
            .orElseThrow(() -> new IllegalArgumentException("Repository not found"));

    String targetBranch = branch != null ? branch : repo.getDefaultBranch();

    try (Git git = gitService.cloneOrOpen(repo)) {
        // fetch로 원격 상태 최신화
        gitService.fetch(git, repo, targetBranch);
        return gitCommitWalker.listUnpushedCommits(git, targetBranch);
    } catch (Exception e) {
        throw new RuntimeException("Failed to list unpushed commits: " + e.getMessage(), e);
    }
}
```

### 3. ApiModels.java

**파일**: `backend/src/main/java/com/docst/api/ApiModels.java`

DTO 추가 (line 460-473):

```java
/**
 * 푸시되지 않은 커밋 목록 응답.
 */
public record UnpushedCommitsResponse(
    String branch,
    List<CommitResponse> commits,
    int totalCount,
    boolean hasPushableCommits
) {}
```

### 4. RepositoriesController.java

**파일**: `backend/src/main/java/com/docst/api/RepositoriesController.java`

API 엔드포인트 추가 (line 282-329):

```java
@Operation(summary = "Unpushed 커밋 조회")
@GetMapping("/repositories/{id}/commits/unpushed")
@RequireRepositoryAccess(role = ProjectRole.VIEWER, repositoryIdParam = "id")
public ResponseEntity<ApiModels.UnpushedCommitsResponse> getUnpushedCommits(
        @PathVariable UUID id,
        @RequestParam(required = false) String branch
) {
    Repository repo = repositoryService.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Repository not found"));

    String targetBranch = branch != null ? branch : repo.getDefaultBranch();
    List<GitCommitWalker.CommitInfo> commits = commitService.listUnpushedCommits(id, targetBranch);

    List<ApiModels.CommitResponse> commitResponses = commits.stream()
            .map(c -> new ApiModels.CommitResponse(
                    c.sha(), c.shortSha(), c.shortMessage(), c.fullMessage(),
                    c.authorName(), c.authorEmail(), c.committedAt(), 0
            ))
            .toList();

    return ResponseEntity.ok(new ApiModels.UnpushedCommitsResponse(
            targetBranch, commitResponses, commits.size(), !commits.isEmpty()
    ));
}
```

---

## Frontend 구현

### 1. Types

**파일**: `frontend/lib/types.ts`

```typescript
export interface UnpushedCommitsResponse {
  branch: string;
  commits: Commit[];
  totalCount: number;
  hasPushableCommits: boolean;
}
```

### 2. API Client

**파일**: `frontend/lib/api.ts`

```typescript
export const repositoriesApi = {
  // ... existing methods

  getUnpushedCommits: (id: string, branch?: string): Promise<UnpushedCommitsResponse> => {
    const params = branch ? `?branch=${encodeURIComponent(branch)}` : '';
    return request(`/api/repositories/${id}/commits/unpushed${params}`);
  },
};
```

### 3. React Query Hooks

**파일**: `frontend/hooks/use-api.ts`

Query Keys 추가:

```typescript
export const queryKeys = {
  repositories: {
    // ... existing keys
    unpushedCommits: (id: string, branch?: string) =>
      ['repositories', id, 'unpushed', branch] as const,
  },
};
```

Hooks 추가:

```typescript
// Unpushed commits 조회
export function useUnpushedCommits(repositoryId: string, branch?: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.repositories.unpushedCommits(repositoryId, branch),
    queryFn: () => repositoriesApi.getUnpushedCommits(repositoryId, branch),
    enabled: enabled && !!repositoryId,
    staleTime: 30 * 1000, // 30초
  });
}

// Push 성공 시 캐시 무효화
export function usePushRepository() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, branch }: { id: string; branch?: string }) =>
      repositoriesApi.push(id, branch),
    onSuccess: (_, { id }) => {
      queryClient.invalidateQueries({
        queryKey: ['repositories', id, 'unpushed'],
      });
    },
  });
}
```

### 4. Documents Page UI

**파일**: `frontend/app/[locale]/projects/[projectId]/repositories/[repoId]/documents/page.tsx`

Push 버튼 및 Popover UI:

```tsx
// Hooks
const { data: unpushedData, refetch: refetchUnpushed } = useUnpushedCommits(
  repoId, repo?.defaultBranch, !!repo
);
const pushMutation = usePushRepository();

// Handler
const handlePush = useCallback(async () => {
  if (!repo) return;
  try {
    const result = await pushMutation.mutateAsync({
      id: repoId,
      branch: repo.defaultBranch,
    });
    if (result.success) {
      toast.success(result.message);
      refetchUnpushed();
    } else {
      toast.error(result.message);
    }
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to push to remote');
  }
}, [repo, repoId, pushMutation, toast, refetchUnpushed]);

// UI
<Popover>
  <PopoverTrigger asChild>
    <Button
      variant="outline"
      disabled={pushMutation.isPending || !unpushedData?.hasPushableCommits}
    >
      <Upload className="mr-2 h-4 w-4" />
      Push
      {unpushedData?.totalCount > 0 && (
        <Badge variant="secondary" className="ml-2">
          {unpushedData.totalCount}
        </Badge>
      )}
      <ChevronDown className="ml-1 h-4 w-4" />
    </Button>
  </PopoverTrigger>
  <PopoverContent className="w-80" align="end">
    {/* Commit list and Push button */}
  </PopoverContent>
</Popover>
```

---

## UI 구조

```
┌─────────────────────────────────────────────────────────────┐
│  Header                                                      │
│  ┌─────────────────────────────────────────────────────────┤
│  │ [← Back]  Documents           [Push ▾ (3)]  [Content|Tree]│
│  │           repo/name                                       │
│  └─────────────────────────────────────────────────────────┘
│                                                              │
│  Push Button Popover:                                        │
│  ┌──────────────────────────────┐                           │
│  │ Unpushed Commits      main   │                           │
│  ├──────────────────────────────┤                           │
│  │ ○ abc1234                    │                           │
│  │   Update README.md           │                           │
│  │ ○ def5678                    │                           │
│  │   Add new feature            │                           │
│  │ ○ ghi9012                    │                           │
│  │   Fix bug                    │                           │
│  ├──────────────────────────────┤                           │
│  │ [    Push 3 commits    ]     │                           │
│  └──────────────────────────────┘                           │
│                                                              │
│  Document List / Tree...                                     │
└─────────────────────────────────────────────────────────────┘
```

---

## API Reference

### GET /api/repositories/{id}/commits/unpushed

Unpushed commits 목록을 조회합니다.

**Request**:
```
GET /api/repositories/e8955470-2ebd-4d5f-a250-3f51d775a5d4/commits/unpushed?branch=main
Authorization: Bearer <token>
```

**Response**:
```json
{
  "branch": "main",
  "commits": [
    {
      "sha": "abc1234567890...",
      "shortSha": "abc1234",
      "message": "Update README.md",
      "fullMessage": "Update README.md\n\nAdded installation instructions",
      "authorName": "John Doe",
      "authorEmail": "john@example.com",
      "committedAt": "2026-01-10T10:30:00Z",
      "changedFilesCount": 0
    }
  ],
  "totalCount": 1,
  "hasPushableCommits": true
}
```

**권한**: `VIEWER` 이상

---

## 빌드 및 테스트

### 빌드 결과

```bash
# Frontend
cd frontend && npm run build
# ✓ Compiled successfully
# ✓ Generating static pages (28/28)
```

### 테스트 시나리오

1. **Unpushed commits 없는 경우**
   - Push 버튼 비활성화 상태
   - Popover에 "No unpushed commits" 메시지

2. **Unpushed commits 있는 경우**
   - Badge에 커밋 개수 표시
   - Popover에 커밋 목록 (최대 5개)
   - Push 버튼으로 전체 푸시

3. **Push 성공**
   - 성공 토스트 메시지
   - Badge 자동 갱신 (0)
   - 원격 저장소 반영 확인

4. **Push 실패**
   - 에러 토스트 메시지
   - 커밋 목록 유지

---

## 파일 목록

### Backend (4 files modified)

| File | Changes |
|------|---------|
| `git/GitCommitWalker.java` | `listUnpushedCommits()` 메서드 추가 |
| `service/CommitService.java` | `listUnpushedCommits()` 서비스 메서드 추가 |
| `api/ApiModels.java` | `UnpushedCommitsResponse` DTO 추가 |
| `api/RepositoriesController.java` | GET `/commits/unpushed` 엔드포인트 추가 |

### Frontend (4 files modified)

| File | Changes |
|------|---------|
| `lib/types.ts` | `UnpushedCommitsResponse` 타입 추가 |
| `lib/api.ts` | `getUnpushedCommits()` 함수 추가 |
| `hooks/use-api.ts` | `useUnpushedCommits` 훅 추가, `usePushRepository` 개선 |
| `app/.../documents/page.tsx` | Push 버튼 및 Popover UI 추가 |

---

## 주의사항

### 재임베딩 관련
- **재임베딩 불필요**: 임베딩은 문서 Commit 시점에 `DocumentService.upsertDocument()`에서 이미 처리됨
- Push는 단순히 로컬 커밋을 원격으로 전송하는 역할만 수행
- 변경되지 않은 문서는 영향 없음

### 기술적 주의사항
1. **Fetch 필수**: unpushed commits 조회 전 `gitService.fetch()` 호출로 원격 상태 최신화 필요
2. **원격 브랜치 미존재 처리**: 첫 푸시 시 원격 브랜치가 없으면 모든 로컬 커밋이 unpushed로 표시
3. **기존 Push 로직 재사용**: `GitWriteService.pushToRemote()` 그대로 사용
