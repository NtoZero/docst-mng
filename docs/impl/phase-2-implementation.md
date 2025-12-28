# Phase 2: 의미 검색 구현 완료 보고서

> **구현 일자**: 2025-12-27 ~ 2025-12-28
> **상태**: ✅ 완료 (백엔드 100%, 프론트엔드 100%)
> **구현자**: Spring AI 1.0.0-M5 기반

---

## 개요

Phase 2는 키워드 검색을 넘어 AI 기반 의미 검색(Semantic Search)과 하이브리드 검색을 구현하는 단계입니다. 청킹, 임베딩, 벡터 검색의 전체 파이프라인을 Spring AI 프레임워크를 활용하여 구현했습니다.

---

## Phase 2-A: 청킹 시스템 (Chunking)

### 구현 개요

Markdown 문서를 의미 있는 단위로 분할하여 임베딩 및 검색의 기반을 마련합니다.

### 핵심 컴포넌트

#### 1. DocChunk 엔티티

**파일**: `backend/src/main/java/com/docst/domain/DocChunk.java`

```java
@Entity
@Table(name = "dm_doc_chunk")
public class DocChunk {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_version_id", nullable = false)
    private DocumentVersion documentVersion;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;  // 문서 내 순서

    @Column(name = "heading_path")
    private String headingPath;  // "# Title > ## Section > ### Subsection"

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;  // 청크 내용

    @Column(name = "token_count", nullable = false)
    private int tokenCount;  // tiktoken 기준 토큰 수

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
```

#### 2. MarkdownChunker

**파일**: `backend/src/main/java/com/docst/chunking/MarkdownChunker.java`

**주요 기능**:
- Flexmark 라이브러리로 Markdown AST 파싱
- 헤딩(H1~H6) 계층 구조 추적
- 헤딩 경로 생성 (예: "Introduction > Getting Started > Installation")
- 청크 크기 제어 (최대/최소 토큰, 오버랩)

**알고리즘**:
```
1. Markdown을 AST로 파싱
2. 헤딩 노드를 기준으로 문서 분할
3. 각 섹션의 토큰 수 계산
4. 최대 토큰 초과 시 재귀적 분할
5. 최소 토큰 미만 시 이전 청크와 병합
6. 청크 간 오버랩 적용
```

#### 3. TokenCounter

**파일**: `backend/src/main/java/com/docst/chunking/TokenCounter.java`

**구현**:
- `jtokkit` 라이브러리 (tiktoken 호환)
- OpenAI GPT-3.5-turbo 토크나이저 사용
- 정확한 토큰 수 계산으로 임베딩 비용 최적화

#### 4. ChunkingService

**파일**: `backend/src/main/java/com/docst/chunking/ChunkingService.java`

**책임**:
- MarkdownChunker 오케스트레이션
- DocChunk 엔티티 생성 및 저장
- DocumentVersion과의 연결 관리

### 데이터베이스 스키마

**마이그레이션**: `V5__add_doc_chunk.sql`

```sql
CREATE TABLE dm_doc_chunk (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    document_version_id uuid NOT NULL
        REFERENCES dm_document_version(id) ON DELETE CASCADE,
    chunk_index integer NOT NULL,
    heading_path text,
    content text NOT NULL,
    token_count integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (document_version_id, chunk_index)
);

CREATE INDEX idx_chunk_docver_id ON dm_doc_chunk(document_version_id);
```

### 설정

**application.yml**:
```yaml
docst:
  chunking:
    max-tokens: 512          # 최대 청크 크기
    overlap-tokens: 50       # 청크 간 오버랩
    min-tokens: 100          # 최소 청크 크기 (미만 시 병합)
    heading-path-separator: " > "
```

### 테스트

- **TokenCounterTest**: 토큰 계산 정확도 검증
- **MarkdownChunkerTest**: 청킹 로직 단위 테스트
- **ChunkingServiceTest**: 통합 테스트

---

## Phase 2-B: 임베딩 시스템 (Embedding)

### 구현 개요

Spring AI를 활용하여 pgvector 기반 벡터 저장소와 OpenAI/Ollama 임베딩 모델을 통합했습니다.

### Spring AI 통합

#### 의존성 (build.gradle.kts)

```kotlin
dependencies {
    // Spring AI BOM
    implementation(platform("org.springframework.ai:spring-ai-bom:1.0.0-M5"))

    // PgVector VectorStore
    implementation("org.springframework.ai:spring-ai-pgvector-store-spring-boot-starter")

    // OpenAI Embedding (기본)
    implementation("org.springframework.ai:spring-ai-openai-spring-boot-starter")

    // Ollama Embedding (선택)
    implementation("org.springframework.ai:spring-ai-ollama-spring-boot-starter")
}
```

### 핵심 컴포넌트

#### 1. DocstEmbeddingService

**파일**: `backend/src/main/java/com/docst/embedding/DocstEmbeddingService.java`

**주요 기능**:
```java
@Service
@RequiredArgsConstructor
public class DocstEmbeddingService {
    private final VectorStore vectorStore;  // Spring AI 자동 주입
    private final DocChunkRepository docChunkRepository;

    /**
     * DocChunk를 임베딩하여 VectorStore에 저장
     */
    @Transactional
    public void embedChunks(List<DocChunk> chunks) {
        List<org.springframework.ai.document.Document> documents = chunks.stream()
            .map(chunk -> new org.springframework.ai.document.Document(
                chunk.getId().toString(),
                chunk.getContent(),
                Map.of(
                    "doc_chunk_id", chunk.getId().toString(),
                    "heading_path", chunk.getHeadingPath(),
                    "document_version_id", chunk.getDocumentVersion().getId().toString(),
                    "project_id", chunk.getDocumentVersion().getDocument()
                        .getRepository().getProjectId().toString(),
                    "repository_id", chunk.getDocumentVersion().getDocument()
                        .getRepositoryId().toString(),
                    "token_count", chunk.getTokenCount()
                )
            ))
            .toList();

        vectorStore.add(documents);  // 자동 임베딩 및 저장
    }
}
```

**메타데이터 전략**:
- `doc_chunk_id`: DocChunk와 연결
- `heading_path`: 문서 구조 정보
- `project_id`, `repository_id`: 필터링용
- `document_version_id`: 버전 추적

### 데이터베이스 스키마

**마이그레이션**: `V6__add_spring_ai_vector_store.sql`

```sql
-- pgvector 확장 활성화
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Spring AI VectorStore 테이블
CREATE TABLE IF NOT EXISTS vector_store (
    id uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    content text,
    metadata json,
    embedding vector(1536)  -- OpenAI text-embedding-3-small
);

-- HNSW 인덱스 (고속 벡터 검색)
CREATE INDEX ON vector_store USING HNSW (embedding vector_cosine_ops);

-- 메타데이터 인덱스
CREATE INDEX ON vector_store USING GIN (metadata);
```

**vector_store 테이블**:
- Spring AI 표준 스키마 사용
- `metadata` JSON에 DocChunk 연결 정보 저장
- HNSW 인덱스로 빠른 벡터 검색

### 설정

**application.yml**:
```yaml
spring:
  ai:
    # OpenAI (기본)
    openai:
      api-key: ${OPENAI_API_KEY:}
      embedding:
        enabled: ${OPENAI_EMBEDDING_ENABLED:true}
        options:
          model: text-embedding-3-small  # 1536 dimensions

    # Ollama (선택)
    ollama:
      base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
      embedding:
        enabled: ${OLLAMA_EMBEDDING_ENABLED:false}
        options:
          model: nomic-embed-text  # 768 dimensions

    # PgVector VectorStore
    vectorstore:
      pgvector:
        index-type: HNSW
        distance-type: COSINE_DISTANCE
        dimensions: ${EMBEDDING_DIMENSIONS:1536}
        initialize-schema: true
        table-name: vector_store
```

**환경 변수** (.env):
```bash
# OpenAI (기본)
OPENAI_API_KEY=sk-proj-...
OPENAI_EMBEDDING_ENABLED=true
OPENAI_EMBEDDING_MODEL=text-embedding-3-small
EMBEDDING_DIMENSIONS=1536

# Ollama (선택)
OLLAMA_EMBEDDING_ENABLED=false
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_EMBEDDING_MODEL=nomic-embed-text
# Ollama 사용 시: EMBEDDING_DIMENSIONS=768
```

### 임베딩 모델 비교

| 모델 | Provider | 차원 | 성능 | 비용 |
|------|----------|------|------|------|
| text-embedding-3-small | OpenAI | 1536 | 높음 | 유료 |
| text-embedding-ada-002 | OpenAI | 1536 | 중간 | 유료 |
| nomic-embed-text | Ollama | 768 | 중간 | 무료 (로컬) |
| mxbai-embed-large | Ollama | 1024 | 높음 | 무료 (로컬) |

---

## Phase 2-C: 의미/하이브리드 검색

### 구현 개요

키워드 검색, 의미 검색, 하이브리드 검색을 모두 지원하는 통합 검색 시스템을 구현했습니다.

### 백엔드 컴포넌트

#### 1. SemanticSearchService

**파일**: `backend/src/main/java/com/docst/service/SemanticSearchService.java`

**주요 기능**:
```java
@Service
@RequiredArgsConstructor
public class SemanticSearchService {
    private final VectorStore vectorStore;

    public List<SearchResult> searchSemantic(
        UUID projectId,
        String query,
        int topK
    ) {
        SearchRequest request = SearchRequest.builder()
            .query(query)
            .topK(topK)
            .similarityThreshold(0.7)
            .filterExpression(Filter.builder()
                .key("project_id")
                .value(projectId.toString())
                .build())
            .build();

        List<org.springframework.ai.document.Document> results =
            vectorStore.similaritySearch(request);

        // Spring AI Document → SearchResult 변환
        return results.stream()
            .map(this::toSearchResult)
            .toList();
    }
}
```

**특징**:
- Spring AI VectorStore.similaritySearch() 사용
- 코사인 유사도 기반 검색
- 유사도 임계값 설정 (0.7 = 70%)
- 프로젝트/저장소 필터링

#### 2. HybridSearchService

**파일**: `backend/src/main/java/com/docst/service/HybridSearchService.java`

**RRF (Reciprocal Rank Fusion) 알고리즘**:
```java
@Service
@RequiredArgsConstructor
public class HybridSearchService {
    private final KeywordSearchService keywordSearchService;
    private final SemanticSearchService semanticSearchService;

    private static final int RRF_K = 60;

    public List<SearchResult> hybridSearch(
        UUID projectId,
        String query,
        int topK
    ) {
        // 두 검색 결과 모두 가져오기
        List<SearchResult> keywordResults =
            keywordSearchService.search(projectId, query, topK * 2);
        List<SearchResult> semanticResults =
            semanticSearchService.searchSemantic(projectId, query, topK * 2);

        // RRF 점수 계산
        Map<UUID, Double> rrfScores = new HashMap<>();

        for (int i = 0; i < keywordResults.size(); i++) {
            UUID chunkId = keywordResults.get(i).chunkId();
            double score = 1.0 / (RRF_K + i + 1);
            rrfScores.merge(chunkId, score, Double::sum);
        }

        for (int i = 0; i < semanticResults.size(); i++) {
            UUID chunkId = semanticResults.get(i).chunkId();
            double score = 1.0 / (RRF_K + i + 1);
            rrfScores.merge(chunkId, score, Double::sum);
        }

        // 점수 기준 정렬
        return rrfScores.entrySet().stream()
            .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
            .limit(topK)
            .map(entry -> findResult(entry.getKey(),
                keywordResults, semanticResults))
            .toList();
    }
}
```

**RRF 공식**: `score = sum(1 / (k + rank))`
- k = 60 (상수)
- rank = 결과 내 순위 (0-based)
- 두 검색 결과의 점수를 합산

#### 3. SearchController

**파일**: `backend/src/main/java/com/docst/api/SearchController.java`

**API 엔드포인트**:
```java
@GetMapping("/api/projects/{projectId}/search")
public ResponseEntity<List<SearchResult>> search(
    @PathVariable UUID projectId,
    @RequestParam String q,
    @RequestParam(defaultValue = "keyword") String mode,
    @RequestParam(defaultValue = "10") int topK
) {
    List<SearchResult> results = switch (mode) {
        case "semantic" -> semanticSearchService.searchSemantic(
            projectId, q, topK);
        case "hybrid" -> hybridSearchService.hybridSearch(
            projectId, q, topK);
        default -> searchService.search(projectId, q, topK);
    };

    return ResponseEntity.ok(results);
}
```

**지원 모드**:
- `keyword`: PostgreSQL ILIKE 기반 (기존)
- `semantic`: pgvector 벡터 유사도
- `hybrid`: RRF 융합

### 프론트엔드 컴포넌트

#### 1. SearchModeSelect

**파일**: `frontend/components/search-mode-select.tsx`

```tsx
<SearchModeSelect value={mode} onChange={setMode} />
```

**UI**:
- Keyword / Semantic / Hybrid 버튼
- Hybrid에 "Recommended" 배지
- Tailwind 기반 스타일링

#### 2. SearchResultCard

**파일**: `frontend/components/search-result-card.tsx`

```tsx
<SearchResultCard result={result} />
```

**표시 정보**:
- 문서 경로
- 헤딩 경로 (의미 검색 시)
- 스니펫 (하이라이트 포함)
- 매치 점수 (%)
- 청크 ID

#### 3. 검색 페이지

**파일**: `frontend/app/[locale]/projects/[projectId]/search/page.tsx`

```tsx
const [mode, setMode] = useState<'keyword' | 'semantic' | 'hybrid'>('hybrid');

const { data: results } = useSearch(projectId, {
  q: searchQuery,
  mode: mode,
  topK: 20
});
```

**개선 사항**:
- 검색 모드 선택 UI 추가
- SearchResultCard 사용
- 다국어 메시지 지원

### 다국어 메시지

**messages/en.json**:
```json
{
  "search": {
    "modeKeyword": "Keyword",
    "modeSemantic": "Semantic",
    "modeHybrid": "Hybrid",
    "recommended": "Recommended",
    "searchPlaceholder": "Enter search query..."
  }
}
```

**messages/ko.json**:
```json
{
  "search": {
    "modeKeyword": "키워드",
    "modeSemantic": "의미",
    "modeHybrid": "하이브리드",
    "recommended": "추천"
  }
}
```

---

## 테스트

### 단위 테스트

| 테스트 | 파일 | 검증 내용 |
|--------|------|----------|
| TokenCounterTest | `backend/src/test/.../TokenCounterTest.java` | 토큰 계산 정확도 |
| MarkdownChunkerTest | `backend/src/test/.../MarkdownChunkerTest.java` | 청킹 로직 |
| ChunkingServiceTest | `backend/src/test/.../ChunkingServiceTest.java` | 청킹 통합 |

### 통합 테스트

| 테스트 | 파일 | 검증 내용 |
|--------|------|----------|
| SemanticSearchIntegrationTest | `backend/src/test/.../SemanticSearchIntegrationTest.java` | 임베딩 및 검색 전체 플로우 |
| HybridSearchServiceTest | `backend/src/test/.../HybridSearchServiceTest.java` | RRF 점수 계산 |

---

## 아키텍처 다이어그램

```
┌─────────────────────────────────────────────────────────┐
│                     Frontend (Next.js)                   │
│  ┌─────────────────────────────────────────────────┐   │
│  │  Search Page                                     │   │
│  │  ┌─────────────┐  ┌──────────────────────────┐ │   │
│  │  │ Mode Select │  │ SearchResultCard (List)  │ │   │
│  │  │ - Keyword   │  │ - Document Path          │ │   │
│  │  │ - Semantic  │  │ - Heading Path           │ │   │
│  │  │ - Hybrid    │  │ - Snippet                │ │   │
│  │  └─────────────┘  │ - Score                  │ │   │
│  │                    └──────────────────────────┘ │   │
│  └─────────────────────────────────────────────────┘   │
└──────────────────────┬──────────────────────────────────┘
                       │ GET /api/projects/{id}/search
                       │ ?q=query&mode=hybrid&topK=10
                       ▼
┌─────────────────────────────────────────────────────────┐
│              Backend (Spring Boot + Spring AI)          │
│  ┌─────────────────────────────────────────────────┐   │
│  │  SearchController                                │   │
│  │  - mode 파라미터 분기                            │   │
│  └───┬─────────────────┬─────────────────┬─────────┘   │
│      │                 │                 │              │
│      ▼                 ▼                 ▼              │
│  ┌────────┐      ┌──────────┐     ┌──────────┐        │
│  │Keyword │      │ Semantic │     │ Hybrid   │        │
│  │Search  │      │ Search   │     │ Search   │        │
│  │Service │      │ Service  │     │ Service  │        │
│  └────┬───┘      └─────┬────┘     └────┬─────┘        │
│       │                │                │              │
│       │                ▼                │              │
│       │     ┌──────────────────┐       │              │
│       │     │ Spring AI        │       │              │
│       │     │ VectorStore      │       │              │
│       │     │ .similaritySearch│       │              │
│       │     └─────────┬────────┘       │              │
│       │               │                │              │
│       ▼               ▼                ▼              │
│  ┌──────────────────────────────────────────────┐    │
│  │  PostgreSQL + pgvector                       │    │
│  │  ┌──────────────┐  ┌──────────────────────┐ │    │
│  │  │ dm_doc_chunk │  │ vector_store         │ │    │
│  │  │ - content    │  │ - embedding (1536D)  │ │    │
│  │  │ - heading    │  │ - metadata (JSON)    │ │    │
│  │  │ - tokens     │  │ - HNSW index         │ │    │
│  │  └──────────────┘  └──────────────────────┘ │    │
│  └──────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────┘
```

---

## 성능 최적화

### 1. HNSW 인덱스
- 벡터 검색 성능 향상 (O(log n))
- IVFFlat 대비 빠른 검색 속도
- 약간의 정확도 트레이드오프

### 2. 청킹 전략
- 최적 청크 크기: 512 토큰
- 오버랩: 50 토큰 (컨텍스트 보존)
- 헤딩 기반 분할 (의미 단위 유지)

### 3. 메타데이터 활용
- JSON 인덱스로 빠른 필터링
- 프로젝트/저장소별 격리
- 불필요한 조인 최소화

---

## 운영 가이드

### 임베딩 모델 전환

**OpenAI → Ollama**:
```bash
# .env 수정
OPENAI_EMBEDDING_ENABLED=false
OLLAMA_EMBEDDING_ENABLED=true
EMBEDDING_DIMENSIONS=768

# Ollama 모델 다운로드
docker exec -it docst-ollama ollama pull nomic-embed-text

# 애플리케이션 재시작
./gradlew bootRun
```

### 데이터베이스 재초기화

기존 임베딩 삭제 후 재생성:
```sql
TRUNCATE TABLE vector_store;
TRUNCATE TABLE dm_doc_chunk CASCADE;

-- 재동기화 후 자동 임베딩
```

### 모니터링

**확인 사항**:
- vector_store 테이블 크기
- HNSW 인덱스 상태
- 임베딩 API 호출 비용 (OpenAI 사용 시)
- 검색 응답 시간

---

## 트러블슈팅

### 1. 임베딩이 생성되지 않음

**원인**: OpenAI API 키 미설정
**해결**:
```bash
export OPENAI_API_KEY=sk-proj-...
# 또는 .env 파일 확인
```

### 2. 벡터 검색 결과 없음

**원인**: dimensions 불일치
**해결**:
```yaml
# application.yml 확인
spring.ai.vectorstore.pgvector.dimensions: 1536  # OpenAI
# 또는
spring.ai.vectorstore.pgvector.dimensions: 768   # Ollama
```

### 3. 검색 속도 느림

**원인**: HNSW 인덱스 미생성
**해결**:
```sql
-- 인덱스 확인
SELECT * FROM pg_indexes WHERE tablename = 'vector_store';

-- 재생성
CREATE INDEX ON vector_store USING HNSW (embedding vector_cosine_ops);
```

---

## 참고 자료

- [Spring AI Documentation](https://docs.spring.io/spring-ai/reference/)
- [pgvector Documentation](https://github.com/pgvector/pgvector)
- [OpenAI Embeddings Guide](https://platform.openai.com/docs/guides/embeddings)
- [RRF Algorithm Paper](https://plg.uwaterloo.ca/~gvcormac/cormacksigir09-rrf.pdf)

---

## 다음 단계 (Phase 3)

- [ ] JWT 인증 고도화 ✅ (완료)
- [ ] GitHub OAuth ✅ (완료)
- [ ] Webhook 자동 동기화
- [ ] 문서 관계 그래프
- [ ] 권한 체크 AOP
