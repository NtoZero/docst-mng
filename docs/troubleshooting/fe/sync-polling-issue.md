# Sync Polling 및 SSE 중복 실행 문제

> 작성일: 2025-12-26

---

## 증상

1. **백엔드 쿼리 반복**: Sync 버튼 클릭 후 백엔드에서 `dm_sync_job` 테이블 조회 쿼리가 2초마다 계속 반복됨
2. **Sync 무한 실행**: 백엔드에서 정상 응답이 왔음에도 UI에서 Sync가 계속 동작하는 것처럼 보임

---

## 원인 분석

### 문제 1: SSE와 Polling 동시 실행

`RepositoryCard` 컴포넌트에서 두 가지 방식이 **동시에** 사용되고 있었음:

```typescript
// SSE 방식 (실시간)
const { startSync, ... } = useSync(repo.id, ...);

// Polling 방식 (2초 간격)
const { data: syncStatus } = useSyncStatus(repo.id);
```

`useSyncStatus` 훅의 `refetchInterval` 설정:

```typescript
// hooks/use-api.ts
export function useSyncStatus(repositoryId: string, enabled = true) {
  return useQuery({
    ...
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      if (status === 'RUNNING' || status === 'PENDING') {
        return 2000;  // 2초마다 polling
      }
      return false;
    },
  });
}
```

**결과**: Sync 시작 시 SSE로 실시간 이벤트를 받으면서 동시에 2초마다 REST API polling도 발생

### 문제 2: Stale Closure

`use-sync.ts`의 `onerror` 핸들러에서 클로저 문제:

```typescript
// 문제 코드
const startSync = useCallback(async () => {
  ...
  eventSource.onerror = () => {
    // syncEvent는 startSync 생성 시점의 값 (stale!)
    if (!syncEvent || (syncEvent.status !== 'SUCCEEDED' ...)) {
      setError('Connection to sync stream lost');
    }
  };
}, [repositoryId, cancelSync, syncEvent]);  // syncEvent가 deps에 있어도 클로저 내부는 stale
```

**결과**: SSE 연결이 정상 종료되어도 `onerror`가 호출될 때 `syncEvent`가 null로 인식되어 에러 처리됨

### 문제 3: 이미 완료된 Sync 재요청

Sync API가 이미 `SUCCEEDED` 상태를 반환해도 SSE 연결을 시도하여 불필요한 연결 발생

---

## 해결 방법

### 1. SSE 활성화 시 Polling 비활성화

```typescript
// app/projects/[projectId]/page.tsx

function RepositoryCard({ repo, projectId }) {
  const { startSync, isConnecting, isSyncing, ... } = useSync(repo.id, ...);

  // SSE 활성화 시 polling 비활성화
  const isSSEActive = isConnecting || isSyncing;
  const { data: syncStatus } = useSyncStatus(repo.id, !isSSEActive);
  //                                                  ↑ enabled 파라미터
  ...
}
```

### 2. useRef로 최신 상태 추적 (Stale Closure 해결)

```typescript
// hooks/use-sync.ts

export function useSync(repositoryId: string, options = {}) {
  const [syncEvent, setSyncEvent] = useState<SyncEvent | null>(null);

  // ref로 최신 값 추적
  const syncEventRef = useRef<SyncEvent | null>(null);
  const isCompletedRef = useRef(false);

  useEffect(() => {
    syncEventRef.current = syncEvent;
  }, [syncEvent]);

  const startSync = useCallback(async () => {
    isCompletedRef.current = false;

    eventSource.onmessage = (event) => {
      const data = JSON.parse(event.data);
      setSyncEvent(data);
      syncEventRef.current = data;  // ref도 업데이트

      if (data.status === 'SUCCEEDED' || data.status === 'FAILED') {
        isCompletedRef.current = true;  // 완료 플래그 설정
        ...
      }
    };

    eventSource.onerror = () => {
      // ref를 사용하여 최신 상태 확인
      const currentEvent = syncEventRef.current;
      if (!isCompletedRef.current &&
          (!currentEvent || (currentEvent.status !== 'SUCCEEDED' && currentEvent.status !== 'FAILED'))) {
        setError('Connection to sync stream lost');
      }
    };
  }, [repositoryId, cancelSync]);  // syncEvent 제거
}
```

### 3. 이미 완료된 Sync 처리

```typescript
// hooks/use-sync.ts

const startSync = useCallback(async () => {
  const job = await repositoriesApi.sync(repositoryId, { branch });

  // 이미 완료된 상태면 SSE 연결 불필요
  if (job.status === 'SUCCEEDED') {
    setIsConnecting(false);
    setSyncEvent({
      jobId: job.id,
      status: 'SUCCEEDED',
      message: 'Sync completed',
      progress: 100,
      totalDocs: 0,
      processedDocs: 0,
    });
    isCompletedRef.current = true;
    optionsRef.current.onComplete?.(job);
    return;  // SSE 연결 스킵
  }

  // PENDING/RUNNING인 경우에만 SSE 연결
  const eventSource = new EventSource(streamUrl);
  ...
}, [repositoryId, cancelSync]);
```

---

## 수정된 파일

| 파일 | 변경 내용 |
|------|----------|
| `hooks/use-sync.ts` | `syncEventRef`, `isCompletedRef` 추가, stale closure 해결, 완료 상태 처리 |
| `app/projects/[projectId]/page.tsx` | `useSyncStatus`에 `enabled` 파라미터 전달하여 SSE 활성화 시 polling 비활성화 |

---

## 관련 개념

### React Stale Closure

React 함수형 컴포넌트에서 useCallback/useEffect 내부의 클로저는 생성 시점의 state 값을 캡처함.
이후 state가 변경되어도 클로저 내부에서는 이전 값을 참조함.

**해결책**:
1. `useRef`로 최신 값을 별도로 추적
2. 의존성 배열에 해당 state 추가 (하지만 이벤트 핸들러에서는 효과 없음)
3. 함수형 업데이트 사용 (`setState(prev => ...)`)

### SSE vs Polling

| 방식 | 장점 | 단점 |
|-----|-----|-----|
| SSE | 실시간, 서버 리소스 효율적 | 연결 유지 필요, 브라우저 호환성 |
| Polling | 구현 간단, 안정적 | 불필요한 요청, 지연 발생 |

**권장**: SSE 사용 시 Polling은 fallback으로만 사용하거나 비활성화
