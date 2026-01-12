# Phase 12: 동적 Sync 설정 시스템

## 1. 개요

### 1.1 목적
레포지토리 동기화 시 파일 확장자와 경로를 동적으로 설정할 수 있는 기능을 구현합니다.

### 1.2 현재 상태

| 구분 | 현재 | 문제점 |
|------|------|--------|
| 파일 확장자 | `GitFileScanner.java`에 하드코딩 | 사용자 커스터마이징 불가 |
| 지원 확장자 | *.md, *.adoc, *openapi.*, *swagger.* | 제한적 |
| 경로 필터링 | 없음 (전체 스캔) | 불필요한 파일까지 스캔 |
| 설정 저장 | 없음 | 레포지토리별 설정 불가 |

### 1.3 목표

1. **파일 확장자 동적 선택**
   - 기본값: 현재 확장자 (md, adoc, openapi, swagger)
   - 추가 선택: .yml, .yaml, .json, .java, .py, .ts, .js 등
   - 다중 선택 가능

2. **경로 동적 설정 (Optional)**
   - 포함 경로: 특정 폴더만 스캔 (예: docs/, architecture/)
   - 제외 경로: 특정 폴더 제외 (예: node_modules/, .git/)
   - 폴더 트리 UI로 다중 선택

3. **UI/백엔드 연계**
   - 레포지토리 설정 페이지에서 필터 설정
   - 설정은 DB에 저장되어 다음 동기화에 반영

---

## 2. 아키텍처 설계

### 2.1 데이터 모델

```
┌─────────────────────────────────────────────────────────────┐
│                     dm_repository                            │
├─────────────────────────────────────────────────────────────┤
│  ... 기존 컬럼들 ...                                          │
│  sync_config JSONB  ← 신규 추가                              │
│    {                                                         │
│      "fileExtensions": ["md", "adoc", "yml"],               │
│      "includePaths": ["docs/", "architecture/"],            │
│      "excludePaths": [".git", "node_modules"],              │
│      "scanOpenApi": true,                                   │
│      "scanSwagger": true,                                   │
│      "customPatterns": []                                   │
│    }                                                        │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 시스템 흐름

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│   Frontend   │────▶│   REST API   │────▶│   Database   │
│  SyncConfig  │     │  Controller  │     │  Repository  │
│   Dialog     │     │              │     │  sync_config │
└──────────────┘     └──────────────┘     └──────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────┐
│                    동기화 실행 시                          │
├──────────────────────────────────────────────────────────┤
│  GitSyncService                                          │
│       │                                                  │
│       ▼                                                  │
│  Repository.getSyncConfig()                              │
│       │                                                  │
│       ▼                                                  │
│  GitFileScanner.scanDocumentFiles(git, commit, config)   │
│       │                                                  │
│       ├── buildPatterns(config)      ← 동적 패턴 생성     │
│       ├── isDocumentFile(path, patterns, config)         │
│       │       ├── excludePaths 체크                      │
│       │       ├── includePaths 체크                      │
│       │       └── 패턴 매칭                              │
│       └── 필터링된 문서 목록 반환                         │
└──────────────────────────────────────────────────────────┘
```

---

## 3. 상세 구현 계획

### 3.1 DB 스키마 (V18 마이그레이션)

**파일**: `backend/src/main/resources/db/migration/V18__add_repository_sync_config.sql`

```sql
-- Repository에 동기화 설정 JSONB 컬럼 추가
ALTER TABLE dm_repository
ADD COLUMN sync_config JSONB DEFAULT '{
  "fileExtensions": ["md", "adoc"],
  "includePaths": [],
  "excludePaths": [".git", "node_modules", "target", "build", ".gradle", "dist", "out"],
  "scanOpenApi": true,
  "scanSwagger": true,
  "customPatterns": []
}'::jsonb NOT NULL;

-- 설명 추가
COMMENT ON COLUMN dm_repository.sync_config IS 'Repository synchronization configuration (file extensions, paths, patterns)';

-- JSONB 검색 성능을 위한 GIN 인덱스
CREATE INDEX idx_dm_repository_sync_config ON dm_repository USING gin (sync_config);
```

### 3.2 백엔드 엔티티

#### 3.2.1 RepositorySyncConfig.java (신규)

**경로**: `backend/src/main/java/com/docst/domain/RepositorySyncConfig.java`

```java
package com.docst.domain;

import java.io.Serializable;
import java.util.List;

/**
 * 레포지토리 동기화 설정.
 * JSONB로 저장되어 유연한 확장이 가능하다.
 */
public record RepositorySyncConfig(
    /** 동기화할 파일 확장자 목록 (예: ["md", "adoc", "yml"]) */
    List<String> fileExtensions,

    /** 포함 경로 목록 - 비어있으면 전체 경로 스캔 (예: ["docs/", "src/"]) */
    List<String> includePaths,

    /** 제외 경로 목록 (예: [".git", "node_modules"]) */
    List<String> excludePaths,

    /** OpenAPI 스펙 파일 스캔 여부 (*.openapi.yaml/yml/json) */
    boolean scanOpenApi,

    /** Swagger 스펙 파일 스캔 여부 (*.swagger.yaml/yml/json) */
    boolean scanSwagger,

    /** 커스텀 정규식 패턴 목록 */
    List<String> customPatterns
) implements Serializable {

    /**
     * 기본 동기화 설정을 반환한다.
     */
    public static RepositorySyncConfig defaultConfig() {
        return new RepositorySyncConfig(
            List.of("md", "adoc"),
            List.of(),  // 빈 목록 = 전체 경로 스캔
            List.of(".git", "node_modules", "target", "build", ".gradle", "dist", "out"),
            true,   // OpenAPI 스캔
            true,   // Swagger 스캔
            List.of()
        );
    }
}
```

#### 3.2.2 Repository.java 수정

```java
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// 기존 필드들 아래에 추가
/** 동기화 설정 (JSONB) */
@Setter
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "sync_config", columnDefinition = "jsonb")
private RepositorySyncConfig syncConfig;

/**
 * 동기화 설정을 반환한다.
 * null이면 기본 설정을 반환한다.
 */
public RepositorySyncConfig getSyncConfig() {
    return syncConfig != null ? syncConfig : RepositorySyncConfig.defaultConfig();
}
```

### 3.3 백엔드 API 모델

**파일**: `backend/src/main/java/com/docst/api/ApiModels.java`에 추가

```java
// ===== Repository Sync Config =====

/**
 * 레포지토리 동기화 설정 응답.
 */
public record RepositorySyncConfigResponse(
    List<String> fileExtensions,
    List<String> includePaths,
    List<String> excludePaths,
    boolean scanOpenApi,
    boolean scanSwagger,
    List<String> customPatterns
) {
    public static RepositorySyncConfigResponse from(RepositorySyncConfig config) {
        return new RepositorySyncConfigResponse(
            config.fileExtensions(),
            config.includePaths(),
            config.excludePaths(),
            config.scanOpenApi(),
            config.scanSwagger(),
            config.customPatterns()
        );
    }
}

/**
 * 레포지토리 동기화 설정 업데이트 요청.
 */
public record UpdateRepositorySyncConfigRequest(
    List<String> fileExtensions,
    List<String> includePaths,
    List<String> excludePaths,
    Boolean scanOpenApi,
    Boolean scanSwagger,
    List<String> customPatterns
) {}

/**
 * 폴더 트리 항목.
 */
public record FolderTreeItem(
    String path,
    String name,
    boolean isDirectory,
    List<FolderTreeItem> children
) {}
```

### 3.4 백엔드 서비스

#### 3.4.1 GitFileScanner.java 수정

**핵심 변경**: 동적 설정 기반 스캔 메서드 추가

```java
/**
 * 동적 설정을 기반으로 문서 파일을 스캔한다.
 *
 * @param git Git 인스턴스
 * @param commitSha 커밋 SHA
 * @param config 동기화 설정
 * @return 문서 파일 경로 목록
 */
public List<String> scanDocumentFiles(Git git, String commitSha, RepositorySyncConfig config)
        throws IOException {
    List<String> documentPaths = new ArrayList<>();
    List<Pattern> patterns = buildPatterns(config);

    try (RevWalk revWalk = new RevWalk(git.getRepository())) {
        ObjectId commitId = git.getRepository().resolve(commitSha);
        if (commitId == null) {
            log.warn("Commit not found: {}", commitSha);
            return documentPaths;
        }

        RevCommit commit = revWalk.parseCommit(commitId);
        RevTree tree = commit.getTree();

        try (TreeWalk treeWalk = new TreeWalk(git.getRepository())) {
            treeWalk.addTree(tree);
            treeWalk.setRecursive(true);

            while (treeWalk.next()) {
                String path = treeWalk.getPathString();
                if (isDocumentFile(path, patterns, config)) {
                    documentPaths.add(path);
                }
            }
        }
    }

    log.info("Scanned {} document files with config at commit {}",
             documentPaths.size(), commitSha.substring(0, 7));
    return documentPaths;
}

/**
 * 설정을 기반으로 패턴 목록을 생성한다.
 */
private List<Pattern> buildPatterns(RepositorySyncConfig config) {
    List<Pattern> patterns = new ArrayList<>();

    // 사용자 지정 확장자
    for (String ext : config.fileExtensions()) {
        String escaped = Pattern.quote(ext);
        patterns.add(Pattern.compile(".*\\." + escaped + "$", Pattern.CASE_INSENSITIVE));
    }

    // OpenAPI 옵션
    if (config.scanOpenApi()) {
        patterns.add(Pattern.compile(".*openapi\\.(yaml|yml|json)$", Pattern.CASE_INSENSITIVE));
    }

    // Swagger 옵션
    if (config.scanSwagger()) {
        patterns.add(Pattern.compile(".*swagger\\.(yaml|yml|json)$", Pattern.CASE_INSENSITIVE));
    }

    // 커스텀 패턴
    for (String customPattern : config.customPatterns()) {
        try {
            patterns.add(Pattern.compile(customPattern, Pattern.CASE_INSENSITIVE));
        } catch (PatternSyntaxException e) {
            log.warn("Invalid custom pattern ignored: {}", customPattern);
        }
    }

    // 패턴이 없으면 기본 패턴 사용
    return patterns.isEmpty() ? DOC_PATTERNS : patterns;
}

/**
 * 파일이 문서 파일인지 확인한다 (경로 필터링 포함).
 */
public boolean isDocumentFile(String path, List<Pattern> patterns, RepositorySyncConfig config) {
    // 1. 제외 경로 체크
    for (String excludePath : config.excludePaths()) {
        if (path.startsWith(excludePath) || path.startsWith(excludePath + "/")
            || path.contains("/" + excludePath + "/") || path.contains("/" + excludePath)) {
            return false;
        }
    }

    // 2. 포함 경로 체크 (비어있으면 모든 경로 허용)
    if (!config.includePaths().isEmpty()) {
        boolean inIncludedPath = false;
        for (String includePath : config.includePaths()) {
            if (path.startsWith(includePath) || path.startsWith(includePath + "/")) {
                inIncludedPath = true;
                break;
            }
        }
        if (!inIncludedPath) {
            return false;
        }
    }

    // 3. 패턴 매칭
    for (Pattern pattern : patterns) {
        if (pattern.matcher(path).matches()) {
            return true;
        }
    }
    return false;
}
```

#### 3.4.2 FolderTreeService.java (신규)

**경로**: `backend/src/main/java/com/docst/service/FolderTreeService.java`

```java
package com.docst.service;

import com.docst.api.ApiModels.FolderTreeItem;
import com.docst.domain.Repository;
import com.docst.git.GitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class FolderTreeService {

    private final GitService gitService;
    private final RepositoryService repositoryService;

    /**
     * 레포지토리의 폴더 트리를 조회한다.
     */
    public List<FolderTreeItem> getFolderTree(UUID repositoryId, int maxDepth) {
        return repositoryService.findById(repositoryId)
            .map(repo -> {
                try (Git git = gitService.cloneOrOpen(repo)) {
                    String latestCommit = gitService.getLatestCommitSha(git, repo.getDefaultBranch());
                    return buildFolderTree(git, latestCommit, maxDepth);
                } catch (Exception e) {
                    log.error("Failed to get folder tree for repository {}", repositoryId, e);
                    return List.<FolderTreeItem>of();
                }
            })
            .orElse(List.of());
    }

    private List<FolderTreeItem> buildFolderTree(Git git, String commitSha, int maxDepth) throws Exception {
        Set<String> directories = new TreeSet<>();

        try (RevWalk revWalk = new RevWalk(git.getRepository())) {
            ObjectId commitId = git.getRepository().resolve(commitSha);
            if (commitId == null) return List.of();

            RevCommit commit = revWalk.parseCommit(commitId);
            try (TreeWalk treeWalk = new TreeWalk(git.getRepository())) {
                treeWalk.addTree(commit.getTree());
                treeWalk.setRecursive(true);

                while (treeWalk.next()) {
                    String path = treeWalk.getPathString();
                    // 디렉토리 경로 추출
                    int lastSlash = path.lastIndexOf('/');
                    if (lastSlash > 0) {
                        String dir = path.substring(0, lastSlash);
                        directories.add(dir);
                        // 상위 디렉토리들도 추가
                        while ((lastSlash = dir.lastIndexOf('/')) > 0) {
                            dir = dir.substring(0, lastSlash);
                            directories.add(dir);
                        }
                    }
                }
            }
        }

        return buildTreeStructure(directories, maxDepth);
    }

    private List<FolderTreeItem> buildTreeStructure(Set<String> directories, int maxDepth) {
        // 트리 구조로 변환하는 로직
        Map<String, FolderTreeItem> itemMap = new HashMap<>();
        List<FolderTreeItem> roots = new ArrayList<>();

        for (String dir : directories) {
            int depth = dir.split("/").length;
            if (depth > maxDepth) continue;

            String name = dir.contains("/") ? dir.substring(dir.lastIndexOf('/') + 1) : dir;
            FolderTreeItem item = new FolderTreeItem(dir + "/", name, true, new ArrayList<>());
            itemMap.put(dir, item);

            int lastSlash = dir.lastIndexOf('/');
            if (lastSlash > 0) {
                String parentDir = dir.substring(0, lastSlash);
                FolderTreeItem parent = itemMap.get(parentDir);
                if (parent != null) {
                    parent.children().add(item);
                }
            } else {
                roots.add(item);
            }
        }

        return roots;
    }
}
```

### 3.5 백엔드 API Controller

**파일**: `backend/src/main/java/com/docst/api/RepositoriesController.java`에 추가

```java
// ===== Sync Config APIs =====

/**
 * 레포지토리 동기화 설정을 조회한다.
 */
@Operation(summary = "동기화 설정 조회", description = "레포지토리의 동기화 설정을 조회합니다.")
@GetMapping("/repositories/{repoId}/sync-config")
@RequireRepositoryAccess(role = ProjectRole.VIEWER, repositoryIdParam = "repoId")
public ResponseEntity<RepositorySyncConfigResponse> getSyncConfig(@PathVariable UUID repoId) {
    return repositoryService.findById(repoId)
        .map(repo -> RepositorySyncConfigResponse.from(repo.getSyncConfig()))
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
}

/**
 * 레포지토리 동기화 설정을 업데이트한다.
 */
@Operation(summary = "동기화 설정 수정", description = "레포지토리의 동기화 설정을 수정합니다.")
@PutMapping("/repositories/{repoId}/sync-config")
@RequireRepositoryAccess(role = ProjectRole.ADMIN, repositoryIdParam = "repoId")
public ResponseEntity<RepositorySyncConfigResponse> updateSyncConfig(
        @PathVariable UUID repoId,
        @RequestBody UpdateRepositorySyncConfigRequest request) {
    return repositoryService.updateSyncConfig(repoId, request)
        .map(RepositorySyncConfigResponse::from)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
}

/**
 * 레포지토리의 폴더 트리 구조를 조회한다.
 */
@Operation(summary = "폴더 트리 조회", description = "레포지토리의 폴더 구조를 트리 형태로 조회합니다.")
@GetMapping("/repositories/{repoId}/folder-tree")
@RequireRepositoryAccess(role = ProjectRole.VIEWER, repositoryIdParam = "repoId")
public ResponseEntity<List<FolderTreeItem>> getFolderTree(
        @PathVariable UUID repoId,
        @RequestParam(defaultValue = "4") int depth) {
    List<FolderTreeItem> tree = folderTreeService.getFolderTree(repoId, Math.min(depth, 6));
    return ResponseEntity.ok(tree);
}
```

### 3.6 프론트엔드 타입

**파일**: `frontend/lib/types.ts`에 추가

```typescript
// ===== Repository Sync Config =====

export interface RepositorySyncConfig {
  fileExtensions: string[];
  includePaths: string[];
  excludePaths: string[];
  scanOpenApi: boolean;
  scanSwagger: boolean;
  customPatterns: string[];
}

export interface UpdateRepositorySyncConfigRequest {
  fileExtensions?: string[];
  includePaths?: string[];
  excludePaths?: string[];
  scanOpenApi?: boolean;
  scanSwagger?: boolean;
  customPatterns?: string[];
}

export interface FolderTreeItem {
  path: string;
  name: string;
  isDirectory: boolean;
  children?: FolderTreeItem[];
}
```

### 3.7 프론트엔드 API

**파일**: `frontend/lib/api.ts`의 repositoriesApi에 추가

```typescript
getSyncConfig: (id: string): Promise<RepositorySyncConfig> =>
  request(`/api/repositories/${id}/sync-config`),

updateSyncConfig: (id: string, data: UpdateRepositorySyncConfigRequest): Promise<RepositorySyncConfig> =>
  request(`/api/repositories/${id}/sync-config`, {
    method: 'PUT',
    body: JSON.stringify(data),
  }),

getFolderTree: (id: string, depth: number = 4): Promise<FolderTreeItem[]> =>
  request(`/api/repositories/${id}/folder-tree?depth=${depth}`),
```

### 3.8 프론트엔드 React Query 훅

**파일**: `frontend/hooks/use-api.ts`에 추가

```typescript
// Repository Sync Config
export function useRepositorySyncConfig(repoId: string | undefined) {
  return useQuery({
    queryKey: ['repositories', repoId, 'sync-config'],
    queryFn: () => repositoriesApi.getSyncConfig(repoId!),
    enabled: !!repoId,
  });
}

export function useUpdateRepositorySyncConfig() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdateRepositorySyncConfigRequest }) =>
      repositoriesApi.updateSyncConfig(id, data),
    onSuccess: (_, { id }) => {
      void queryClient.invalidateQueries({ queryKey: ['repositories', id, 'sync-config'] });
    },
  });
}

export function useFolderTree(repoId: string | undefined, depth: number = 4) {
  return useQuery({
    queryKey: ['repositories', repoId, 'folder-tree', depth],
    queryFn: () => repositoriesApi.getFolderTree(repoId!, depth),
    enabled: !!repoId,
  });
}
```

### 3.9 프론트엔드 UI 컴포넌트

#### 3.9.1 SyncConfigDialog

**경로**: `frontend/components/sync-config/sync-config-dialog.tsx`

메인 설정 다이얼로그:
- 확장자 선택 영역 (ExtensionSelector)
- 경로 필터 영역 (PathSelector)
- OpenAPI/Swagger 토글
- 저장/취소 버튼

#### 3.9.2 ExtensionSelector

**경로**: `frontend/components/sync-config/extension-selector.tsx`

확장자 선택 컴포넌트:
- 선택된 확장자를 Badge로 표시 (X 버튼으로 제거)
- 추천 확장자 목록 (클릭하여 추가)
  - 문서: md, adoc, rst
  - 설정: yml, yaml, json, toml
  - 코드: java, py, ts, tsx, js, jsx, go, rs, kt
- 커스텀 확장자 입력 필드

#### 3.9.3 PathSelector

**경로**: `frontend/components/sync-config/path-selector.tsx`

경로 선택 컴포넌트:
- 포함/제외 모드 토글 버튼
- 폴더 트리 표시 (useFolderTree 사용)
  - 폴더 아이콘 + 이름
  - 클릭하면 선택/해제
  - 포함된 폴더는 초록색 배경
  - 제외된 폴더는 빨간색 배경
- 선택된 경로를 Badge로 표시

---

## 4. 파일 목록

### 4.1 신규 생성 파일

| 경로 | 설명 |
|------|------|
| `backend/src/main/resources/db/migration/V18__add_repository_sync_config.sql` | DB 마이그레이션 |
| `backend/src/main/java/com/docst/domain/RepositorySyncConfig.java` | 설정 Record |
| `backend/src/main/java/com/docst/service/FolderTreeService.java` | 폴더 트리 서비스 |
| `frontend/components/sync-config/sync-config-dialog.tsx` | 설정 다이얼로그 |
| `frontend/components/sync-config/extension-selector.tsx` | 확장자 선택 UI |
| `frontend/components/sync-config/path-selector.tsx` | 경로 선택 UI |
| `frontend/components/sync-config/index.ts` | 컴포넌트 export |

### 4.2 수정 파일

| 경로 | 변경 내용 |
|------|----------|
| `backend/src/main/java/com/docst/domain/Repository.java` | syncConfig 필드 추가 |
| `backend/src/main/java/com/docst/api/ApiModels.java` | DTO 3개 추가 |
| `backend/src/main/java/com/docst/git/GitFileScanner.java` | 동적 패턴/경로 필터링 메서드 추가 |
| `backend/src/main/java/com/docst/service/GitSyncService.java` | syncConfig 전달 로직 |
| `backend/src/main/java/com/docst/service/RepositoryService.java` | updateSyncConfig 메서드 추가 |
| `backend/src/main/java/com/docst/api/RepositoriesController.java` | API 3개 추가 |
| `frontend/lib/types.ts` | 타입 3개 추가 |
| `frontend/lib/api.ts` | API 함수 3개 추가 |
| `frontend/hooks/use-api.ts` | React Query 훅 3개 추가 |
| `frontend/app/[locale]/projects/[projectId]/page.tsx` | 설정 버튼 및 다이얼로그 연동 |

---

## 5. 구현 순서

### Step 1: DB 마이그레이션
1. V18 마이그레이션 파일 생성
2. 로컬 DB에서 마이그레이션 테스트

### Step 2: 백엔드 엔티티/DTO
1. RepositorySyncConfig.java 생성
2. Repository.java에 필드 추가
3. ApiModels.java에 DTO 추가

### Step 3: 백엔드 서비스
1. GitFileScanner.java 수정 (동적 패턴 지원)
2. FolderTreeService.java 생성
3. RepositoryService.java에 updateSyncConfig 추가
4. GitSyncService.java 수정 (config 전달)

### Step 4: 백엔드 API
1. RepositoriesController.java에 엔드포인트 추가
2. Postman/curl로 API 테스트

### Step 5: 프론트엔드 기반
1. types.ts에 타입 추가
2. api.ts에 API 함수 추가
3. use-api.ts에 훅 추가

### Step 6: 프론트엔드 UI
1. sync-config 컴포넌트 폴더 생성
2. ExtensionSelector 구현
3. PathSelector 구현
4. SyncConfigDialog 구현
5. 프로젝트 상세 페이지에 연동

### Step 7: 테스트 및 검증
1. E2E 시나리오 테스트
2. 엣지 케이스 확인

---

## 6. 검증 방법

### 6.1 백엔드 테스트

```bash
# 1. 마이그레이션 확인
./gradlew flywayInfo

# 2. API 테스트 - 설정 조회
curl -X GET http://localhost:8342/api/repositories/{id}/sync-config \
  -H "Authorization: Bearer {token}"

# 3. API 테스트 - 설정 수정
curl -X PUT http://localhost:8342/api/repositories/{id}/sync-config \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{
    "fileExtensions": ["md", "adoc", "java"],
    "includePaths": ["docs/", "src/"],
    "excludePaths": [".git", "node_modules", "target"]
  }'

# 4. API 테스트 - 폴더 트리
curl -X GET "http://localhost:8342/api/repositories/{id}/folder-tree?depth=3" \
  -H "Authorization: Bearer {token}"
```

### 6.2 프론트엔드 테스트

1. 레포지토리 카드에서 설정 버튼 클릭
2. SyncConfigDialog 열림 확인
3. 확장자 추가/제거 테스트
4. 경로 선택/해제 테스트
5. 저장 후 재조회 시 설정 유지 확인

### 6.3 E2E 시나리오

1. 새 레포지토리 등록 → 기본 설정(md, adoc) 확인
2. 설정 변경: 확장자에 java 추가, docs/ 경로만 포함
3. 동기화 실행
4. 결과 확인: docs/ 하위의 .md, .adoc, .java, *openapi.*, *swagger.* 파일만 동기화됨

---

## 7. 참고 사항

### 7.1 기본 확장자 목록

| 카테고리 | 확장자 | 설명 |
|----------|--------|------|
| 문서 | md, adoc, rst | 기본값 (md, adoc) |
| 설정 | yml, yaml, json, toml | 설정 파일 |
| 코드 | java, py, ts, tsx, js, jsx, go, rs, kt | 소스 코드 |
| 데이터 | sql, graphql | 스키마/쿼리 |
| 기타 | txt, csv, xml | 텍스트 데이터 |

### 7.2 기본 제외 경로

```
.git
node_modules
target
build
.gradle
dist
out
__pycache__
.venv
vendor
```

### 7.3 보안 고려사항

- **ReDoS 방지**: customPatterns에 복잡한 정규식이 들어올 경우 성능 이슈 가능
  - 해결: 패턴 길이 제한 (예: 100자) 및 timeout 적용
  - try-catch로 잘못된 패턴 무시

### 7.4 성능 고려사항

- **폴더 트리 조회**: 대형 레포지토리에서 depth 제한 필요 (기본 4, 최대 6)
- **JSONB 인덱스**: GIN 인덱스로 검색 성능 확보
