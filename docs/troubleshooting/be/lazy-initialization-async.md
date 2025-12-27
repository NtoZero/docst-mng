# LazyInitializationException in Async Context

## 증상

`SyncService.startSync()` 호출 시 비동기 스레드에서 다음 예외 발생:

```
org.hibernate.LazyInitializationException: Could not initialize proxy
[com.docst.domain.Repository#6bd84651-9fe2-44c5-87b3-92932cd9cd64] - no session
    at com.docst.domain.Repository$HibernateProxy.getFullName(Unknown Source)
    at com.docst.service.SyncService.executeSync(SyncService.java:99)
    at com.docst.service.SyncService.lambda$startSync$1(SyncService.java:76)
    at java.base/java.util.concurrent.CompletableFuture...
```

## 원인

`CompletableFuture.runAsync()`로 실행되는 비동기 스레드는 원래 트랜잭션/Hibernate 세션 컨텍스트 밖에서 실행됩니다.

### 문제 흐름

```
startSync() [@Transactional - 세션 존재]
    │
    ├── SyncJob 저장 (Repository는 Lazy 프록시로 연결)
    │
    └── CompletableFuture.runAsync() ← 새 스레드 생성
            │
            └── executeSync() [세션 없음!]
                    │
                    └── job.getRepository().getFullName()
                            ↓
                        LazyInitializationException
```

### 문제 코드

```java
@Transactional
public SyncJob startSync(UUID repositoryId, String branch) {
    // ... SyncJob 생성 및 저장

    // 비동기 실행 - 이 스레드는 트랜잭션 컨텍스트가 없음
    CompletableFuture.runAsync(() -> executeSync(savedJob.getId()));

    return job;
}

private void executeSync(UUID jobId) {
    SyncJob job = syncJobRepository.findById(jobId).orElse(null);  // Lazy 프록시

    Repository repo = job.getRepository();     // 아직 Lazy 프록시
    repo.getFullName();  // ← 여기서 실제 DB 조회 시도 → 세션 없음 → 예외!
}
```

## 해결 방법

### 방법 1: Fetch Join 사용 (권장)

Repository에 fetch join 쿼리를 추가하여 연관 엔티티를 함께 조회:

```java
// SyncJobRepository.java
@Query("SELECT j FROM SyncJob j JOIN FETCH j.repository WHERE j.id = :jobId")
Optional<SyncJob> findByIdWithRepository(@Param("jobId") UUID jobId);
```

```java
// SyncService.java - executeSync()
private void executeSync(UUID jobId) {
    // Repository를 함께 fetch join으로 조회
    SyncJob job = syncJobRepository.findByIdWithRepository(jobId).orElse(null);

    Repository repo = job.getRepository();  // 이미 로드된 엔티티
    repo.getFullName();  // 정상 동작
}
```

### 방법 2: 별도 조회

연관 엔티티를 별도로 다시 조회:

```java
private void executeSync(UUID jobId) {
    SyncJob job = syncJobRepository.findById(jobId).orElse(null);

    // Repository를 별도로 조회
    Repository repo = repositoryRepository.findById(job.getRepository().getId())
            .orElseThrow(() -> new IllegalStateException("Repository not found"));
}
```

### 방법 3: @Async + @Transactional (별도 서비스)

비동기 메서드를 별도 서비스로 분리하여 새 트랜잭션에서 실행:

```java
@Service
public class SyncExecutor {

    @Async
    @Transactional
    public void executeSync(UUID jobId) {
        // 새 트랜잭션에서 실행되므로 Lazy 로딩 정상 동작
    }
}
```

> 주의: `@Async`가 동작하려면 `@EnableAsync` 설정 필요

## 핵심 포인트

1. **비동기 스레드는 원래 트랜잭션을 상속받지 않음**
2. **Lazy 프록시는 세션이 열려 있을 때만 초기화 가능**
3. **비동기 컨텍스트에서는 필요한 데이터를 미리 로드하거나, 새 트랜잭션에서 다시 조회해야 함**

## 관련 파일

- `backend/src/main/java/com/docst/service/SyncService.java`
- `backend/src/main/java/com/docst/repository/SyncJobRepository.java`