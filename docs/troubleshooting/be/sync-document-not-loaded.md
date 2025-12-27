# Sync 완료 후 Document가 로드되지 않는 문제

## 증상

1. Sync 버튼 클릭 후 동기화가 완료됨 (SUCCEEDED 상태)
2. 하지만 Documents 목록에서 문서가 보이지 않음
3. 새로고침해도 문서가 나타나지 않음

## 원인 분석

### 1. SSE 스트림이 실제 진행 상황을 제공하지 않음

**백엔드 (`SyncController.java:70-105`)**:
```java
@GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter stream(@PathVariable UUID repoId) {
    // 1초마다 status만 polling해서 전송
    scheduler.scheduleAtFixedRate(() -> {
        syncService.findLatestByRepositoryId(repoId).ifPresent(job -> {
            emitter.send(Map.of(
                "jobId", job.getId(),
                "status", job.getStatus().name(),
                "message", getStatusMessage(job)
                // ❌ progress, processedDocs, totalDocs 없음
            ));
        });
    }, 0, 1, TimeUnit.SECONDS);
}
```

**프론트엔드 (`use-sync.ts:82-86`)** 가 기대하는 데이터:
```typescript
const syncProgress = syncEvent?.progress ?? 0;      // ❌ 백엔드가 안 보냄
const processedDocs = syncEvent?.processedDocs ?? 0; // ❌ 백엔드가 안 보냄
const totalDocs = syncEvent?.totalDocs ?? 0;         // ❌ 백엔드가 안 보냄
```

### 2. SSE가 아닌 Polling-over-SSE 구조

현재 구조:
```
[Backend SSE]                    [Frontend]
     │                                │
     │ ←── 1초마다 DB polling ───     │
     │                                │
     ├── status 이벤트 전송 ──────────→│
     │                                │
                                      │ ←── enabled=false
                                      │     (SSE 활성 시 polling 비활성화)
```

문제점:
- 백엔드: SSE라고 하지만 내부적으로 1초마다 DB polling
- 프론트엔드: SSE 비활성 시 2초마다 API polling
- 결국 둘 다 polling 방식으로 비효율적

### 3. 트랜잭션 타이밍 문제

`SyncService.executeSync()` 흐름:

```java
private void executeSync(UUID jobId) {
    SyncJob job = syncJobRepository.findByIdWithRepository(jobId).orElse(null);

    try {
        job.start();
        syncJobRepository.save(job);  // ① RUNNING 저장 (별도 트랜잭션)

        // ② 문서 동기화 (GitSyncService의 @Transactional 내에서 처리)
        String lastCommit = gitSyncService.syncRepository(repo.getId(), ...);

        job.complete(lastCommit);
        syncJobRepository.save(job);  // ③ SUCCEEDED 저장 (별도 트랜잭션)
    } catch (Exception e) {
        job.fail(e.getMessage());
        syncJobRepository.save(job);  // FAILED 저장
    }
}
```

**잠재적 문제**:
- `executeSync`는 `@Transactional`이 아님
- 각 `save()` 호출이 개별 트랜잭션으로 실행됨
- SSE polling이 ②와 ③ 사이에서 status를 조회하면, 문서는 커밋됐지만 status는 아직 RUNNING일 수 있음
- 반대로 ③ 직후에 조회해도 DB 복제 지연이 있을 수 있음

### 4. Query Invalidation 타이밍

`[projectId]/page.tsx:48-52`:
```typescript
const handleSyncComplete = useCallback(() => {
  void queryClient.invalidateQueries({ queryKey: queryKeys.repositories.syncStatus(repo.id) });
  void queryClient.invalidateQueries({ queryKey: queryKeys.documents.byRepository(repo.id) });
}, [queryClient, repo.id]);
```

`use-sync.ts:119-123`:
```typescript
if (status === 'SUCCEEDED') {
  repositoriesApi.getSyncStatus(repositoryId).then((finalJob) => {
    optionsRef.current.onComplete?.(finalJob);  // ← 이때 handleSyncComplete 호출
  });
}
```

**문제**:
- SSE에서 "SUCCEEDED" 이벤트를 받으면 `onComplete` 호출
- 하지만 이 시점에 백엔드의 문서 저장 트랜잭션이 완전히 커밋되었는지 보장 없음

## 실제 문서가 저장되지 않는 경우

백엔드 로그에서 다음을 확인해야 함:

1. **GitFileScanner가 문서를 찾지 못함**:
   ```
   INFO  - Found 0 document files
   ```

2. **Git clone/fetch 실패**:
   ```
   ERROR - Failed to sync repository: owner/name
   ```

3. **문서 파싱 실패**:
   ```
   ERROR - Failed to process document: path/to/file.md
   ```

## 해결 방안 (적용 완료)

### 1. SyncProgressTracker 서비스 추가

메모리 기반 진행 상황 추적기:

```java
// backend/src/main/java/com/docst/service/SyncProgressTracker.java
@Component
public class SyncProgressTracker {
    private final Map<UUID, Progress> progressMap = new ConcurrentHashMap<>();

    public void start(UUID jobId, UUID repositoryId) { ... }
    public void setTotal(UUID jobId, int total) { ... }
    public void update(UUID jobId, int processed, String currentFile) { ... }
    public Progress getProgress(UUID jobId) { ... }
}
```

### 2. GitSyncService에서 진행 상황 업데이트

```java
// 문서 처리 루프에서 진행 상황 업데이트
for (int i = 0; i < documentPaths.size(); i++) {
    String path = documentPaths.get(i);
    processDocument(git, repo, path, latestCommit, commitInfo);
    progressTracker.update(jobId, i + 1, path);
}
```

### 3. SSE 스트림 개선

```java
// SyncController.java - 진행 상황 포함 + scheduler 누수 수정
Map<String, Object> data = new HashMap<>();
data.put("status", job.getStatus().name());
data.put("totalDocs", progress.getTotalDocs());
data.put("processedDocs", progress.getProcessedDocs());
data.put("progress", progress.getProgressPercent());
data.put("message", progress.getMessage());

// Scheduler 누수 방지
emitter.onCompletion(() -> scheduledTask.cancel(true));
emitter.onTimeout(() -> scheduledTask.cancel(true));
emitter.onError(e -> scheduledTask.cancel(true));
```

### 4. Frontend 타이밍 문제 해결

```typescript
// use-sync.ts - Sync 완료 후 지연을 두고 콜백 호출
if (status === 'SUCCEEDED') {
  setTimeout(() => {
    repositoriesApi.getSyncStatus(repositoryId).then((finalJob) => {
      optionsRef.current.onComplete?.(finalJob);
    });
  }, 300);
}
```

## 관련 파일

### Backend
- `backend/src/main/java/com/docst/service/SyncProgressTracker.java` (신규)
- `backend/src/main/java/com/docst/service/SyncService.java`
- `backend/src/main/java/com/docst/service/GitSyncService.java`
- `backend/src/main/java/com/docst/api/SyncController.java`

### Frontend
- `frontend/app/projects/[projectId]/page.tsx`
- `frontend/hooks/use-sync.ts`
- `frontend/hooks/use-api.ts`
