# Troubleshooting: N+1 쿼리 문제와 Batch 조회 패턴

> **작성일**: 2025-01-26
> **관련 Phase**: Phase 16 (MCP UX 개선)
> **증상**: MCP `search_documents` 도구에서 검색 결과에 문서 제목을 포함시킬 때 성능 저하 우려

---

## 배경

### 문제 상황

`search_documents` MCP 도구의 응답에서 `SearchHit.title` 필드가 항상 `null`로 반환되는 문제가 있었습니다.

```java
// 기존 코드: McpDocumentTools.java
var hits = results.stream()
    .map(r -> new SearchHit(
        r.documentId(),
        r.path(),
        null,  // <- title이 항상 null
        r.headingPath(),
        r.score(),
        r.snippet(),
        null
    ))
    .toList();
```

### 해결 필요 사항

검색 결과(`SearchResult`)는 `documentId`를 포함하지만 `title`은 포함하지 않습니다.
`title`을 채우려면 `Document` 엔티티를 조회해야 합니다.

---

## N+1 쿼리 문제란?

### 정의

N+1 쿼리 문제는 1개의 쿼리로 N개의 데이터를 조회한 후, 각 데이터에 대해 추가로 N개의 쿼리를 실행하는 비효율적인 패턴입니다.

### 시각화

```
┌─────────────────────────────────────────────────────────────────┐
│                        N+1 쿼리 패턴                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  쿼리 1: 검색 결과 조회 (N개 결과)                                  │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ SELECT * FROM search_results WHERE project_id = ?        │   │
│  │ → 결과: [doc1, doc2, doc3, ..., docN]                    │   │
│  └─────────────────────────────────────────────────────────┘   │
│                              │                                   │
│                              ▼                                   │
│  ┌───────────────────────────────────────────────────────────┐ │
│  │ 쿼리 2: SELECT * FROM document WHERE id = 'doc1-uuid'     │ │
│  │ 쿼리 3: SELECT * FROM document WHERE id = 'doc2-uuid'     │ │
│  │ 쿼리 4: SELECT * FROM document WHERE id = 'doc3-uuid'     │ │
│  │ ...                                                        │ │
│  │ 쿼리 N+1: SELECT * FROM document WHERE id = 'docN-uuid'   │ │
│  └───────────────────────────────────────────────────────────┘ │
│                                                                  │
│  총 쿼리 수: 1 + N = N+1                                         │
│  N=10 → 11개 쿼리, N=100 → 101개 쿼리                            │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### N+1 쿼리의 위험성

| N (결과 수) | 쿼리 수 | 예상 지연 (쿼리당 5ms) |
|------------|---------|----------------------|
| 10 | 11 | 55ms |
| 50 | 51 | 255ms |
| 100 | 101 | 505ms |
| 1000 | 1001 | 5초 이상 |

**문제점**:
- 데이터베이스 연결 오버헤드 증가
- 네트워크 라운드트립 증가
- 데이터베이스 부하 증가
- 응답 시간 선형 증가

---

## 해결 방안 비교

### 방안 1: 개별 조회 (N+1 쿼리 - 사용 안 함)

```java
// ❌ N+1 쿼리 발생 - 사용하지 않음
var hits = results.stream()
    .map(r -> {
        // 각 결과마다 DB 쿼리 발생!
        Document doc = documentService.findById(r.documentId())
            .orElse(null);
        String title = doc != null ? doc.getTitle() : null;
        return new SearchHit(r.documentId(), r.path(), title, ...);
    })
    .toList();
```

**문제점**:
- 검색 결과 10개 → 11개 쿼리
- 검색 결과 100개 → 101개 쿼리
- 성능이 결과 수에 비례하여 저하

---

### 방안 2: Batch 조회 (채택)

```java
// ✅ Batch 조회 - 채택된 방안
// 1. documentId 목록 추출
List<UUID> docIds = results.stream()
    .map(r -> r.documentId())
    .filter(Objects::nonNull)
    .distinct()
    .toList();

// 2. 단일 쿼리로 Document 일괄 조회
Map<UUID, Document> docMap = documentService.findByIds(docIds).stream()
    .collect(Collectors.toMap(Document::getId, d -> d));

// 3. 메모리에서 매핑
var hits = results.stream()
    .map(r -> {
        Document doc = docMap.get(r.documentId());
        String title = doc != null ? doc.getTitle() : null;
        return new SearchHit(r.documentId(), r.path(), title, ...);
    })
    .toList();
```

**장점**:
- 항상 2개 쿼리 (검색 1회 + Document 조회 1회)
- 결과 수에 관계없이 일정한 쿼리 수
- 성능 예측 가능

**구현 요구사항**:
```java
// DocumentRepository.java
List<Document> findByIdIn(List<UUID> ids);

// DocumentService.java
public List<Document> findByIds(List<UUID> ids) {
    if (ids == null || ids.isEmpty()) {
        return List.of();
    }
    return documentRepository.findByIdIn(ids);
}
```

---

### 방안 3: SearchResult에 title 포함 (검토 후 미채택)

검색 서비스에서 처음부터 title을 포함하여 반환하는 방안.

```java
// SearchResult record에 title 필드 추가
public record SearchResult(
    UUID documentId,
    String path,
    String title,  // <- 추가
    String headingPath,
    double score,
    String snippet
) {}
```

**미채택 사유**:

| 고려사항 | 평가 |
|---------|------|
| **변경 범위** | SearchResult를 사용하는 모든 서비스 수정 필요 |
| **검색 쿼리 복잡도** | 벡터 검색 쿼리에 JOIN 추가 필요 |
| **기존 로직 영향** | KeywordSearchStrategy, SemanticSearchStrategy, HybridSearchService 등 모두 수정 |
| **성능** | JOIN 추가로 인한 검색 쿼리 성능 영향 가능 |

```sql
-- 현재 pgvector 검색 쿼리
SELECT dc.document_id, dc.heading_path, dc.content,
       1 - (dc.embedding <=> :queryVector) as similarity
FROM dm_doc_chunk dc
WHERE dc.project_id = :projectId
ORDER BY dc.embedding <=> :queryVector
LIMIT :topK

-- title 포함 시 (복잡도 증가)
SELECT dc.document_id, d.title, dc.heading_path, dc.content,
       1 - (dc.embedding <=> :queryVector) as similarity
FROM dm_doc_chunk dc
JOIN dm_document d ON dc.document_id = d.id  -- 추가 JOIN
WHERE dc.project_id = :projectId
ORDER BY dc.embedding <=> :queryVector
LIMIT :topK
```

---

### 방안 4: JPA Fetch Join (검토 후 미채택)

연관 엔티티를 함께 로드하는 JPA 패턴.

```java
@Query("SELECT d FROM Document d JOIN FETCH d.repository WHERE d.id IN :ids")
List<Document> findByIdInWithRepository(@Param("ids") List<UUID> ids);
```

**미채택 사유**:

| 고려사항 | 평가 |
|---------|------|
| **적용 범위** | 현재 상황에서는 Document만 필요하고 연관 엔티티는 불필요 |
| **오버페칭** | Repository까지 로드하면 불필요한 데이터 전송 |
| **적합한 케이스** | 연관 엔티티도 함께 필요할 때 유용 |

---

### 방안 5: @EntityGraph (검토 후 미채택)

선언적으로 연관 엔티티 로딩 전략 정의.

```java
@EntityGraph(attributePaths = {"repository"})
List<Document> findByIdIn(List<UUID> ids);
```

**미채택 사유**:
- 방안 4와 동일하게 현재 상황에서는 불필요한 오버페칭 발생
- title만 필요한데 Repository까지 로드할 필요 없음

---

## 채택 방안: Batch 조회

### 최종 구현 코드

**DocumentRepository.java**:
```java
/**
 * ID 목록으로 문서를 일괄 조회한다.
 *
 * @param ids 문서 ID 목록
 * @return 문서 목록
 */
List<Document> findByIdIn(List<UUID> ids);
```

**DocumentService.java**:
```java
/**
 * ID 목록으로 문서를 일괄 조회한다.
 *
 * @param ids 문서 ID 목록
 * @return 문서 목록
 */
public List<Document> findByIds(List<UUID> ids) {
    if (ids == null || ids.isEmpty()) {
        return List.of();
    }
    return documentRepository.findByIdIn(ids);
}
```

**McpDocumentTools.java**:
```java
// searchDocuments 메서드 내부
// 검색 결과에서 documentId 추출하여 Document 일괄 조회 (N+1 방지)
List<UUID> docIds = results.stream()
    .map(r -> r.documentId())
    .filter(Objects::nonNull)
    .distinct()
    .toList();

Map<UUID, Document> docMap = documentService.findByIds(docIds).stream()
    .collect(Collectors.toMap(Document::getId, d -> d));

var hits = results.stream()
    .map(r -> {
        Document doc = docMap.get(r.documentId());
        String title = doc != null ? doc.getTitle() : null;
        return new SearchHit(
            r.documentId(),
            r.path(),
            title,
            r.headingPath(),
            r.score(),
            r.snippet(),
            null
        );
    })
    .toList();
```

### 성능 비교

```
┌─────────────────────────────────────────────────────────────────┐
│                     성능 비교 (검색 결과 10개)                     │
├───────────────────┬─────────────────┬───────────────────────────┤
│ 방안              │ 쿼리 수          │ 예상 지연                   │
├───────────────────┼─────────────────┼───────────────────────────┤
│ N+1 쿼리 (개별)    │ 11              │ 55ms                      │
│ Batch 조회 (채택) │ 2               │ 10ms                      │
├───────────────────┼─────────────────┼───────────────────────────┤
│ 개선율            │ 82% 감소         │ 82% 감소                   │
└───────────────────┴─────────────────┴───────────────────────────┘
```

```
┌─────────────────────────────────────────────────────────────────┐
│                     성능 비교 (검색 결과 100개)                    │
├───────────────────┬─────────────────┬───────────────────────────┤
│ 방안              │ 쿼리 수          │ 예상 지연                   │
├───────────────────┼─────────────────┼───────────────────────────┤
│ N+1 쿼리 (개별)    │ 101             │ 505ms                     │
│ Batch 조회 (채택) │ 2               │ 10ms                      │
├───────────────────┼─────────────────┼───────────────────────────┤
│ 개선율            │ 98% 감소         │ 98% 감소                   │
└───────────────────┴─────────────────┴───────────────────────────┘
```

---

## 시퀀스 다이어그램

### N+1 쿼리 (문제 패턴)

```
Client          MCP Tools       Search Service    Document Service    Database
  │                │                  │                  │                │
  │ search_docs    │                  │                  │                │
  │───────────────>│                  │                  │                │
  │                │ search(query)    │                  │                │
  │                │─────────────────>│                  │                │
  │                │                  │ pgvector search  │                │
  │                │                  │────────────────────────────────────>│
  │                │                  │<────────────────────────────────────│
  │                │ results (10개)   │                  │                │
  │                │<─────────────────│                  │                │
  │                │                  │                  │                │
  │                │ for each result:                    │                │
  │                │ ┌──────────────────────────────────────────────────┐ │
  │                │ │ findById(doc1)                    │              │ │
  │                │ │ ─────────────────────────────────>│              │ │
  │                │ │                                   │ SELECT       │ │
  │                │ │                                   │─────────────>│ │
  │                │ │                                   │<─────────────│ │
  │                │ │ findById(doc2)                    │              │ │
  │                │ │ ─────────────────────────────────>│              │ │
  │                │ │                                   │ SELECT       │ │
  │                │ │                                   │─────────────>│ │
  │                │ │                                   │<─────────────│ │
  │                │ │ ... (8번 더 반복)                  │              │ │
  │                │ └──────────────────────────────────────────────────┘ │
  │                │                  │                  │                │
  │ response       │                  │                  │                │
  │<───────────────│                  │                  │                │
  │                │                  │                  │                │

총 쿼리: 1 (검색) + 10 (개별 조회) = 11개
```

### Batch 조회 (해결 패턴)

```
Client          MCP Tools       Search Service    Document Service    Database
  │                │                  │                  │                │
  │ search_docs    │                  │                  │                │
  │───────────────>│                  │                  │                │
  │                │ search(query)    │                  │                │
  │                │─────────────────>│                  │                │
  │                │                  │ pgvector search  │                │
  │                │                  │────────────────────────────────────>│
  │                │                  │<────────────────────────────────────│
  │                │ results (10개)   │                  │                │
  │                │<─────────────────│                  │                │
  │                │                  │                  │                │
  │                │ extract docIds   │                  │                │
  │                │ [doc1..doc10]    │                  │                │
  │                │                  │                  │                │
  │                │ findByIds([...]) │                  │                │
  │                │ ─────────────────────────────────────>│              │
  │                │                  │                  │ SELECT WHERE  │
  │                │                  │                  │ id IN (...)   │
  │                │                  │                  │─────────────>│
  │                │                  │                  │<─────────────│
  │                │ documents (10개) │                  │                │
  │                │<─────────────────────────────────────│              │
  │                │                  │                  │                │
  │                │ map in memory    │                  │                │
  │                │ build SearchHits │                  │                │
  │                │                  │                  │                │
  │ response       │                  │                  │                │
  │<───────────────│                  │                  │                │
  │                │                  │                  │                │

총 쿼리: 1 (검색) + 1 (batch 조회) = 2개
```

---

## 베스트 프랙티스

### 1. N+1 쿼리 감지 방법

```yaml
# application.yml - 개발 환경에서 쿼리 로깅 활성화
spring:
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true
logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.orm.jdbc.bind: TRACE
```

### 2. Batch 조회 패턴 적용 기준

| 상황 | 권장 패턴 |
|------|----------|
| 루프 내에서 연관 엔티티 조회 | Batch 조회 |
| 1:N 관계에서 N 조회 | Fetch Join 또는 @EntityGraph |
| ID 목록으로 엔티티 조회 | `findByIdIn()` |
| 단일 엔티티 조회 | `findById()` |

### 3. 주의사항

```java
// ✅ 올바른 Batch 조회
List<UUID> ids = getIds();
if (!ids.isEmpty()) {  // 빈 리스트 체크
    repository.findByIdIn(ids);
}

// ❌ 빈 리스트 전달 시 문제 (DB에 따라 오류 발생 가능)
repository.findByIdIn(Collections.emptyList());
```

### 4. IN 절 제한

PostgreSQL의 IN 절에는 최대 개수 제한이 있습니다 (약 32,767개).
대량 데이터 처리 시 청크 분할 필요:

```java
public List<Document> findByIds(List<UUID> ids) {
    if (ids == null || ids.isEmpty()) {
        return List.of();
    }

    // 1000개씩 청크 분할 (안전한 크기)
    int chunkSize = 1000;
    List<Document> result = new ArrayList<>();

    for (int i = 0; i < ids.size(); i += chunkSize) {
        List<UUID> chunk = ids.subList(i, Math.min(i + chunkSize, ids.size()));
        result.addAll(documentRepository.findByIdIn(chunk));
    }

    return result;
}
```

---

## 관련 파일

| 파일 | 설명 |
|------|------|
| `backend/src/main/java/com/docst/document/repository/DocumentRepository.java` | `findByIdIn()` 메서드 추가 |
| `backend/src/main/java/com/docst/document/service/DocumentService.java` | `findByIds()` 메서드 추가 |
| `backend/src/main/java/com/docst/mcp/tools/McpDocumentTools.java` | Batch 조회 적용 |

---

## 참고 자료

- [Hibernate N+1 Query Problem](https://docs.jboss.org/hibernate/orm/6.4/userguide/html_single/Hibernate_User_Guide.html#fetching-strategies)
- [Spring Data JPA - Query Methods](https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html)
- [PostgreSQL IN Clause Performance](https://www.postgresql.org/docs/current/functions-comparisons.html)
