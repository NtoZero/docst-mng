# PostgreSQL LIKE 연산자 타입 불일치 오류

## 증상

문서 목록 조회 시 다음과 같은 SQL 예외 발생:

```
ERROR: operator does not exist: character varying ~~ bytea
Hint: No operator matches the given name and argument types. You might need to add explicit type casts.
Position: 224
```

## 환경

- Spring Boot 3.5.x
- Hibernate 6.6.x
- PostgreSQL 16 + pgvector
- Spring Data JPA 3.5.x

## 원인

### 1. Hibernate 6.x의 null 파라미터 바인딩 문제

JPQL에서 `CONCAT` 함수와 null 가능 파라미터를 함께 사용할 때 발생:

```java
// 문제가 되는 쿼리
@Query("SELECT d FROM Document d WHERE d.repository.id = :repoId " +
       "AND (:pathPrefix IS NULL OR d.path LIKE CONCAT(:pathPrefix, '%'))")
```

`pathPrefix`가 `null`일 때, Hibernate 6.x가 이를 `bytea`(바이트 배열) 타입으로 바인딩합니다.
PostgreSQL의 `LIKE`(`~~`) 연산자는 `character varying`과 `bytea` 간 비교를 지원하지 않아 오류 발생.

### 2. 생성되는 SQL

```sql
SELECT ... FROM dm_document d1_0
WHERE d1_0.repository_id=?
  AND d1_0.deleted=false
  AND (? is null or d1_0.path like (?||'%') escape '')
--                               ↑ bytea 타입으로 전달됨
```

## 해결 방법

### Step 1: CONCAT 제거 및 서비스 레이어에서 패턴 생성

**DocumentRepository.java**
```java
// Before (문제)
@Query("... AND (:pathPrefix IS NULL OR d.path LIKE CONCAT(:pathPrefix, '%'))")

// After (해결) - ESCAPE 문자로 '!'를 사용 (백슬래시는 JPQL 이스케이프 문제 발생)
@Query("SELECT d FROM Document d WHERE d.repository.id = :repoId AND d.deleted = false " +
       "AND (:pathPattern IS NULL OR d.path LIKE :pathPattern ESCAPE '!') " +
       "AND (:docType IS NULL OR d.docType = :docType) " +
       "ORDER BY d.path")
List<Document> findByRepositoryIdWithFilters(
        @Param("repoId") UUID repositoryId,
        @Param("pathPattern") String pathPattern,  // 이미 '%' 포함된 패턴
        @Param("docType") DocType docType);
```

> **Note**: ESCAPE 문자로 `\` 대신 `!`를 사용합니다.
> JPQL에서 `ESCAPE '\\'`는 두 문자로 해석되어 `SemanticException: Escape character literals must have exactly a single character` 오류가 발생합니다.

### Step 2: 서비스에서 LIKE 패턴 생성 + 보안 처리

**DocumentService.java**
```java
public List<Document> findByRepositoryId(UUID repositoryId, String pathPrefix, String docType) {
    DocType type = docType != null ? DocType.valueOf(docType.toUpperCase()) : null;

    // pathPrefix가 있으면 LIKE 패턴으로 변환 (LIKE 특수문자 이스케이프 처리)
    String pathPattern = null;
    if (pathPrefix != null) {
        String escaped = escapeLikePattern(pathPrefix);
        pathPattern = escaped + "%";
    }
    return documentRepository.findByRepositoryIdWithFilters(repositoryId, pathPattern, type);
}

/**
 * LIKE 패턴에서 사용되는 특수문자를 이스케이프 처리한다.
 * Wildcard Injection 방지를 위해 %, _, ! 문자를 이스케이프한다.
 * ESCAPE 문자로 '!'를 사용한다.
 */
private String escapeLikePattern(String input) {
    return input
            .replace("!", "!!")     // 이스케이프 문자 먼저 처리
            .replace("%", "!%")
            .replace("_", "!_");
}
```

## 보안 고려사항: Wildcard Injection 방지

### 문제

SQL Injection은 PreparedStatement로 방지되지만, LIKE 패턴의 와일드카드 문자 주입은 별도 처리 필요:

| 공격 입력 | 처리 전 패턴 | 결과 |
|----------|-------------|------|
| `%` | `%%` | 모든 문서 반환 (의도치 않음) |
| `_` | `_%` | 단일문자 와일드카드로 동작 |

### 해결

1. **특수문자 이스케이프**: `!`, `%`, `_` 문자를 `!!`, `!%`, `!_`로 변환
2. **ESCAPE 절 추가**: JPQL에 `ESCAPE '!'` 추가

### 검증

| 사용자 입력 | 이스케이프 후 | 최종 패턴 | 동작 |
|------------|-------------|----------|------|
| `docs/` | `docs/` | `docs/%` | docs/로 시작하는 문서 |
| `%` | `!%` | `!%%` | 리터럴 '%'로 시작하는 문서 |
| `_test` | `!_test` | `!_test%` | 리터럴 '_test'로 시작하는 문서 |
| `test!` | `test!!` | `test!!%` | 리터럴 'test!'로 시작하는 문서 |

## 관련 파일

- `backend/src/main/java/com/docst/repository/DocumentRepository.java`
- `backend/src/main/java/com/docst/service/DocumentService.java`

## 참고

- [Hibernate 6 Migration Guide](https://docs.jboss.org/hibernate/orm/6.0/migration-guide/migration-guide.html)
- [PostgreSQL Pattern Matching](https://www.postgresql.org/docs/current/functions-matching.html)
- [OWASP SQL Injection Prevention](https://cheatsheetseries.owasp.org/cheatsheets/SQL_Injection_Prevention_Cheat_Sheet.html)
