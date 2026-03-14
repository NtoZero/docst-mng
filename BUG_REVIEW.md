# Bug Review: diff_document Short SHA 미지원

## 발견 경위

`diff_document` MCP 도구 호출 시 short SHA(`114cdb8`)를 사용하면 `"Version not found"` 에러 발생.
full SHA를 사용해야만 정상 동작.

## 원인 분석

### 근본 원인

`DocumentVersionRepository.findByDocumentIdAndCommitSha()`가 **정확한 문자열 매칭(=)**을 사용.
DB에는 full SHA(40자)가 저장되어 있으므로, short SHA(7자 등)로는 절대 매칭되지 않음.

### 영향 범위

| MCP 도구 | 영향받는 파라미터 | 위치 |
|----------|------------------|------|
| `diff_document` | `fromCommitSha`, `toCommitSha` | `McpDocumentTools.java:212-213` |
| `get_document` | `commitSha` | `McpDocumentTools.java:143-144` |

### 호출 경로

```
McpDocumentTools.diffDocument()
  → documentService.findVersion(docId, commitSha)
    → documentVersionRepository.findByDocumentIdAndCommitSha(documentId, commitSha)
      → JPA: SELECT ... WHERE document_id = ? AND commit_sha = ?  (정확 매칭)
```

## 수정 방안

### Option A: Repository에 prefix 매칭 쿼리 추가 (권장)

`DocumentVersionRepository`에 LIKE 기반 쿼리 추가:

```java
@Query("SELECT dv FROM DocumentVersion dv WHERE dv.document.id = :documentId AND dv.commitSha LIKE :commitShaPrefix || '%'")
Optional<DocumentVersion> findByDocumentIdAndCommitShaStartingWith(
    @Param("documentId") UUID documentId,
    @Param("commitShaPrefix") String commitShaPrefix);
```

### Option B: Service 레이어에서 fallback 처리

`DocumentService.findVersion()`에서 short SHA 감지 후 fallback:

```java
public Optional<DocumentVersion> findVersion(UUID documentId, String commitSha) {
    // 1차: 정확 매칭 시도
    Optional<DocumentVersion> result = documentVersionRepository.findByDocumentIdAndCommitSha(documentId, commitSha);
    if (result.isPresent()) return result;

    // 2차: short SHA인 경우 prefix 매칭
    if (commitSha != null && commitSha.length() < 40) {
        return documentVersionRepository.findByDocumentIdAndCommitShaStartingWith(documentId, commitSha);
    }
    return Optional.empty();
}
```

### 권장: Option B

- 기존 full SHA 사용 코드에 영향 없음 (정확 매칭 우선)
- short SHA일 때만 추가 쿼리 실행
- Git의 short SHA 관행(7자 이상)과 일관성 유지
- 복수 매칭 시 에러 처리도 고려 필요 (ambiguous SHA)

## 추가 고려사항

- Short SHA가 여러 버전에 매칭될 경우 ambiguous 에러 반환 필요
- MCP 도구 description에 "full or abbreviated commit SHA" 지원 명시 권장
- REST API의 `/api/documents/{id}/versions`도 동일 이슈 가능성 확인 필요
