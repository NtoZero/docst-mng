# SSE/Polling 비효율성 문제

## 현황

### Backend: Polling-over-SSE

`SyncController.java:70-105`:
```java
@GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter stream(@PathVariable UUID repoId) {
    SseEmitter emitter = new SseEmitter(60000L);

    // ❌ SSE라고 하지만 실제로는 1초마다 DB polling
    scheduler.scheduleAtFixedRate(() -> {
        syncService.findLatestByRepositoryId(repoId).ifPresent(job -> {
            emitter.send(...);
        });
    }, 0, 1, TimeUnit.SECONDS);

    return emitter;
}
```

### Frontend: 이중 Polling 구조

`use-api.ts:215-228`:
```typescript
export function useSyncStatus(repositoryId: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.repositories.syncStatus(repositoryId),
    queryFn: () => repositoriesApi.getSyncStatus(repositoryId),
    enabled: enabled && !!repositoryId,
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      // ❌ RUNNING/PENDING 상태면 2초마다 polling
      if (status === 'RUNNING' || status === 'PENDING') {
        return 2000;
      }
      return false;
    },
  });
}
```

`[projectId]/page.tsx:67-69`:
```typescript
// SSE 활성화 시 polling 비활성화 시도
const isSSEActive = isConnecting || isSyncing;
const { data: syncStatus } = useSyncStatus(repo.id, !isSSEActive);
```

## 문제점

### 1. 리소스 낭비

```
┌─────────────────────────────────────────────────────────────┐
│  동기화 중 (예: 30초 소요)                                    │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Backend SSE Scheduler:                                     │
│    ├── 1초: SELECT * FROM sync_job WHERE repo_id = ?       │
│    ├── 2초: SELECT * FROM sync_job WHERE repo_id = ?       │
│    ├── 3초: SELECT * FROM sync_job WHERE repo_id = ?       │
│    └── ... (30회 DB 쿼리)                                   │
│                                                             │
│  Frontend (SSE 연결 실패 시):                                │
│    ├── 2초: GET /api/repositories/{id}/sync/status          │
│    ├── 4초: GET /api/repositories/{id}/sync/status          │
│    └── ... (15회 API 호출)                                   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 2. 실시간성 부재

- SSE를 사용하면서도 1초 간격 polling → 실시간 아님
- 문서 처리 진행률을 제공하지 않음
- 사용자 경험: "지금 뭘 하고 있는지 모름"

### 3. SSE 연결 관리 부재

```java
// ❌ 현재: scheduler가 종료되지 않음
scheduler.scheduleAtFixedRate(() -> { ... }, 0, 1, TimeUnit.SECONDS);

emitter.onCompletion(() -> {});  // scheduler 취소 안 함
emitter.onTimeout(emitter::complete);  // scheduler 취소 안 함
```

## 해결 방안

### 방안 1: 진정한 Push 방식 SSE

```java
@Service
public class SyncProgressEmitter {
    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public void addEmitter(UUID repoId, SseEmitter emitter) {
        emitters.computeIfAbsent(repoId, k -> new CopyOnWriteArrayList<>()).add(emitter);
    }

    public void emit(UUID repoId, SyncProgressEvent event) {
        List<SseEmitter> repoEmitters = emitters.get(repoId);
        if (repoEmitters != null) {
            for (SseEmitter emitter : repoEmitters) {
                try {
                    emitter.send(event);
                } catch (IOException e) {
                    repoEmitters.remove(emitter);
                }
            }
        }
    }
}
```

```java
// GitSyncService에서 진행 상황 emit
for (int i = 0; i < documentPaths.size(); i++) {
    processDocument(...);
    progressEmitter.emit(repoId, new SyncProgressEvent(
        jobId, "RUNNING", i + 1, documentPaths.size()
    ));
}
```

### 방안 2: Polling 최적화

SSE 대신 smart polling:

```typescript
// Frontend
export function useSyncStatus(repositoryId: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.repositories.syncStatus(repositoryId),
    queryFn: () => repositoriesApi.getSyncStatus(repositoryId),
    enabled: enabled && !!repositoryId,
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      if (status === 'RUNNING') return 1000;   // 실행 중: 1초
      if (status === 'PENDING') return 3000;   // 대기 중: 3초
      return false;  // 완료/실패: polling 중지
    },
    staleTime: 500,  // 0.5초간 캐시
  });
}
```

### 방안 3: WebSocket 사용

복잡한 실시간 통신이 필요하다면:

```typescript
// Frontend
const ws = new WebSocket(`ws://localhost:8080/ws/sync/${repoId}`);
ws.onmessage = (event) => {
  const progress = JSON.parse(event.data);
  setSyncProgress(progress);
};
```

## 권장 사항

| 방안 | 복잡도 | 실시간성 | 리소스 효율 |
|------|--------|----------|-------------|
| 현재 (Polling-over-SSE) | 낮음 | 낮음 | 낮음 |
| 방안 1: 진정한 SSE | 중간 | 높음 | 높음 |
| 방안 2: Smart Polling | 낮음 | 중간 | 중간 |
| 방안 3: WebSocket | 높음 | 매우 높음 | 높음 |

**MVP 단계**: 방안 2 (Smart Polling) 권장
**확장 단계**: 방안 1 (진정한 SSE Push) 권장

## 관련 파일

- `backend/src/main/java/com/docst/api/SyncController.java`
- `frontend/hooks/use-sync.ts`
- `frontend/hooks/use-api.ts`
