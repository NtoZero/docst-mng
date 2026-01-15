# Phase 14: RAG 시스템 파라미터 고도화 계획

> **목표**: RAG 검색 파라미터를 외부에서 동적으로 받아 처리할 수 있도록 API/MCP/UI 확장

> **핵심 원칙**: 모든 동적 파라미터는 외부(요청)에서 받음. yml 중앙 설정 사용 안 함.

---

## 1. 현황 분석

### 1.1 현재 구현 상태

#### API 인터페이스 (SearchController.java:27-53)
```java
@GetMapping
public ResponseEntity<List<SearchResultResponse>> search(
    @PathVariable UUID projectId,
    @RequestParam(name = "q") String query,
    @RequestParam String mode,              // ⚠️ 받기만 하고 미사용
    @RequestParam(required = false) Integer topK  // ⚠️ 기본값 10만 적용
)
```

**문제점**:
- `mode` 파라미터를 받지만 실제 검색 로직에서 사용하지 않음
- `topK` 외 다른 파라미터 없음 (similarity_threshold, rerank, filters 등)
- 파라미터 검증 로직 부재
- score가 하드코딩됨 (0.9 고정)
- 기본값이 코드에 하드코딩됨

#### MCP 인터페이스
- **구현 상태**: ❌ 미구현 (문서만 존재)
- **문제점**: 실제 구현이 없어 파라미터 전달 불가

#### Frontend (page.tsx)
```tsx
export default function HomePage() {
  return (
    <main>
      <h1>Docst</h1>
      <p>Phase 1 MVP scaffolding is online.</p>
    </main>
  );
}
```

**현황**:
- ✅ 기본 Next.js 페이지 존재
- ❌ 검색 UI 없음
- ❌ shadcn/ui 등 UI 라이브러리 미설치

#### 설정 관리 (application.yml)
```yaml
server:
  port: 8080
spring:
  application:
    name: docst
```

**현황**:
- ✅ 인프라 설정만 존재 (올바름)
- ⚠️ 임베딩 프로바이더 설정 필요 (인프라 수준)

---

## 2. 핵심 문제점

### 2.1 파라미터 관리의 문제

| 문제 영역 | 상세 |
|---------|------|
| **하드코딩** | score 0.9 고정, mode 미사용, 기본값 코드 내 분산 |
| **제한적 파라미터** | topK만 지원, 고급 옵션 없음 |
| **검증 로직 부재** | 잘못된 값 입력 시 오류 처리 없음 |
| **인터페이스 불일치** | API/MCP/UI 간 파라미터 명세 없음 |
| **외부 입력 제한** | 대부분의 파라미터를 외부에서 조정 불가 |

### 2.2 설계 원칙 부재

**기존 문제**:
- 파라미터 기본값을 어디에 둘지 명확하지 않음
- 중앙 설정(yml)을 사용해야 할지 불명확
- 동적 조정이 어려움

**필요한 원칙**:
1. **모든 검색 파라미터는 외부 요청에서 받음**
2. **기본값은 코드에서 처리** (메서드 파라미터 기본값)
3. **yml은 인프라 설정만** (DB, 임베딩 서버 URL 등)
4. **프리셋은 DB에 저장** (동적 관리)

---

## 3. 설계 원칙

### 3.1 파라미터 분류

```mermaid
flowchart LR
    subgraph "인프라 설정 (yml)"
        DB[DB 연결]
        OLLAMA[Ollama URL]
        PORT[서버 포트]
    end

    subgraph "검색 파라미터 (외부 요청)"
        MODE[mode]
        TOPK[topK]
        THRESHOLD[similarityThreshold]
        RERANK[rerankEnabled]
        FILTERS[filters]
    end

    subgraph "프리셋 (DB)"
        QUICK[quick-search]
        PRECISE[precise-search]
        BALANCED[balanced-search]
    end

    검색요청 --> 검색 파라미터
    프리셋 --> 검색 파라미터
    인프라 설정 --> 서비스구현
    검색 파라미터 --> 서비스구현
```

**원칙**:
- **인프라 설정**: 환경 변수 또는 yml (서버 재시작 필요)
- **검색 파라미터**: 모두 요청에서 받음 (동적 조정)
- **프리셋**: DB에 저장 (런타임 관리)

### 3.2 기본값 처리 전략

```java
// ❌ 잘못된 방법: yml에 기본값
// application.yml:
//   search.default-top-k: 10

// ✅ 올바른 방법: 코드에서 기본값
public record SearchParameters(
    UUID projectId,
    String query,
    String mode,
    Integer topK,
    Double similarityThreshold,
    Boolean rerankEnabled
) {
    // 생성자에서 기본값 처리
    public SearchParameters {
        if (mode == null) mode = "hybrid";
        if (topK == null) topK = 10;
        if (similarityThreshold == null) similarityThreshold = 0.7;
        if (rerankEnabled == null) rerankEnabled = false;
    }
}
```

**장점**:
- 코드에서 기본값이 명확함
- yml 수정 없이 동적 조정 가능
- 테스트하기 쉬움
- 환경별로 다른 설정 불필요

---

## 4. 구현 계획

### Sprint 14-1: 파라미터 DTO 및 검증 (3일) ⭐⭐⭐

#### 4.1.1 SearchParameters DTO
**파일**: `backend/src/main/java/com/docst/api/SearchParameters.java`

```java
package com.docst.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.*;

/**
 * 검색 파라미터 DTO
 *
 * 원칙:
 * - 모든 파라미터는 외부 요청에서 받음
 * - 기본값은 생성자에서 처리
 * - yml 설정 의존 없음
 */
public record SearchParameters(
    @NotNull UUID projectId,

    @NotBlank @Size(min = 1, max = 500)
    String query,

    @Pattern(regexp = "keyword|semantic|hybrid")
    String mode,

    @Min(1) @Max(100)
    Integer topK,

    @Min(0) @Max(1)
    Double similarityThreshold,

    Boolean rerankEnabled,

    @Min(1) @Max(20)
    Integer rerankTopK,

    // 필터링
    List<@Pattern(regexp = "MD|ADOC|OPENAPI|ADR") String> docTypes,
    List<UUID> repositoryIds,
    String pathPrefix,

    // 응답 옵션
    Boolean includeContent,
    Boolean includeMetadata,

    // 하이브리드 가중치
    @Min(0) @Max(1) Double keywordWeight,
    @Min(0) @Max(1) Double semanticWeight
) {
    // 기본값 생성자
    public SearchParameters {
        // 필수 파라미터 기본값
        if (mode == null) mode = "hybrid";
        if (topK == null) topK = 10;
        if (similarityThreshold == null) similarityThreshold = 0.7;

        // 선택 파라미터 기본값
        if (rerankEnabled == null) rerankEnabled = false;
        if (rerankTopK == null) rerankTopK = 5;
        if (includeContent == null) includeContent = false;
        if (includeMetadata == null) includeMetadata = false;

        // 하이브리드 가중치
        if (keywordWeight == null && semanticWeight == null) {
            keywordWeight = 0.5;
            semanticWeight = 0.5;
        }
    }

    // 검증 메서드
    public void validate() {
        // 가중치 합 검증
        if (keywordWeight != null && semanticWeight != null) {
            double sum = keywordWeight + semanticWeight;
            if (Math.abs(sum - 1.0) > 0.01) {
                throw new IllegalArgumentException(
                    "keywordWeight + semanticWeight must equal 1.0, got: " + sum
                );
            }
        }

        // 필터 개수 검증
        if (docTypes != null && docTypes.size() > 10) {
            throw new IllegalArgumentException(
                "Cannot filter by more than 10 document types"
            );
        }

        if (repositoryIds != null && repositoryIds.size() > 50) {
            throw new IllegalArgumentException(
                "Cannot filter by more than 50 repositories"
            );
        }
    }

    // 빌더 패턴 (테스트 및 프리셋에서 사용)
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UUID projectId;
        private String query;
        private String mode = "hybrid";
        private Integer topK = 10;
        private Double similarityThreshold = 0.7;
        private Boolean rerankEnabled = false;
        private Integer rerankTopK = 5;
        private List<String> docTypes;
        private List<UUID> repositoryIds;
        private String pathPrefix;
        private Boolean includeContent = false;
        private Boolean includeMetadata = false;
        private Double keywordWeight = 0.5;
        private Double semanticWeight = 0.5;

        public Builder projectId(UUID projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder query(String query) {
            this.query = query;
            return this;
        }

        public Builder mode(String mode) {
            this.mode = mode;
            return this;
        }

        public Builder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        // ... 나머지 setter들

        public SearchParameters build() {
            return new SearchParameters(
                projectId, query, mode, topK, similarityThreshold,
                rerankEnabled, rerankTopK, docTypes, repositoryIds, pathPrefix,
                includeContent, includeMetadata, keywordWeight, semanticWeight
            );
        }
    }
}
```

#### 4.1.2 API 요청/응답 모델
**파일**: `backend/src/main/java/com/docst/api/ApiModels.java` (추가)

```java
// 기존 SearchResultResponse 확장
public record SearchResultResponse(
    UUID documentId,
    UUID repositoryId,
    String path,
    String commitSha,
    UUID chunkId,
    String headingPath,        // NEW: 청크 경로
    double score,
    String snippet,
    String highlightedSnippet,
    String content             // includeContent=true일 때만
) {}

// 검색 응답 (메타데이터 포함)
public record SearchResponse(
    List<SearchResultResponse> results,
    SearchMetadata metadata
) {}

public record SearchMetadata(
    String mode,
    int totalResults,
    long queryTimeMs,
    SearchParameters usedParameters  // 실제 사용된 파라미터 반환
) {}
```

---

### Sprint 14-2: API 확장 (3일) ⭐⭐⭐

#### 4.2.1 SearchController 개선
**파일**: `backend/src/main/java/com/docst/api/SearchController.java`

```java
package com.docst.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.docst.service.SearchService;

@RestController
@RequestMapping("/api/projects/{projectId}/search")
public class SearchController {
    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * 검색 API
     *
     * 모든 파라미터는 쿼리 파라미터로 받음
     * 기본값은 SearchParameters 생성자에서 처리
     */
    @GetMapping
    public ResponseEntity<SearchResponse> search(
        @PathVariable UUID projectId,

        // 필수 파라미터
        @RequestParam(name = "q") String query,

        // 선택 파라미터 (기본값은 DTO에서)
        @RequestParam(required = false) String mode,
        @RequestParam(required = false) Integer topK,
        @RequestParam(required = false) Double similarityThreshold,
        @RequestParam(required = false) Boolean rerankEnabled,
        @RequestParam(required = false) Integer rerankTopK,

        // 필터
        @RequestParam(required = false) List<String> docTypes,
        @RequestParam(required = false) List<UUID> repositoryIds,
        @RequestParam(required = false) String pathPrefix,

        // 응답 옵션
        @RequestParam(required = false) Boolean includeContent,
        @RequestParam(required = false) Boolean includeMetadata,

        // 하이브리드 가중치
        @RequestParam(required = false) Double keywordWeight,
        @RequestParam(required = false) Double semanticWeight
    ) {
        // 파라미터 객체 생성 (기본값 자동 적용)
        SearchParameters params = new SearchParameters(
            projectId, query, mode, topK, similarityThreshold,
            rerankEnabled, rerankTopK, docTypes, repositoryIds, pathPrefix,
            includeContent, includeMetadata, keywordWeight, semanticWeight
        );

        // 검증
        params.validate();

        // 검색 실행
        long startTime = System.currentTimeMillis();
        List<SearchResultResponse> results = searchService.search(params);
        long queryTime = System.currentTimeMillis() - startTime;

        // 메타데이터 포함 응답
        SearchMetadata metadata = new SearchMetadata(
            params.mode(),
            results.size(),
            queryTime,
            params  // 실제 사용된 파라미터 반환
        );

        return ResponseEntity.ok(new SearchResponse(results, metadata));
    }

    /**
     * 파라미터 스키마 조회 API
     *
     * 클라이언트가 사용 가능한 파라미터와 기본값을 알 수 있음
     */
    @GetMapping("/schema")
    public ResponseEntity<SearchParameterSchema> getParameterSchema() {
        return ResponseEntity.ok(new SearchParameterSchema(
            List.of(
                new ParameterInfo("mode", "string", "hybrid",
                    List.of("keyword", "semantic", "hybrid"),
                    "검색 모드"),
                new ParameterInfo("topK", "integer", 10,
                    new Range(1, 100),
                    "반환 결과 개수"),
                new ParameterInfo("similarityThreshold", "number", 0.7,
                    new Range(0.0, 1.0),
                    "의미 검색 유사도 임계값"),
                new ParameterInfo("rerankEnabled", "boolean", false,
                    null,
                    "재순위화 활성화"),
                new ParameterInfo("keywordWeight", "number", 0.5,
                    new Range(0.0, 1.0),
                    "키워드 검색 가중치 (하이브리드 모드)"),
                new ParameterInfo("semanticWeight", "number", 0.5,
                    new Range(0.0, 1.0),
                    "의미 검색 가중치 (하이브리드 모드)")
                // ... 나머지 파라미터들
            )
        ));
    }
}

record SearchParameterSchema(List<ParameterInfo> parameters) {}

record ParameterInfo(
    String name,
    String type,
    Object defaultValue,
    Object constraint,  // Range 또는 List<String>
    String description
) {}

record Range(Number min, Number max) {}
```

#### 4.2.2 application.yml (인프라만)
**파일**: `backend/src/main/resources/application.yml`

```yaml
# 인프라 설정만 포함
server:
  port: 8080

spring:
  application:
    name: docst

  # Phase 2에서 추가될 DB 설정
  # datasource:
  #   url: jdbc:postgresql://postgres:5432/docst
  #   username: docst
  #   password: ${DB_PASSWORD}

# 임베딩 프로바이더 설정 (인프라 수준)
docst:
  embedding:
    provider: ${EMBEDDING_PROVIDER:ollama}  # openai, ollama, local

  ollama:
    base-url: ${OLLAMA_URL:http://localhost:11434}

  openai:
    api-key: ${OPENAI_API_KEY:}

# 주의: 검색 파라미터 기본값은 여기에 두지 않음!
# 모든 검색 파라미터는 요청에서 받거나 코드에서 기본값 처리
```

---

### Sprint 14-3: DB 기반 프리셋 시스템 (4일) ⭐⭐

#### 4.3.1 SearchPreset 엔티티
**파일**: `backend/src/main/java/com/docst/domain/SearchPreset.java`

```java
package com.docst.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.*;

@Entity
@Table(name = "dm_search_preset")
public class SearchPreset {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_system", nullable = false)
    private boolean isSystem;

    @Column(name = "user_id")
    private UUID userId;

    // 검색 파라미터 (JSON으로 저장하거나 개별 컬럼)
    @Column(nullable = false)
    private String mode;

    @Column(name = "top_k", nullable = false)
    private int topK;

    @Column(name = "similarity_threshold")
    private Double similarityThreshold;

    @Column(name = "rerank_enabled")
    private Boolean rerankEnabled;

    @Column(name = "keyword_weight")
    private Double keywordWeight;

    @Column(name = "semantic_weight")
    private Double semanticWeight;

    @ElementCollection
    @CollectionTable(name = "dm_search_preset_doc_types")
    private List<String> docTypes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    // SearchParameters 변환
    public SearchParameters toSearchParameters(UUID projectId, String query) {
        return SearchParameters.builder()
            .projectId(projectId)
            .query(query)
            .mode(mode)
            .topK(topK)
            .similarityThreshold(similarityThreshold)
            .rerankEnabled(rerankEnabled)
            .keywordWeight(keywordWeight)
            .semanticWeight(semanticWeight)
            .docTypes(docTypes)
            .build();
    }

    // getters, setters, constructors
}
```

#### 4.3.2 시스템 프리셋 초기화
**파일**: `backend/src/main/java/com/docst/config/SystemPresetInitializer.java`

```java
package com.docst.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.docst.domain.SearchPreset;
import com.docst.repository.SearchPresetRepository;

@Component
public class SystemPresetInitializer implements CommandLineRunner {

    private final SearchPresetRepository repository;

    public SystemPresetInitializer(SearchPresetRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        initializeSystemPresets();
    }

    private void initializeSystemPresets() {
        // 1. 빠른 검색 (키워드)
        createIfNotExists("quick-search",
            "빠른 키워드 검색 (성능 우선)",
            "keyword", 10, null, false, null, null);

        // 2. 정확한 검색 (의미)
        createIfNotExists("precise-search",
            "정확한 의미 기반 검색 (관련성 우선)",
            "semantic", 10, 0.8, false, null, null);

        // 3. 균형 검색 (하이브리드)
        createIfNotExists("balanced-search",
            "균형잡힌 하이브리드 검색 (기본 권장)",
            "hybrid", 10, 0.7, false, 0.5, 0.5);

        // 4. 포괄적 검색
        createIfNotExists("broad-search",
            "포괄적 검색 (많은 결과)",
            "hybrid", 50, 0.5, false, 0.3, 0.7);

        // 5. 정밀 검색 (rerank)
        createIfNotExists("refined-search",
            "정밀 검색 (재순위화 포함, 느림)",
            "hybrid", 20, 0.7, true, 0.5, 0.5);
    }

    private void createIfNotExists(
        String name, String description, String mode, int topK,
        Double similarityThreshold, Boolean rerankEnabled,
        Double keywordWeight, Double semanticWeight
    ) {
        if (!repository.existsByNameAndIsSystemTrue(name)) {
            SearchPreset preset = new SearchPreset();
            preset.setName(name);
            preset.setDescription(description);
            preset.setSystem(true);
            preset.setMode(mode);
            preset.setTopK(topK);
            preset.setSimilarityThreshold(similarityThreshold);
            preset.setRerankEnabled(rerankEnabled);
            preset.setKeywordWeight(keywordWeight);
            preset.setSemanticWeight(semanticWeight);
            preset.setCreatedAt(Instant.now());

            repository.save(preset);
        }
    }
}
```

#### 4.3.3 프리셋 API
**파일**: `backend/src/main/java/com/docst/api/PresetController.java`

```java
package com.docst.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.docst.domain.SearchPreset;
import com.docst.repository.SearchPresetRepository;
import com.docst.service.SearchService;

@RestController
@RequestMapping("/api/search/presets")
public class PresetController {
    private final SearchPresetRepository presetRepository;
    private final SearchService searchService;

    public PresetController(
        SearchPresetRepository presetRepository,
        SearchService searchService
    ) {
        this.presetRepository = presetRepository;
        this.searchService = searchService;
    }

    // 시스템 프리셋 목록
    @GetMapping("/system")
    public ResponseEntity<List<SearchPreset>> getSystemPresets() {
        return ResponseEntity.ok(presetRepository.findByIsSystemTrue());
    }

    // 프리셋으로 검색 (프리셋 파라미터 적용)
    @GetMapping("/{presetId}/search")
    public ResponseEntity<SearchResponse> searchWithPreset(
        @PathVariable UUID presetId,
        @RequestParam UUID projectId,
        @RequestParam String query,

        // 프리셋 오버라이드 (선택)
        @RequestParam(required = false) Integer topK,
        @RequestParam(required = false) Boolean includeContent
    ) {
        SearchPreset preset = presetRepository.findById(presetId)
            .orElseThrow(() -> new IllegalArgumentException("Preset not found"));

        // 프리셋 파라미터 사용
        SearchParameters params = preset.toSearchParameters(projectId, query);

        // 개별 파라미터 오버라이드
        if (topK != null) {
            params = SearchParameters.builder()
                .from(params)
                .topK(topK)
                .build();
        }
        if (includeContent != null) {
            params = SearchParameters.builder()
                .from(params)
                .includeContent(includeContent)
                .build();
        }

        // 검색 실행
        List<SearchResultResponse> results = searchService.search(params);

        return ResponseEntity.ok(new SearchResponse(
            results,
            new SearchMetadata(params.mode(), results.size(), 0L, params)
        ));
    }
}
```

#### 4.3.4 DB 마이그레이션
**파일**: `backend/src/main/resources/db/migration/V6__add_search_preset.sql`

```sql
-- 검색 프리셋 테이블
CREATE TABLE dm_search_preset (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name text NOT NULL UNIQUE,
    description text,
    is_system boolean NOT NULL DEFAULT false,
    user_id uuid REFERENCES dm_user(id) ON DELETE CASCADE,

    -- 검색 파라미터
    mode text NOT NULL CHECK (mode IN ('keyword', 'semantic', 'hybrid')),
    top_k integer NOT NULL DEFAULT 10 CHECK (top_k >= 1 AND top_k <= 100),
    similarity_threshold numeric(3, 2) CHECK (similarity_threshold >= 0 AND similarity_threshold <= 1),
    rerank_enabled boolean DEFAULT false,
    keyword_weight numeric(3, 2) CHECK (keyword_weight >= 0 AND keyword_weight <= 1),
    semantic_weight numeric(3, 2) CHECK (semantic_weight >= 0 AND semantic_weight <= 1),

    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz,

    CHECK (
        (is_system = true AND user_id IS NULL) OR
        (is_system = false AND user_id IS NOT NULL)
    )
);

CREATE INDEX idx_preset_system ON dm_search_preset(is_system);
CREATE INDEX idx_preset_user ON dm_search_preset(user_id) WHERE user_id IS NOT NULL;

-- 프리셋별 문서 타입 필터
CREATE TABLE dm_search_preset_doc_types (
    search_preset_id uuid NOT NULL REFERENCES dm_search_preset(id) ON DELETE CASCADE,
    doc_type text NOT NULL CHECK (doc_type IN ('MD', 'ADOC', 'OPENAPI', 'ADR')),
    PRIMARY KEY (search_preset_id, doc_type)
);
```

---

### Sprint 14-4: Frontend 검색 UI 추가 (5일) ⭐⭐

#### 4.4.1 shadcn/ui 설치
```bash
cd frontend
npx shadcn@latest init
npx shadcn@latest add button input select slider switch card label
```

#### 4.4.2 검색 페이지
**파일**: `frontend/app/search/page.tsx`

```tsx
'use client'

import { useState } from 'react'
import { SearchBar } from '@/components/search/SearchBar'
import { SearchResults } from '@/components/search/SearchResults'
import { PresetSelector } from '@/components/search/PresetSelector'

export default function SearchPage() {
  const [results, setResults] = useState([])
  const [loading, setLoading] = useState(false)

  const handleSearch = async (params: any) => {
    setLoading(true)
    try {
      const queryParams = new URLSearchParams()
      queryParams.append('q', params.query)
      if (params.mode) queryParams.append('mode', params.mode)
      if (params.topK) queryParams.append('topK', params.topK.toString())
      if (params.similarityThreshold)
        queryParams.append('similarityThreshold', params.similarityThreshold.toString())
      // ... 나머지 파라미터

      const response = await fetch(
        `/api/projects/${params.projectId}/search?${queryParams}`
      )
      const data = await response.json()
      setResults(data.results)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="container mx-auto p-8">
      <h1 className="text-3xl font-bold mb-8">문서 검색</h1>

      <PresetSelector onSelectPreset={(preset) => {
        // 프리셋 파라미터로 검색
      }} />

      <SearchBar onSearch={handleSearch} loading={loading} />

      <SearchResults results={results} />
    </div>
  )
}
```

#### 4.4.3 고급 검색바
**파일**: `frontend/components/search/SearchBar.tsx`

```tsx
'use client'

import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Slider } from '@/components/ui/slider'
import { Switch } from '@/components/ui/switch'
import { Label } from '@/components/ui/label'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
import { ChevronDown, Search, Settings2 } from 'lucide-react'

interface SearchBarProps {
  onSearch: (params: any) => void
  loading?: boolean
}

export function SearchBar({ onSearch, loading }: SearchBarProps) {
  // 기본값은 백엔드와 동일하게 설정
  const [params, setParams] = useState({
    query: '',
    mode: 'hybrid',
    topK: 10,
    similarityThreshold: 0.7,
    rerankEnabled: false,
    keywordWeight: 0.5,
    semanticWeight: 0.5,
  })

  const [showAdvanced, setShowAdvanced] = useState(false)

  const handleSearch = () => {
    onSearch(params)
  }

  return (
    <div className="space-y-4">
      {/* 기본 검색 */}
      <div className="flex gap-2">
        <Input
          placeholder="문서 검색..."
          value={params.query}
          onChange={(e) => setParams({ ...params, query: e.target.value })}
          onKeyPress={(e) => e.key === 'Enter' && handleSearch()}
          className="flex-1"
        />

        <Select
          value={params.mode}
          onValueChange={(mode) => setParams({ ...params, mode })}
        >
          <SelectTrigger className="w-[180px]">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="keyword">키워드</SelectItem>
            <SelectItem value="semantic">의미 기반</SelectItem>
            <SelectItem value="hybrid">하이브리드 ⭐</SelectItem>
          </SelectContent>
        </Select>

        <Button onClick={handleSearch} disabled={loading}>
          <Search className="h-4 w-4 mr-2" />
          검색
        </Button>
      </div>

      {/* 고급 옵션 */}
      <Collapsible open={showAdvanced} onOpenChange={setShowAdvanced}>
        <CollapsibleTrigger asChild>
          <Button variant="ghost" size="sm" className="w-full">
            <Settings2 className="h-4 w-4 mr-2" />
            고급 옵션
            <ChevronDown className={`h-4 w-4 ml-2 transition-transform ${showAdvanced ? 'rotate-180' : ''}`} />
          </Button>
        </CollapsibleTrigger>

        <CollapsibleContent className="space-y-4 pt-4">
          {/* Top K */}
          <div className="space-y-2">
            <div className="flex justify-between">
              <Label>결과 개수</Label>
              <span className="text-sm text-muted-foreground">{params.topK}</span>
            </div>
            <Slider
              value={[params.topK]}
              onValueChange={([value]) => setParams({ ...params, topK: value })}
              min={1}
              max={100}
              step={1}
            />
          </div>

          {/* Similarity Threshold */}
          {(params.mode === 'semantic' || params.mode === 'hybrid') && (
            <div className="space-y-2">
              <div className="flex justify-between">
                <Label>유사도 임계값</Label>
                <span className="text-sm text-muted-foreground">
                  {params.similarityThreshold.toFixed(2)}
                </span>
              </div>
              <Slider
                value={[params.similarityThreshold]}
                onValueChange={([value]) =>
                  setParams({ ...params, similarityThreshold: value })
                }
                min={0}
                max={1}
                step={0.05}
              />
            </div>
          )}

          {/* Rerank */}
          <div className="flex items-center justify-between">
            <div className="space-y-0.5">
              <Label>재순위화</Label>
              <p className="text-xs text-muted-foreground">더 정확하지만 느림</p>
            </div>
            <Switch
              checked={params.rerankEnabled}
              onCheckedChange={(checked) =>
                setParams({ ...params, rerankEnabled: checked })
              }
            />
          </div>
        </CollapsibleContent>
      </Collapsible>

      {/* 현재 파라미터 표시 */}
      <div className="text-xs text-muted-foreground">
        현재 설정: {params.mode} 모드, 상위 {params.topK}개 결과
        {params.rerankEnabled && ', 재순위화 활성'}
      </div>
    </div>
  )
}
```

#### 4.4.4 프리셋 선택기
**파일**: `frontend/components/search/PresetSelector.tsx`

```tsx
'use client'

import { useEffect, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'

export function PresetSelector({ onSelectPreset }: any) {
  const [presets, setPresets] = useState([])

  useEffect(() => {
    fetch('/api/search/presets/system')
      .then(res => res.json())
      .then(setPresets)
  }, [])

  return (
    <div className="mb-4">
      <div className="text-sm text-muted-foreground mb-2">빠른 프리셋:</div>
      <div className="flex gap-2 flex-wrap">
        {presets.map((preset: any) => (
          <Button
            key={preset.id}
            variant="outline"
            size="sm"
            onClick={() => onSelectPreset(preset)}
          >
            {preset.name}
            <Badge variant="secondary" className="ml-2">
              {preset.mode}
            </Badge>
          </Button>
        ))}
      </div>
    </div>
  )
}
```

---

### Sprint 14-5: MCP 파라미터 지원 (3일) ⭐⭐

#### 4.5.1 MCP search_documents Tool
**파일**: `backend/src/main/java/com/docst/mcp/McpSearchTool.java`

```java
package com.docst.mcp;

import java.util.*;

import org.springframework.stereotype.Component;

import com.docst.api.SearchParameters;
import com.docst.service.SearchService;

@Component
public class McpSearchTool implements McpTool {

    private final SearchService searchService;

    public McpSearchTool(SearchService searchService) {
        this.searchService = searchService;
    }

    @Override
    public ToolDefinition getDefinition() {
        return new ToolDefinition(
            "search_documents",
            "프로젝트 문서 검색 (모든 파라미터 동적으로 받음)",
            Map.of(
                "type", "object",
                "required", List.of("projectId", "query"),
                "properties", Map.of(
                    "projectId", Map.of("type", "string", "format", "uuid"),
                    "query", Map.of("type", "string", "minLength", 1, "maxLength", 500),

                    // 모든 파라미터 외부에서 받음
                    "mode", Map.of(
                        "type", "string",
                        "enum", List.of("keyword", "semantic", "hybrid"),
                        "default", "hybrid",
                        "description", "검색 모드"
                    ),
                    "topK", Map.of(
                        "type", "integer",
                        "minimum", 1,
                        "maximum", 100,
                        "default", 10,
                        "description", "반환 결과 개수"
                    ),
                    "similarityThreshold", Map.of(
                        "type", "number",
                        "minimum", 0.0,
                        "maximum", 1.0,
                        "default", 0.7,
                        "description", "의미 검색 유사도 임계값"
                    ),
                    "rerankEnabled", Map.of(
                        "type", "boolean",
                        "default", false,
                        "description", "재순위화 활성화"
                    ),
                    "includeContent", Map.of(
                        "type", "boolean",
                        "default", false
                    ),

                    // 프리셋 지원
                    "presetId", Map.of(
                        "type", "string",
                        "format", "uuid",
                        "description", "프리셋 ID (지정 시 다른 파라미터 무시)"
                    ),

                    // 필터
                    "docTypes", Map.of(
                        "type", "array",
                        "items", Map.of("type", "string", "enum", List.of("MD", "ADOC", "OPENAPI", "ADR"))
                    ),
                    "repositoryIds", Map.of(
                        "type", "array",
                        "items", Map.of("type", "string", "format", "uuid")
                    )
                )
            )
        );
    }

    @Override
    public Object execute(Map<String, Object> arguments) {
        // 프리셋 사용 시
        if (arguments.containsKey("presetId")) {
            UUID presetId = UUID.fromString((String) arguments.get("presetId"));
            // ... 프리셋 로직
        }

        // 일반 파라미터 (외부에서 모두 받음, 기본값은 SearchParameters에서)
        SearchParameters params = new SearchParameters(
            UUID.fromString((String) arguments.get("projectId")),
            (String) arguments.get("query"),
            (String) arguments.get("mode"),  // null이면 기본값 적용
            (Integer) arguments.get("topK"),
            (Double) arguments.get("similarityThreshold"),
            (Boolean) arguments.get("rerankEnabled"),
            null,  // rerankTopK
            (List<String>) arguments.get("docTypes"),
            (List<UUID>) arguments.get("repositoryIds"),
            (String) arguments.get("pathPrefix"),
            (Boolean) arguments.get("includeContent"),
            true,  // includeMetadata
            (Double) arguments.get("keywordWeight"),
            (Double) arguments.get("semanticWeight")
        );

        // 검증
        params.validate();

        // 검색
        var results = searchService.search(params);

        return Map.of(
            "results", results,
            "metadata", Map.of(
                "mode", params.mode(),
                "totalResults", results.size(),
                "usedParameters", params  // 실제 사용된 파라미터 반환
            )
        );
    }
}
```

---

### Sprint 14-6: 실험 모드 (4일) ⭐

#### 4.6.1 실험 서비스
**파일**: `backend/src/main/java/com/docst/service/SearchExperimentService.java`

```java
package com.docst.service;

import java.util.*;
import java.util.concurrent.*;

import org.springframework.stereotype.Service;

import com.docst.api.SearchParameters;

@Service
public class SearchExperimentService {

    private final SearchService searchService;
    private final ExecutorService executor = Executors.newFixedThreadPool(5);

    public SearchExperimentService(SearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * 여러 파라미터 조합을 동시에 테스트
     *
     * 각 variant는 완전히 동적으로 외부에서 정의됨
     */
    public ExperimentResult runExperiment(
        UUID projectId,
        String query,
        List<SearchParameters> variants
    ) {
        if (variants.size() > 5) {
            throw new IllegalArgumentException("최대 5개 variant만 지원");
        }

        List<CompletableFuture<VariantResult>> futures = variants.stream()
            .map(params -> CompletableFuture.supplyAsync(() -> {
                long start = System.currentTimeMillis();
                var results = searchService.search(params);
                long duration = System.currentTimeMillis() - start;

                return new VariantResult(
                    params,
                    results,
                    duration,
                    calculateMetrics(results)
                );
            }, executor))
            .toList();

        List<VariantResult> results = futures.stream()
            .map(CompletableFuture::join)
            .toList();

        return new ExperimentResult(query, results);
    }

    private SearchMetrics calculateMetrics(List<?> results) {
        // ... 메트릭 계산
        return new SearchMetrics(results.size(), 0.0, 0.0, 0.0);
    }
}

record ExperimentResult(String query, List<VariantResult> variants) {}

record VariantResult(
    SearchParameters parameters,
    List<?> results,
    long durationMs,
    SearchMetrics metrics
) {}

record SearchMetrics(int total, double avgScore, double maxScore, double minScore) {}
```

---

## 5. 완료 기준 (Definition of Done)

### 5.1 기능 요구사항

- [ ] SearchParameters DTO 구현 (기본값 처리)
- [ ] API에서 12개 파라미터 모두 외부에서 받음
- [ ] 파라미터 스키마 API (`/search/schema`)
- [ ] DB 기반 프리셋 시스템 (시스템 프리셋 5개)
- [ ] Frontend 검색 UI 구현
- [ ] MCP에서 모든 파라미터 지원
- [ ] 실험 모드 API

### 5.2 아키텍처 요구사항

- [ ] **yml에 검색 파라미터 기본값 없음** ✅
- [ ] 모든 파라미터는 요청에서 받음 ✅
- [ ] 기본값은 코드에서만 처리 ✅
- [ ] 프리셋은 DB에만 저장 ✅

### 5.3 테스트

- [ ] 파라미터 기본값 테스트
- [ ] 파라미터 검증 테스트
- [ ] 프리셋 변환 테스트
- [ ] API 파라미터 바인딩 테스트

---

## 6. 파일 구조

```
backend/
├── src/main/java/com/docst/
│   ├── api/
│   │   ├── SearchParameters.java         # NEW: 파라미터 DTO (기본값 포함)
│   │   ├── SearchController.java         # MODIFIED: 모든 파라미터 받음
│   │   ├── PresetController.java         # NEW
│   │   ├── ExperimentController.java     # NEW
│   │   └── ApiModels.java                # MODIFIED
│   ├── domain/
│   │   └── SearchPreset.java             # NEW
│   ├── repository/
│   │   └── SearchPresetRepository.java   # NEW
│   ├── service/
│   │   └── SearchExperimentService.java  # NEW
│   ├── config/
│   │   └── SystemPresetInitializer.java  # NEW
│   └── mcp/
│       └── McpSearchTool.java            # NEW
├── src/main/resources/
│   ├── application.yml                    # 인프라 설정만
│   └── db/migration/
│       └── V6__add_search_preset.sql     # NEW

frontend/
├── app/
│   └── search/
│       └── page.tsx                      # NEW
├── components/search/
│   ├── SearchBar.tsx                     # NEW
│   ├── SearchResults.tsx                 # NEW
│   └── PresetSelector.tsx                # NEW
└── package.json                          # MODIFIED: shadcn/ui 추가
```

---

## 7. 구현 일정

| Sprint | 기간 | 태스크 | 우선순위 |
|--------|------|--------|---------|
| **14-1** | 3일 | 파라미터 DTO 및 검증 | ⭐⭐⭐ |
| **14-2** | 3일 | API 확장 (모든 파라미터) | ⭐⭐⭐ |
| **14-3** | 4일 | DB 기반 프리셋 시스템 | ⭐⭐ |
| **14-4** | 5일 | Frontend 검색 UI | ⭐⭐ |
| **14-5** | 3일 | MCP 파라미터 지원 | ⭐⭐ |
| **14-6** | 4일 | 실험 모드 | ⭐ |

**총 예상 기간**: 22일

---

## 8. 핵심 설계 원칙 요약

### ✅ 올바른 접근

1. **모든 검색 파라미터는 외부 요청에서 받음**
   ```java
   @GetMapping
   public ResponseEntity<SearchResponse> search(
       @RequestParam(required = false) String mode,
       @RequestParam(required = false) Integer topK,
       // ... 모든 파라미터
   )
   ```

2. **기본값은 DTO 생성자에서 처리**
   ```java
   public SearchParameters {
       if (mode == null) mode = "hybrid";
       if (topK == null) topK = 10;
   }
   ```

3. **yml은 인프라 설정만**
   ```yaml
   docst:
     embedding:
       provider: ollama
     ollama:
       base-url: http://localhost:11434
   ```

4. **프리셋은 DB에 저장**
   ```java
   @Component
   public class SystemPresetInitializer {
       // DB에 저장
   }
   ```

### ❌ 피해야 할 접근

1. **yml에 검색 파라미터 기본값** ❌
   ```yaml
   # 이렇게 하지 말 것!
   docst:
     search:
       default-top-k: 10
       default-mode: hybrid
   ```

2. **중앙 설정 클래스** ❌
   ```java
   // 이렇게 하지 말 것!
   @ConfigurationProperties("docst.search")
   public class SearchProperties {
       private int defaultTopK = 10;
   }
   ```

---

## 9. 참고 자료

- [Spring @RequestParam](https://docs.spring.io/spring-framework/docs/current/reference/html/web.html#mvc-ann-requestparam)
- [Bean Validation](https://beanvalidation.org/)
- [Record Pattern (Java 21)](https://openjdk.org/jeps/440)
- [shadcn/ui](https://ui.shadcn.com/)
