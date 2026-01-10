# 문서 목록 페이지 Push 버튼 추가 구현 계획

## 개요

문서 목록 페이지(`/projects/{projectId}/repositories/{repoId}/documents`)에서 Push 버튼을 추가하여 unpushed commits을 확인하고 한 번에 원격 저장소로 푸시할 수 있는 기능을 구현합니다.

### 현재 상태
- **문서 상세 페이지** (`/documents/[docId]`): Git commit + push 기능 존재
- **문서 목록 페이지** (`/projects/.../documents`): push 기능 없음

### 목표
- 문서 목록 페이지에 Push 버튼 추가
- Unpushed commits 목록 조회 및 표시
- 한 번에 모든 unpushed commits 푸시
- 기존 push 로직 재사용

---

## 구현 계획

### Phase 1: 백엔드 Git 레이어

**파일**: `backend/src/main/java/com/docst/git/GitCommitWalker.java`

```java
/**
 * 원격에 푸시되지 않은 커밋 목록을 조회한다.
 * git log origin/{branch}..HEAD 와 동일한 동작
 */
public List<CommitInfo> listUnpushedCommits(Git git, String branch) throws IOException {
    List<CommitInfo> commits = new ArrayList<>();
    Repository repo = git.getRepository();

    try (RevWalk revWalk = new RevWalk(repo)) {
        Ref localRef = repo.findRef("refs/heads/" + branch);
        if (localRef == null) {
            return commits;
        }

        Ref remoteRef = repo.findRef("refs/remotes/origin/" + branch);
        if (remoteRef == null) {
            // 원격 브랜치 없음 → 모든 로컬 커밋이 unpushed
            RevCommit start = revWalk.parseCommit(localRef.getObjectId());
            revWalk.markStart(start);
            for (RevCommit commit : revWalk) {
                commits.add(toCommitInfo(commit));
            }
            return commits;
        }

        // origin/branch..HEAD 범위 조회
        RevCommit localHead = revWalk.parseCommit(localRef.getObjectId());
        RevCommit remoteHead = revWalk.parseCommit(remoteRef.getObjectId());
        revWalk.markStart(localHead);
        revWalk.markUninteresting(remoteHead);

        for (RevCommit commit : revWalk) {
            commits.add(toCommitInfo(commit));
        }
    }
    return commits;
}
```

---

### Phase 2: 백엔드 서비스 레이어

**파일**: `backend/src/main/java/com/docst/service/CommitService.java`

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

---

### Phase 3: 백엔드 API 레이어

**파일**: `backend/src/main/java/com/docst/api/ApiModels.java`

```java
/**
 * Unpushed commits 응답 DTO
 */
public record UnpushedCommitsResponse(
    String branch,
    List<CommitResponse> commits,
    int totalCount,
    boolean hasPushableCommits
) {}
```

**파일**: `backend/src/main/java/com/docst/api/RepositoriesController.java`

```java
/**
 * 푸시되지 않은 커밋 목록 조회
 * GET /api/repositories/{id}/commits/unpushed
 */
@GetMapping("/repositories/{id}/commits/unpushed")
@RequireRepositoryAccess(role = ProjectRole.VIEWER, repositoryIdParam = "id")
public ResponseEntity<UnpushedCommitsResponse> getUnpushedCommits(
        @PathVariable UUID id,
        @RequestParam(required = false) String branch
) {
    Repository repo = repositoryService.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Repository not found"));

    String targetBranch = branch != null ? branch : repo.getDefaultBranch();
    List<GitCommitWalker.CommitInfo> commits = commitService.listUnpushedCommits(id, targetBranch);

    List<CommitResponse> commitResponses = commits.stream()
            .map(c -> new CommitResponse(c.sha(), c.shortSha(), c.shortMessage(),
                    c.fullMessage(), c.authorName(), c.authorEmail(), c.committedAt(), 0))
            .toList();

    return ResponseEntity.ok(new UnpushedCommitsResponse(
            targetBranch, commitResponses, commits.size(), !commits.isEmpty()
    ));
}
```

---

### Phase 4: 프론트엔드 타입/API

**파일**: `frontend/lib/types.ts`

```typescript
export interface UnpushedCommitsResponse {
  branch: string;
  commits: Commit[];
  totalCount: number;
  hasPushableCommits: boolean;
}
```

**파일**: `frontend/lib/api.ts`

```typescript
export const repositoriesApi = {
  // ... 기존 메서드들 ...

  getUnpushedCommits: (id: string, branch?: string): Promise<UnpushedCommitsResponse> => {
    const params = branch ? `?branch=${encodeURIComponent(branch)}` : '';
    return request(`/api/repositories/${id}/commits/unpushed${params}`);
  },
};
```

---

### Phase 5: 프론트엔드 훅

**파일**: `frontend/hooks/use-api.ts`

```typescript
// Query Keys 추가
export const queryKeys = {
  repositories: {
    // ... 기존 키들 ...
    unpushedCommits: (id: string, branch?: string) =>
      ['repositories', id, 'unpushed', branch] as const,
  },
};

// Unpushed commits 조회 훅
export function useUnpushedCommits(repositoryId: string, branch?: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.repositories.unpushedCommits(repositoryId, branch),
    queryFn: () => repositoriesApi.getUnpushedCommits(repositoryId, branch),
    enabled: enabled && !!repositoryId,
    staleTime: 30 * 1000, // 30초
  });
}

// usePushRepository 개선 - 성공 시 캐시 무효화
export function usePushRepository() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, branch }: { id: string; branch?: string }) =>
      repositoriesApi.push(id, branch),
    onSuccess: (_, { id }) => {
      queryClient.invalidateQueries({
        queryKey: ['repositories', id, 'unpushed']
      });
    },
  });
}
```

---

### Phase 6: 프론트엔드 UI

**파일**: `frontend/app/[locale]/projects/[projectId]/repositories/[repoId]/documents/page.tsx`

```tsx
// 추가 import
import { Upload, GitCommit, ChevronDown, Loader2 } from 'lucide-react';
import { useUnpushedCommits, usePushRepository } from '@/hooks/use-api';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { Badge } from '@/components/ui/badge';

// 컴포넌트 내부
const { data: unpushedData } = useUnpushedCommits(repoId, repo?.defaultBranch);
const pushMutation = usePushRepository();

const handlePush = async () => {
  if (!repo) return;
  try {
    const result = await pushMutation.mutateAsync({ id: repoId, branch: repo.defaultBranch });
    if (result.success) {
      toast.success(result.message);
    } else {
      toast.error(result.message);
    }
  } catch (err) {
    toast.error('Failed to push to remote');
  }
};

// Header 영역에 Push 버튼 추가
<Popover>
  <PopoverTrigger asChild>
    <Button
      variant="outline"
      disabled={pushMutation.isPending || !unpushedData?.hasPushableCommits}
    >
      {pushMutation.isPending ? (
        <Loader2 className="mr-2 h-4 w-4 animate-spin" />
      ) : (
        <Upload className="mr-2 h-4 w-4" />
      )}
      Push
      {unpushedData && unpushedData.totalCount > 0 && (
        <Badge variant="secondary" className="ml-2">{unpushedData.totalCount}</Badge>
      )}
      <ChevronDown className="ml-1 h-4 w-4" />
    </Button>
  </PopoverTrigger>
  <PopoverContent className="w-80" align="end">
    {/* Unpushed commits 목록 및 Push 버튼 */}
  </PopoverContent>
</Popover>
```

---

## 수정 파일 요약

| 레이어 | 파일 | 변경 내용 |
|--------|------|----------|
| Backend Git | `GitCommitWalker.java` | `listUnpushedCommits()` 메서드 추가 |
| Backend Service | `CommitService.java` | `listUnpushedCommits()` 서비스 메서드 추가 |
| Backend API | `RepositoriesController.java` | `GET /commits/unpushed` 엔드포인트 추가 |
| Backend DTO | `ApiModels.java` | `UnpushedCommitsResponse` record 추가 |
| Frontend Type | `types.ts` | `UnpushedCommitsResponse` 인터페이스 추가 |
| Frontend API | `api.ts` | `getUnpushedCommits()` 함수 추가 |
| Frontend Hook | `use-api.ts` | `useUnpushedCommits` 훅 추가, `usePushRepository` 개선 |
| Frontend UI | `documents/page.tsx` | Push 버튼 및 Popover UI 추가 |

---

## 주의사항

### 재임베딩 관련
- **재임베딩 불필요**: 임베딩은 문서 편집 → Commit 시점에 `DocumentService.upsertDocument()`에서 이미 처리됨
- Push는 단순히 로컬 커밋을 원격으로 전송하는 역할만 수행
- 변경되지 않은 문서는 영향 없음

### 기술적 주의사항
1. **Fetch 필수**: unpushed commits 조회 전 `gitService.fetch()` 호출로 원격 상태 최신화 필요
2. **원격 브랜치 미존재 처리**: 첫 푸시 시 원격 브랜치가 없으면 모든 로컬 커밋이 unpushed로 표시
3. **기존 Push 로직 재사용**: `GitWriteService.pushToRemote()` 그대로 사용

---

## 검증 시나리오

1. **Unpushed commits 없는 경우**
   - Push 버튼 비활성화
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
