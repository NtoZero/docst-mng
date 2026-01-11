# Phase 4: Graph RAG & Hybrid RAG 구현 계획 (v2)

> **작성일**: 2025-12-29 (v2: 2025-12-30)
> **기반 계획서**: `docs/plan/phase-4-flexible-rag-architecture.md`
> **목표**: Neo4j Graph RAG 및 Hybrid RAG를 섹션 6(동적 전략 선택)까지 구현 + **동적 설정 시스템**

---

## 버전 이력

| 버전 | 날짜 | 변경 내용 |
|------|------|-----------|
| v1 | 2025-12-29 | 초기 계획 (4-A ~ 4-C) |
| **v2** | **2025-12-30** | **Phase 4-D 추가: yml 기반 설정 → UI 동적 설정으로 전환** |

---

## 구현 범위

- ✅ Phase 4-A: 기반 구조 (RagMode, RagSearchStrategy)
- ✅ Phase 4-B: Mode 1 리팩토링 (PgVectorSearchStrategy)
- ✅ Phase 4-C: Mode 2 - Neo4j Graph RAG
- 🆕 **Phase 4-D: 동적 RAG 설정 시스템**
- ⏳ Phase 4-E: 동적 전략 선택 (QueryRouter) - 추후

---

## 설계 결정 사항 (v2)

| 항목 | 결정 | 이유 |
|------|------|------|
| 설정 방식 | yml → **프로젝트별 JSONB** | UI에서 동적 설정 가능 |
| 임베딩 모델 변경 | **변경 시 재임베딩** | 유연성 확보, 차원 불일치 방지 |
| Neo4j 접속정보 | **단일 인스턴스** | projectId로 데이터 분리, 구현 간단 |
| 구현 범위 | **백엔드 API만** | 프론트엔드는 별도 Phase |

---

## Phase 4-D: 동적 RAG 설정 시스템

### 변경 개요

**Before (v1)**
```yaml
# application.yml - 정적 설정
docst:
  rag:
    neo4j:
      max-hop: 2
      entity-extraction-model: gpt-4o-mini
    hybrid:
      fusion-strategy: rrf
      rrf-k: 60
```

**After (v2)**
```java
// Project.ragConfig (JSONB) - 프로젝트별 동적 설정
// 설정 우선순위: 요청 파라미터 > 프로젝트 설정 > 전역 기본값
ResolvedRagConfig config = ragConfigService.resolve(project, requestParams);
```

---

### 1. RagConfig JSONB 스키마

```json
{
  "version": "1.0",
  "embedding": {
    "provider": "openai",
    "model": "text-embedding-3-small",
    "dimensions": 1536
  },
  "pgvector": {
    "enabled": true,
    "similarityThreshold": 0.5
  },
  "neo4j": {
    "enabled": false,
    "maxHop": 2,
    "entityExtractionModel": "gpt-4o-mini"
  },
  "hybrid": {
    "fusionStrategy": "rrf",
    "rrfK": 60,
    "vectorWeight": 0.6,
    "graphWeight": 0.4
  }
}
```

---

### 2. 신규 파일 목록 (9개)

#### 2.1 설정 관련
```
backend/src/main/java/com/docst/rag/config/
├── RagConfigDto.java              # JSONB 매핑 DTO (record)
├── ResolvedRagConfig.java         # 최종 해결된 설정 (builder)
├── RagGlobalProperties.java       # yml 전역 설정 (@ConfigurationProperties)
└── RagConfigService.java          # 설정 해결 서비스 (우선순위 처리)
```

#### 2.2 Fusion 전략 패턴
```
backend/src/main/java/com/docst/rag/hybrid/
├── FusionStrategy.java            # 융합 전략 인터페이스
├── FusionParams.java              # 융합 파라미터 record
├── RrfFusionStrategy.java         # RRF 구현 (기존 로직 이동)
└── WeightedSumFusionStrategy.java # Weighted Sum 구현 (신규)
```

#### 2.3 API
```
backend/src/main/java/com/docst/api/
└── ProjectRagConfigController.java  # GET/PUT /api/projects/{id}/rag-config
```

---

### 3. 수정 파일 목록 (8개)

| 파일 | 변경 내용 |
|------|-----------|
| `ApiModels.java` | ProjectRagConfigResponse, UpdateProjectRagConfigRequest DTO 추가 |
| `SearchController.java` | 동적 설정 오버라이드 파라미터 추가, RagConfigService 연동 |
| `Neo4jSearchStrategy.java` | @Value 제거 → RagConfigService로 maxHop 조회 |
| `Text2CypherService.java` | @Value 제거 → 메서드 파라미터로 model 전달 |
| `EntityExtractionService.java` | @Value 제거 → 메서드 파라미터로 model 전달 |
| `HybridSearchService.java` | RRF_K 상수 제거 → FusionStrategy 패턴 적용 |
| `DocstEmbeddingService.java` | 프로젝트별 임베딩 모델 선택 + 재임베딩 로직 |
| `application.yml` | @ConfigurationProperties 활성화 |

---

### 4. 구현 상세

#### 4-D-1: 설정 인프라

**RagConfigDto.java**
```java
package com.docst.rag.config;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RagConfigDto(
    String version,
    EmbeddingConfig embedding,
    PgVectorConfig pgvector,
    Neo4jConfig neo4j,
    HybridConfig hybrid
) {
    public record EmbeddingConfig(String provider, String model, Integer dimensions) {}
    public record PgVectorConfig(Boolean enabled, Double similarityThreshold) {}
    public record Neo4jConfig(Boolean enabled, Integer maxHop, String entityExtractionModel) {}
    public record HybridConfig(String fusionStrategy, Integer rrfK, Double vectorWeight, Double graphWeight) {}
}
```

**RagConfigService.java**
```java
@Service
@RequiredArgsConstructor
public class RagConfigService {
    private final RagGlobalProperties globalProps;
    private final ProjectRepository projectRepository;
    private final ObjectMapper objectMapper;

    /**
     * 설정 해결 우선순위:
     * 1. 요청 파라미터 (검색 API 호출 시)
     * 2. 프로젝트 설정 (Project.ragConfig JSONB)
     * 3. 전역 기본값 (application.yml)
     */
    public ResolvedRagConfig resolve(Project project, @Nullable SearchRequestParams requestParams) {
        RagConfigDto projectConfig = parseProjectConfig(project);

        return ResolvedRagConfig.builder()
            .embeddingProvider(firstNonNull(
                requestParams != null ? requestParams.embeddingProvider() : null,
                projectConfig != null ? projectConfig.embedding().provider() : null,
                globalProps.getEmbedding().getProvider()
            ))
            .maxHop(firstNonNull(
                requestParams != null ? requestParams.maxHop() : null,
                projectConfig != null ? projectConfig.neo4j().maxHop() : null,
                globalProps.getNeo4j().getMaxHop()
            ))
            // ... 기타 필드
            .build();
    }
}
```

#### 4-D-2: Neo4j 동적화

**Neo4jSearchStrategy.java 수정**
```java
// Before
@Value("${docst.rag.neo4j.max-hop:2}")
private int maxHop;

// After
private final RagConfigService ragConfigService;
private final ProjectRepository projectRepository;

@Override
public List<SearchResult> search(UUID projectId, String query, int topK) {
    Project project = projectRepository.findById(projectId).orElseThrow();
    ResolvedRagConfig config = ragConfigService.resolve(project, null);
    int maxHop = config.maxHop();
    // ... 검색 로직
}
```

**EntityExtractionService.java 수정**
```java
// Before
@Value("${docst.rag.neo4j.entity-extraction-model:gpt-4o-mini}")
private String extractionModel;

// After - 메서드 파라미터로 전달
public ExtractionResult extractEntitiesAndRelations(
    String content,
    String headingPath,
    String extractionModel  // 동적 값
)
```

#### 4-D-3: Hybrid 동적화 (FusionStrategy 패턴)

**FusionStrategy.java**
```java
public interface FusionStrategy {
    List<SearchResult> fuse(
        List<SearchResult> vectorResults,
        List<SearchResult> graphResults,
        FusionParams params
    );
    String getName();
}

public record FusionParams(int rrfK, double vectorWeight, double graphWeight, int topK) {}
```

**RrfFusionStrategy.java**
```java
@Component
public class RrfFusionStrategy implements FusionStrategy {
    @Override
    public List<SearchResult> fuse(List<SearchResult> vectorResults,
                                    List<SearchResult> graphResults,
                                    FusionParams params) {
        int rrfK = params.rrfK();  // 동적 값 (기존: 상수 60)

        Map<UUID, RRFResult> rrfScores = new HashMap<>();
        for (int i = 0; i < vectorResults.size(); i++) {
            double score = 1.0 / (rrfK + i + 1);
            // ... RRF 점수 계산
        }
        return sortAndLimit(rrfScores, params.topK());
    }

    @Override
    public String getName() { return "rrf"; }
}
```

**WeightedSumFusionStrategy.java**
```java
@Component
public class WeightedSumFusionStrategy implements FusionStrategy {
    @Override
    public List<SearchResult> fuse(List<SearchResult> vectorResults,
                                    List<SearchResult> graphResults,
                                    FusionParams params) {
        // score = vectorWeight * normalizedVectorScore + graphWeight * normalizedGraphScore
        double vectorWeight = params.vectorWeight();
        double graphWeight = params.graphWeight();
        // ...
    }

    @Override
    public String getName() { return "weighted_sum"; }
}
```

#### 4-D-4: API 구현

**ProjectRagConfigController.java**
```java
@RestController
@RequestMapping("/api/projects/{projectId}/rag-config")
@RequiredArgsConstructor
public class ProjectRagConfigController {

    @GetMapping
    public ResponseEntity<ProjectRagConfigResponse> getRagConfig(@PathVariable UUID projectId) {
        // 프로젝트 RAG 설정 조회
    }

    @PutMapping
    public ResponseEntity<ProjectRagConfigResponse> updateRagConfig(
        @PathVariable UUID projectId,
        @RequestBody UpdateProjectRagConfigRequest request
    ) {
        // 설정 업데이트 (임베딩 모델 변경 시 재임베딩 트리거)
    }

    @PostMapping("/validate")
    public ResponseEntity<RagConfigValidationResponse> validateConfig(
        @PathVariable UUID projectId,
        @RequestBody RagConfigDto config
    ) {
        // 설정 유효성 검증
    }

    @GetMapping("/defaults")
    public ResponseEntity<RagConfigDto> getDefaults() {
        // 전역 기본 설정 조회
    }
}
```

**SearchController.java 수정**
```java
@GetMapping
public ResponseEntity<List<SearchResultResponse>> search(
    @PathVariable UUID projectId,
    @RequestParam(name = "q") String query,
    @RequestParam(required = false, defaultValue = "auto") String mode,
    @RequestParam(required = false, defaultValue = "10") Integer topK,
    // 동적 오버라이드 파라미터 추가
    @RequestParam(required = false) Integer maxHop,
    @RequestParam(required = false) Integer rrfK,
    @RequestParam(required = false) Double vectorWeight,
    @RequestParam(required = false) Double graphWeight
) {
    SearchRequestParams requestParams = SearchRequestParams.builder()
        .maxHop(maxHop).rrfK(rrfK)
        .vectorWeight(vectorWeight).graphWeight(graphWeight)
        .build();

    ResolvedRagConfig config = ragConfigService.resolve(project, requestParams);
    // ... 검색 실행
}
```

#### 4-D-5: 임베딩 동적화 + 재임베딩

**DocstEmbeddingService.java 수정**
```java
// Spring AI EmbeddingRequest로 런타임 모델 오버라이드
public int embedDocumentVersion(DocumentVersion docVersion, ResolvedRagConfig config) {
    EmbeddingOptions options = buildEmbeddingOptions(config);
    EmbeddingRequest request = new EmbeddingRequest(contents, options);
    EmbeddingResponse response = embeddingModel.call(request);
    // VectorStore에 저장
}

private EmbeddingOptions buildEmbeddingOptions(ResolvedRagConfig config) {
    return switch (config.embeddingProvider()) {
        case "ollama" -> OllamaEmbeddingOptions.builder()
            .model(config.embeddingModel()).build();
        default -> OpenAiSdkEmbeddingOptions.builder()
            .model(config.embeddingModel())
            .dimensions(config.embeddingDimensions()).build();
    };
}

/**
 * 임베딩 모델 변경 시 기존 임베딩 삭제 후 재생성
 */
@Async
public void reEmbedProject(UUID projectId, ResolvedRagConfig newConfig) {
    // 1. 기존 임베딩 삭제
    vectorStore.delete(FilterExpression.eq("project_id", projectId.toString()));

    // 2. 모든 DocumentVersion 재임베딩
    List<DocumentVersion> versions = documentVersionRepository.findByProjectId(projectId);
    for (DocumentVersion version : versions) {
        embedDocumentVersion(version, newConfig);
    }
}
```

---

### 5. API 명세

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/projects/{id}/rag-config` | 프로젝트 RAG 설정 조회 |
| PUT | `/api/projects/{id}/rag-config` | 설정 업데이트 (모델 변경 시 재임베딩) |
| POST | `/api/projects/{id}/rag-config/validate` | 설정 검증 |
| GET | `/api/projects/{id}/rag-config/defaults` | 전역 기본값 조회 |

**검색 API 동적 오버라이드**
```
GET /api/projects/{id}/search
    ?q=검색어
    &mode=auto|semantic|graph|hybrid
    &topK=10
    &maxHop=2          # Neo4j 오버라이드
    &rrfK=60           # Hybrid RRF 오버라이드
    &vectorWeight=0.6  # Hybrid 가중치
    &graphWeight=0.4
```

---

### 6. 설정 우선순위

```
┌─────────────────────────────────────┐
│  1. 검색 API 요청 파라미터          │  ← 최우선 (개발/테스트용)
│     (maxHop, rrfK, weights...)      │
└────────────────┬────────────────────┘
                 │ (없으면)
                 ▼
┌─────────────────────────────────────┐
│  2. Project.ragConfig (JSONB)       │  ← 프로젝트별 설정 (UI에서 관리)
│     (프로젝트 설정 화면)             │
└────────────────┬────────────────────┘
                 │ (없으면)
                 ▼
┌─────────────────────────────────────┐
│  3. application.yml 전역 기본값     │  ← 시스템 기본값
│     (@ConfigurationProperties)       │
└─────────────────────────────────────┘
```

---

### 7. 핵심 파일 경로

#### 수정 대상
| 파일 | 라인 | 변경 |
|------|------|------|
| `Neo4jSearchStrategy.java` | 40 | @Value → RagConfigService |
| `EntityExtractionService.java` | 42 | @Value → 메서드 파라미터 |
| `Text2CypherService.java` | 32 | @Value → 메서드 파라미터 |
| `HybridSearchService.java` | 22 | RRF_K 상수 → FusionStrategy |
| `DocstEmbeddingService.java` | - | 동적 임베딩 + 재임베딩 |
| `SearchController.java` | - | 오버라이드 파라미터 추가 |
| `ApiModels.java` | - | DTO 추가 |

#### 신규 생성
```
backend/src/main/java/com/docst/rag/config/
├── RagConfigDto.java
├── ResolvedRagConfig.java
├── RagGlobalProperties.java
└── RagConfigService.java

backend/src/main/java/com/docst/rag/hybrid/
├── FusionStrategy.java
├── FusionParams.java
├── RrfFusionStrategy.java
└── WeightedSumFusionStrategy.java

backend/src/main/java/com/docst/api/
└── ProjectRagConfigController.java
```

---

### 8. 주의사항

1. **임베딩 차원 검증**: 모델 변경 시 차원 일치 여부 검증 필수
2. **재임베딩 비동기 처리**: 대용량 프로젝트는 시간 소요 → `@Async` + 진행 상태 조회
3. **캐싱**: `RagConfigService.resolve()` 빈번 호출 → 필요시 `@Cacheable` 적용
4. **마이그레이션 불필요**: Project.ragConfig 컬럼 이미 존재 (V9)

---

### 9. 구현 일정

| 단계 | 작업 | 예상 |
|------|------|------|
| 4-D-1 | 설정 인프라 (RagConfigDto, RagConfigService) | 1일 |
| 4-D-2 | Neo4j 동적화 (@Value 제거) | 0.5일 |
| 4-D-3 | Hybrid 동적화 (FusionStrategy 패턴) | 1일 |
| 4-D-4 | API 구현 (ProjectRagConfigController) | 0.5일 |
| 4-D-5 | 임베딩 동적화 + 재임베딩 | 1일 |
| **합계** | | **4일** |

---

## 전체 Phase 4 진행 상태

| Phase | 작업 | 상태 | 비고 |
|-------|------|------|------|
| 4-A | 기반 구조 | ✅ 완료 | RagMode, RagSearchStrategy |
| 4-B | Mode 1 리팩토링 | ✅ 완료 | PgVectorSearchStrategy |
| 4-C | Neo4j Graph RAG | ✅ 완료 | EntityExtraction, Text2Cypher |
| **4-D** | **동적 RAG 설정** | 🆕 **신규** | **yml → JSONB 동적 설정** |
| 4-E | QueryRouter (auto 모드) | ⏳ 대기 | LLM 기반 자동 라우팅 |

---

## 완료 기준 (v2)

- [ ] 3가지 RAG 모드 모두 동작 (pgvector, neo4j, hybrid)
- [ ] **프로젝트별 RAG 설정 API (GET/PUT)**
- [ ] **설정 우선순위 동작 (요청 > 프로젝트 > 전역)**
- [ ] **FusionStrategy 패턴 적용 (RRF, WeightedSum)**
- [ ] **임베딩 모델 변경 시 재임베딩 동작**
- [ ] 기존 API 호환성 유지 (mode="keyword", "semantic")
- [ ] Docker Compose로 전체 스택 실행 가능
