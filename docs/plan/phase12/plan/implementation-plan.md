# Phase 12: 동적 Sync 설정 시스템 - 구현 계획서

> 작성일: 2026-01-13
> 상태: 구현 준비 완료

---

## 1. 개요

### 1.1 목적
레포지토리 동기화 시 파일 확장자와 경로를 동적으로 설정할 수 있는 기능 구현.
현재 `GitFileScanner.java`에 하드코딩된 패턴을 DB 설정 기반으로 변경.

### 1.2 현재 문제점
- `GitFileScanner.java` 라인 29-37에 패턴 하드코딩
- 사용자 커스터마이징 불가
- 레포지토리별 설정 불가

### 1.3 해결 방안
- `dm_repository.sync_config` JSONB 컬럼 추가
- UI에서 확장자/경로 설정
- 동기화 시 설정 적용

---

## 2. 구현 파일 목록

### 2.1 신규 생성 (7개)

| # | 파일 경로 | 설명 |
|---|----------|------|
| 1 | `backend/src/main/resources/db/migration/V18__add_repository_sync_config.sql` | DB 마이그레이션 |
| 2 | `backend/src/main/java/com/docst/domain/RepositorySyncConfig.java` | 설정 Record |
| 3 | `backend/src/main/java/com/docst/service/FolderTreeService.java` | 폴더 트리 서비스 |
| 4 | `frontend/components/sync-config/sync-config-dialog.tsx` | 설정 다이얼로그 |
| 5 | `frontend/components/sync-config/extension-selector.tsx` | 확장자 선택 UI |
| 6 | `frontend/components/sync-config/path-selector.tsx` | 경로 선택 UI |
| 7 | `frontend/components/sync-config/index.ts` | 컴포넌트 export |

### 2.2 수정 (10개)

| # | 파일 경로 | 변경 내용 |
|---|----------|----------|
| 1 | `backend/.../domain/Repository.java` | syncConfig JSONB 필드 추가 |
| 2 | `backend/.../git/GitFileScanner.java` | 동적 패턴 생성 메서드 추가 |
| 3 | `backend/.../service/GitSyncService.java` | syncConfig 전달 로직 |
| 4 | `backend/.../service/RepositoryService.java` | updateSyncConfig 메서드 |
| 5 | `backend/.../api/RepositoriesController.java` | API 3개 추가 |
| 6 | `backend/.../api/ApiModels.java` | DTO 4개 추가 |
| 7 | `frontend/lib/types.ts` | 타입 4개 추가 |
| 8 | `frontend/lib/api.ts` | API 함수 3개 추가 |
| 9 | `frontend/hooks/use-api.ts` | React Query 훅 3개 추가 |
| 10 | `frontend/app/[locale]/projects/[projectId]/page.tsx` | 다이얼로그 연동 |

---

## 3. 상세 구현 명세

### 3.1 DB 마이그레이션 (V18)

```sql
-- dm_repository에 sync_config JSONB 컬럼 추가
ALTER TABLE dm_repository
ADD COLUMN sync_config JSONB DEFAULT '{
  "fileExtensions": ["md", "adoc"],
  "includePaths": [],
  "excludePaths": [".git", "node_modules", "target", "build", ".gradle", "dist", "out"],
  "scanOpenApi": true,
  "scanSwagger": true,
  "customPatterns": []
}'::jsonb;

COMMENT ON COLUMN dm_repository.sync_config IS 'Repository sync configuration (file extensions, paths, patterns)';
CREATE INDEX idx_dm_repository_sync_config ON dm_repository USING gin (sync_config);
```

### 3.2 백엔드 도메인

#### RepositorySyncConfig.java (신규)
```java
public record RepositorySyncConfig(
    List<String> fileExtensions,    // ["md", "adoc", "yml"]
    List<String> includePaths,      // ["docs/", "src/"]
    List<String> excludePaths,      // [".git", "node_modules"]
    boolean scanOpenApi,            // *.openapi.yaml/yml/json
    boolean scanSwagger,            // *.swagger.yaml/yml/json
    List<String> customPatterns     // 정규식 패턴
) {
    public static RepositorySyncConfig defaultConfig() {
        return new RepositorySyncConfig(
            List.of("md", "adoc"),
            List.of(),
            List.of(".git", "node_modules", "target", "build", ".gradle", "dist", "out"),
            true, true, List.of()
        );
    }
}
```

#### Repository.java 수정
```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "sync_config", columnDefinition = "jsonb")
private RepositorySyncConfig syncConfig;

public RepositorySyncConfig getSyncConfig() {
    return syncConfig != null ? syncConfig : RepositorySyncConfig.defaultConfig();
}
```

### 3.3 백엔드 서비스

#### GitFileScanner.java - 추가 메서드

```java
// 동적 패턴 생성
public List<Pattern> buildPatterns(RepositorySyncConfig config) {
    List<Pattern> patterns = new ArrayList<>();

    // 확장자 패턴
    for (String ext : config.fileExtensions()) {
        patterns.add(Pattern.compile(".*\\." + Pattern.quote(ext) + "$", CASE_INSENSITIVE));
    }

    // OpenAPI/Swagger 패턴
    if (config.scanOpenApi()) {
        patterns.add(Pattern.compile(".*openapi\\.(yaml|yml|json)$", CASE_INSENSITIVE));
    }
    if (config.scanSwagger()) {
        patterns.add(Pattern.compile(".*swagger\\.(yaml|yml|json)$", CASE_INSENSITIVE));
    }

    // 커스텀 패턴
    for (String custom : config.customPatterns()) {
        patterns.add(Pattern.compile(custom, CASE_INSENSITIVE));
    }

    return patterns.isEmpty() ? DOC_PATTERNS : patterns;
}

// 경로 필터링 포함 문서 파일 확인
public boolean isDocumentFile(String path, List<Pattern> patterns, RepositorySyncConfig config) {
    // 1. 제외 경로 체크
    for (String exclude : config.excludePaths()) {
        if (path.startsWith(exclude) || path.contains("/" + exclude)) return false;
    }

    // 2. 포함 경로 체크 (비어있으면 전체 허용)
    if (!config.includePaths().isEmpty()) {
        boolean included = config.includePaths().stream()
            .anyMatch(inc -> path.startsWith(inc));
        if (!included) return false;
    }

    // 3. 패턴 매칭
    return patterns.stream().anyMatch(p -> p.matcher(path).matches());
}

// 설정 기반 스캔 (오버로드)
public List<String> scanDocumentFiles(Git git, String commitSha, RepositorySyncConfig config) {
    List<Pattern> patterns = buildPatterns(config);
    // ... TreeWalk로 순회하며 isDocumentFile 호출
}
```

#### FolderTreeService.java (신규)
```java
@Service
public class FolderTreeService {
    public List<FolderTreeItem> getFolderTree(UUID repoId, int maxDepth) {
        // Git TreeWalk로 폴더 구조 조회
    }
}
```

### 3.4 백엔드 API

#### ApiModels.java - 추가 DTO
```java
record RepositorySyncConfigResponse(
    List<String> fileExtensions,
    List<String> includePaths,
    List<String> excludePaths,
    boolean scanOpenApi,
    boolean scanSwagger,
    List<String> customPatterns
) {}

record UpdateRepositorySyncConfigRequest(
    List<String> fileExtensions,
    List<String> includePaths,
    List<String> excludePaths,
    Boolean scanOpenApi,
    Boolean scanSwagger,
    List<String> customPatterns
) {}

record FolderTreeItem(
    String path,
    String name,
    boolean isDirectory,
    List<FolderTreeItem> children
) {}
```

#### RepositoriesController.java - 추가 엔드포인트
```java
@GetMapping("/repositories/{repoId}/sync-config")
@RequireRepositoryAccess(role = ProjectRole.VIEWER)
public ResponseEntity<RepositorySyncConfigResponse> getSyncConfig(@PathVariable UUID repoId)

@PutMapping("/repositories/{repoId}/sync-config")
@RequireRepositoryAccess(role = ProjectRole.ADMIN)
public ResponseEntity<RepositorySyncConfigResponse> updateSyncConfig(
    @PathVariable UUID repoId, @RequestBody UpdateRepositorySyncConfigRequest request)

@GetMapping("/repositories/{repoId}/folder-tree")
@RequireRepositoryAccess(role = ProjectRole.VIEWER)
public ResponseEntity<List<FolderTreeItem>> getFolderTree(
    @PathVariable UUID repoId, @RequestParam(defaultValue = "4") int depth)
```

### 3.5 프론트엔드

#### types.ts 추가
```typescript
interface RepositorySyncConfig {
  fileExtensions: string[];
  includePaths: string[];
  excludePaths: string[];
  scanOpenApi: boolean;
  scanSwagger: boolean;
  customPatterns: string[];
}

interface UpdateRepositorySyncConfigRequest { ... }
interface FolderTreeItem { ... }
```

#### api.ts - repositoriesApi 확장
```typescript
getSyncConfig: (id: string) => request(`/api/repositories/${id}/sync-config`),
updateSyncConfig: (id: string, data) => request(..., { method: 'PUT', body: ... }),
getFolderTree: (id: string, depth = 4) => request(`/api/repositories/${id}/folder-tree?depth=${depth}`),
```

#### use-api.ts - 훅 추가
```typescript
export function useRepositorySyncConfig(repoId: string | undefined)
export function useUpdateRepositorySyncConfig()
export function useFolderTree(repoId: string | undefined, depth = 4)
```

---

## 4. 구현 순서

```
Step 1: DB 마이그레이션
└── V18__add_repository_sync_config.sql

Step 2: 백엔드 도메인
├── RepositorySyncConfig.java (신규)
└── Repository.java (수정)

Step 3: 백엔드 서비스
├── GitFileScanner.java (동적 패턴 메서드 추가)
├── FolderTreeService.java (신규)
├── RepositoryService.java (updateSyncConfig 추가)
└── GitSyncService.java (config 전달)

Step 4: 백엔드 API
├── ApiModels.java (DTO 추가)
└── RepositoriesController.java (엔드포인트 추가)

Step 5: 프론트엔드 기반
├── types.ts
├── api.ts
└── use-api.ts

Step 6: 프론트엔드 UI
├── extension-selector.tsx
├── path-selector.tsx
├── sync-config-dialog.tsx
└── index.ts

Step 7: 페이지 연동
└── projects/[projectId]/page.tsx
```

---

## 5. 검증 방법

### 5.1 백엔드 API 테스트
```bash
# 마이그레이션 확인
./gradlew flywayInfo

# 설정 조회
curl -X GET http://localhost:8342/api/repositories/{id}/sync-config \
  -H "Authorization: Bearer {token}"

# 설정 수정
curl -X PUT http://localhost:8342/api/repositories/{id}/sync-config \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{"fileExtensions": ["md", "java"], "includePaths": ["docs/"]}'

# 폴더 트리
curl -X GET "http://localhost:8342/api/repositories/{id}/folder-tree?depth=3" \
  -H "Authorization: Bearer {token}"
```

### 5.2 E2E 시나리오
1. 레포지토리 카드 → Settings → "Sync Configuration" 클릭
2. 확장자 추가: `java`, `yml` 선택
3. 경로 설정: `docs/`, `src/` 포함, `test/` 제외
4. 저장 → 동기화 실행
5. 결과 확인: 설정에 맞는 파일만 동기화됨

---

## 6. 주의사항

### 6.1 보안
- `customPatterns`: ReDoS 방지 (패턴 길이 100자 제한)
- 경로 검증: `..` 포함 시 거부
- 권한: 설정 수정은 ADMIN 이상만 가능

### 6.2 성능
- 폴더 트리: depth 제한 (기본 4, 최대 6)
- JSONB: GIN 인덱스로 검색 성능 확보
- 패턴 컴파일: 캐싱 고려

### 6.3 호환성
- `syncConfig`가 null이면 기존 `DOC_PATTERNS` 사용
- 기존 `scanDocumentFiles(git, commit)` 메서드 유지 (오버로드 추가)
- 마이그레이션에 기본값 설정으로 기존 데이터 영향 없음

---

## 7. 기본값 정의

### 7.1 기본 파일 확장자
| 카테고리 | 확장자 | 기본 선택 |
|----------|--------|----------|
| 문서 | md, adoc, rst | md, adoc ✓ |
| 설정 | yml, yaml, json, toml | - |
| 코드 | java, py, ts, js, go, rs | - |

### 7.2 기본 제외 경로
```
.git, node_modules, target, build, .gradle, dist, out, __pycache__, .venv, vendor
```

### 7.3 기본 특수 패턴
- OpenAPI: `*.openapi.yaml/yml/json` ✓
- Swagger: `*.swagger.yaml/yml/json` ✓