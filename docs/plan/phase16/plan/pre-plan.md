# Phase 16 우선 과제: MCP UX 개선

## 개요

Phase 16의 기존 계획(그래프/브랜치/커밋 도구 확장)에 앞서 MCP 사용성 개선 과제를 우선 처리합니다.

---

## 과제 1: MCP 도구 호출 시 문서 정보 가시성 개선

### 현재 문제
- LLM이 `get_document(documentId="abc-123-...")` 형태로 호출할 때, 사용자는 어떤 문서인지 알기 어려움
- UUID만 표시되어 맥락 파악이 어려움

### 분석 결과

| 도구 | title 포함 | path 포함 | 상태 |
|------|-----------|----------|------|
| `list_documents` | O | O | 정상 |
| `get_document` | O | O | 정상 |
| `search_documents` | **X (null)** | O | **수정 필요** |

**핵심 문제**: `search_documents` 응답의 `SearchHit.title`이 항상 null로 설정됨

```java
// McpDocumentTools.java:281-290
var hits = results.stream()
    .map(r -> new SearchHit(
        r.documentId(),
        r.path(),
        null,  // <- title이 null로 고정됨
        ...
    ))
```

### 해결 방안

**방안 A (권장): SearchHit에 title 채우기**
- SearchResult가 documentId를 포함하므로, 해당 Document의 title을 조회하여 포함
- 성능 영향: N+1 쿼리 우려 -> batch 조회로 해결

**구현 방식**:
```java
// 1. 검색 결과의 documentId 목록 추출
List<UUID> docIds = results.stream()
    .map(SearchResult::documentId)
    .distinct()
    .toList();

// 2. Document 일괄 조회 (N+1 방지)
Map<UUID, Document> docMap = documentService.findByIds(docIds).stream()
    .collect(Collectors.toMap(Document::getId, d -> d));

// 3. SearchHit 생성 시 title 포함
var hits = results.stream()
    .map(r -> new SearchHit(
        r.documentId(),
        r.path(),
        docMap.get(r.documentId()).getTitle(),  // <- title 채움
        r.headingPath(),
        r.score(),
        r.snippet(),
        null
    ))
    .toList();
```

### 수정 대상 파일

| 파일 | 변경 내용 |
|------|----------|
| `DocumentService.java` | `findByIds(List<UUID>)` 메서드 추가 |
| `DocumentRepository.java` | `findByIdIn(List<UUID>)` 쿼리 추가 |
| `McpDocumentTools.java` | searchDocuments 메서드에서 title 채우기 |

---

## 과제 2: 문서 목록 조회 시 경로 필터링 개선

### 현재 문제
- `list_documents`에 `pathPrefix` 파라미터가 존재하지만
- `projectId`로 조회할 때는 필터링이 적용되지 않음

### 현재 코드 분석

```java
// McpDocumentTools.java:91-98
if (repoId != null) {
    documents = documentService.findByRepositoryId(repoId, pathPrefix, type);  // <- pathPrefix 적용됨
} else if (projId != null) {
    documents = documentService.findByProjectId(projId);  // <- pathPrefix 미적용!
}
```

### 해결 방안

**DocumentService와 Repository 확장**:

```java
// DocumentService.java - 신규 메서드
public List<Document> findByProjectId(UUID projectId, String pathPrefix, String docType) {
    DocType type = docType != null ? DocType.valueOf(docType.toUpperCase()) : null;
    String pathPattern = pathPrefix != null ? escapeLikePattern(pathPrefix) + "%" : null;
    return documentRepository.findByProjectIdWithFilters(projectId, pathPattern, type);
}

// DocumentRepository.java - 신규 쿼리
@Query("SELECT d FROM Document d JOIN d.repository r " +
       "WHERE r.project.id = :projectId AND d.deleted = false " +
       "AND (:pathPattern IS NULL OR d.path LIKE :pathPattern ESCAPE '!') " +
       "AND (:docType IS NULL OR d.docType = :docType) " +
       "ORDER BY d.path")
List<Document> findByProjectIdWithFilters(
    @Param("projectId") UUID projectId,
    @Param("pathPattern") String pathPattern,
    @Param("docType") DocType docType);
```

### 수정 대상 파일

| 파일 | 변경 내용 |
|------|----------|
| `DocumentRepository.java` | `findByProjectIdWithFilters` 쿼리 추가 |
| `DocumentService.java` | `findByProjectId(UUID, String, String)` 오버로드 추가 |
| `McpDocumentTools.java` | projectId 조회 시 pathPrefix, type 전달 |

---

## 구현 계획

### 작업 순서

```
1. DocumentRepository 확장
   +-- findByIdIn(List<UUID>) 추가
   +-- findByProjectIdWithFilters 추가

2. DocumentService 확장
   +-- findByIds(List<UUID>) 추가
   +-- findByProjectId(UUID, String, String) 오버로드 추가

3. McpDocumentTools 수정
   +-- searchDocuments: title 채우기
   +-- listDocuments: projectId 조회 시 필터 적용
```

### 상세 변경 내용

#### 1. DocumentRepository.java

```java
// 추가: ID 목록으로 문서 일괄 조회
List<Document> findByIdIn(List<UUID> ids);

// 추가: 프로젝트 ID + 필터로 문서 조회
@Query("SELECT d FROM Document d JOIN d.repository r " +
       "WHERE r.project.id = :projectId AND d.deleted = false " +
       "AND (:pathPattern IS NULL OR d.path LIKE :pathPattern ESCAPE '!') " +
       "AND (:docType IS NULL OR d.docType = :docType) " +
       "ORDER BY d.path")
List<Document> findByProjectIdWithFilters(
    @Param("projectId") UUID projectId,
    @Param("pathPattern") String pathPattern,
    @Param("docType") DocType docType);
```

#### 2. DocumentService.java

```java
// 추가: ID 목록으로 문서 조회
public List<Document> findByIds(List<UUID> ids) {
    if (ids == null || ids.isEmpty()) {
        return List.of();
    }
    return documentRepository.findByIdIn(ids);
}

// 추가: 프로젝트 ID + 필터로 문서 조회
public List<Document> findByProjectId(UUID projectId, String pathPrefix, String docType) {
    DocType type = docType != null ? DocType.valueOf(docType.toUpperCase()) : null;
    String pathPattern = null;
    if (pathPrefix != null && !pathPrefix.isBlank()) {
        pathPattern = escapeLikePattern(pathPrefix) + "%";
    }
    return documentRepository.findByProjectIdWithFilters(projectId, pathPattern, type);
}
```

#### 3. McpDocumentTools.java

**listDocuments 수정**:
```java
if (repoId != null) {
    documents = documentService.findByRepositoryId(repoId, pathPrefix, type);
} else if (projId != null) {
    // 변경: pathPrefix, type 필터 적용
    documents = documentService.findByProjectId(projId, pathPrefix, type);
}
```

**searchDocuments 수정**:
```java
// 검색 결과에서 documentId 추출
List<UUID> docIds = results.stream()
    .map(SearchResult::documentId)
    .filter(Objects::nonNull)
    .distinct()
    .toList();

// Document 일괄 조회 (N+1 방지)
Map<UUID, Document> docMap = documentService.findByIds(docIds).stream()
    .collect(Collectors.toMap(Document::getId, d -> d));

// SearchHit 생성 시 title 포함
var hits = results.stream()
    .map(r -> {
        Document doc = docMap.get(r.documentId());
        String title = doc != null ? doc.getTitle() : null;
        return new SearchHit(
            r.documentId(),
            r.path(),
            title,  // <- title 채움
            r.headingPath(),
            r.score(),
            r.snippet(),
            null
        );
    })
    .toList();
```

---

## 검증 계획

### 테스트 항목

1. **과제 1 검증: search_documents title 채우기**
   ```bash
   # MCP 도구 호출 테스트
   curl -X POST http://localhost:8342/sse \
     -H "X-API-Key: YOUR_KEY" \
     -d '{"method":"tools/call","params":{"name":"search_documents","arguments":{"projectId":"...","query":"test"}}}'

   # 응답에서 title 필드가 null이 아닌지 확인
   ```

2. **과제 2 검증: list_documents pathPrefix 필터**
   ```bash
   # projectId로 조회 시 pathPrefix 필터 테스트
   curl -X POST http://localhost:8342/sse \
     -H "X-API-Key: YOUR_KEY" \
     -d '{"method":"tools/call","params":{"name":"list_documents","arguments":{"projectId":"...","pathPrefix":"docs/"}}}'

   # 응답에서 docs/ 경로 문서만 반환되는지 확인
   ```

3. **단위 테스트**
   - `DocumentServiceTest`: findByIds, findByProjectId 오버로드 테스트
   - `McpDocumentToolsTest`: searchDocuments title 포함 테스트

---

## 예상 효과

| 개선 항목 | Before | After |
|----------|--------|-------|
| 검색 결과 title | null | 실제 문서 제목 |
| projectId 조회 시 경로 필터 | 미지원 | 지원 |
| LLM 사용자 경험 | UUID만 표시 | 제목+경로 표시 가능 |

---

## 소요 예상

- Repository/Service 확장: 간단한 CRUD 추가
- MCP Tools 수정: 기존 로직에 title 조회 추가
- 테스트: 기존 테스트 패턴 활용
