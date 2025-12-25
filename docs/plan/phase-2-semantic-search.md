# Phase 2: 의미 검색(Semantic Search) 구현 계획

> **목표**: pgvector 기반 의미 검색, 청킹/임베딩 파이프라인, MCP semantic search 지원

---

## 선행 조건 (Phase 1 완료)
- [x] PostgreSQL + JPA 연동
- [x] 문서 동기화 파이프라인
- [x] 키워드 검색 동작
- [x] MCP Tools 기초

---

## Sprint 2-1: 청킹(Chunking) 시스템

### 2.1.1 DocChunk 엔티티 추가
**위치**: `backend/src/main/java/com/docst/domain/DocChunk.java`

```java
@Entity
@Table(name = "dm_doc_chunk")
public class DocChunk {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_version_id", nullable = false)
    private DocumentVersion documentVersion;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "heading_path")
    private String headingPath;  // "# Title > ## Section > ### Subsection"

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "token_count", nullable = false)
    private int tokenCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
```

### 2.1.2 청킹 전략
**위치**: `backend/src/main/java/com/docst/chunking/`

| 클래스 | 책임 |
|--------|------|
| `ChunkingService` | 청킹 오케스트레이션 |
| `MarkdownChunker` | Markdown 헤딩 기반 분할 |
| `TokenCounter` | 토큰 수 계산 (tiktoken 호환) |

**청킹 규칙**:
```java
public class ChunkingConfig {
    private int maxTokens = 512;        // 청크 최대 토큰 수
    private int overlapTokens = 50;     // 청크 간 중복 토큰
    private int minTokens = 100;        // 청크 최소 토큰 (미만 시 병합)
}
```

**헤딩 기반 청킹 알고리즘**:
```
1. Markdown AST 파싱
2. 헤딩(#, ##, ###) 기준으로 섹션 분리
3. 각 섹션에 대해:
   a. 토큰 수 계산
   b. maxTokens 초과 시 문단 단위로 추가 분할
   c. minTokens 미만 시 이전/다음 청크와 병합 고려
   d. headingPath 생성 (상위 헤딩 경로)
4. 각 청크에 overlap 적용 (이전 청크 끝부분 포함)
```

### 2.1.3 Flyway 마이그레이션
**파일**: `V4__add_doc_chunk.sql`

```sql
CREATE TABLE dm_doc_chunk (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    document_version_id uuid NOT NULL REFERENCES dm_document_version(id) ON DELETE CASCADE,
    chunk_index integer NOT NULL,
    heading_path text,
    content text NOT NULL,
    token_count integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (document_version_id, chunk_index)
);

CREATE INDEX idx_chunk_docver_id ON dm_doc_chunk(document_version_id);
```

---

## Sprint 2-2: 임베딩(Embedding) 시스템

### 2.2.1 DocEmbedding 엔티티 추가
**위치**: `backend/src/main/java/com/docst/domain/DocEmbedding.java`

```java
@Entity
@Table(name = "dm_doc_embedding")
public class DocEmbedding {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doc_chunk_id", nullable = false)
    private DocChunk docChunk;

    @Column(nullable = false)
    private String model;  // "text-embedding-3-small", "ollama/nomic-embed-text"

    @Column(nullable = false, columnDefinition = "vector(1536)")
    private float[] embedding;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
```

### 2.2.2 임베딩 서비스 추상화
**위치**: `backend/src/main/java/com/docst/embedding/`

```java
public interface EmbeddingProvider {
    String getModel();
    int getDimension();
    float[] embed(String text);
    List<float[]> embedBatch(List<String> texts);
}

// 구현체
@Component
@ConditionalOnProperty(name = "docst.embedding.provider", havingValue = "openai")
public class OpenAiEmbeddingProvider implements EmbeddingProvider { ... }

@Component
@ConditionalOnProperty(name = "docst.embedding.provider", havingValue = "ollama")
public class OllamaEmbeddingProvider implements EmbeddingProvider { ... }

@Component
@ConditionalOnProperty(name = "docst.embedding.provider", havingValue = "local", matchIfMissing = true)
public class LocalEmbeddingProvider implements EmbeddingProvider {
    // Sentence Transformers 또는 ONNX 기반 로컬 임베딩
}
```

### 2.2.3 설정
**application.yml**:
```yaml
docst:
  embedding:
    provider: ollama  # openai, ollama, local
    model: nomic-embed-text
    dimension: 768
    batch-size: 32

  ollama:
    base-url: http://localhost:11434

  openai:
    api-key: ${OPENAI_API_KEY:}
    model: text-embedding-3-small
```

### 2.2.4 Flyway 마이그레이션
**파일**: `V5__add_doc_embedding.sql`

```sql
-- pgvector 확장 활성화
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE dm_doc_embedding (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    doc_chunk_id uuid NOT NULL REFERENCES dm_doc_chunk(id) ON DELETE CASCADE,
    model text NOT NULL,
    embedding vector(1536) NOT NULL,  -- 기본값, 모델에 따라 조정
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (doc_chunk_id, model)
);

-- IVFFlat 인덱스 (초기)
CREATE INDEX idx_embedding_ivfflat
    ON dm_doc_embedding
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);
```

---

## Sprint 2-3: 동기화 파이프라인 확장

### 2.3.1 파이프라인 흐름 (확장)
```
1. SyncJob 생성 (PENDING)
2. Git clone/fetch (RUNNING)
3. 문서 파일 스캔
4. 각 문서에 대해:
   a. Document/DocumentVersion 저장
   b. 청킹 수행 → DocChunk 저장
   c. 임베딩 생성 → DocEmbedding 저장 (비동기)
5. SyncJob 완료 (SUCCEEDED/FAILED)
```

### 2.3.2 비동기 임베딩 처리
```java
@Service
public class EmbeddingJobService {
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    @Async
    public CompletableFuture<Void> processEmbeddings(UUID documentVersionId) {
        List<DocChunk> chunks = chunkRepository.findByDocumentVersionId(documentVersionId);

        // 배치 처리
        List<List<DocChunk>> batches = partition(chunks, batchSize);
        for (List<DocChunk> batch : batches) {
            List<String> texts = batch.stream().map(DocChunk::getContent).toList();
            List<float[]> embeddings = embeddingProvider.embedBatch(texts);

            for (int i = 0; i < batch.size(); i++) {
                DocEmbedding embedding = new DocEmbedding(
                    batch.get(i),
                    embeddingProvider.getModel(),
                    embeddings.get(i)
                );
                embeddingRepository.save(embedding);
            }
        }
        return CompletableFuture.completedFuture(null);
    }
}
```

### 2.3.3 SSE 이벤트 확장
```java
public record SyncEvent(
    UUID jobId,
    String status,
    String phase,      // CLONING, SCANNING, CHUNKING, EMBEDDING, COMPLETED
    String message,
    int progress,
    SyncStats stats
) {}

public record SyncStats(
    int totalDocs,
    int processedDocs,
    int totalChunks,
    int embeddedChunks
) {}
```

---

## Sprint 2-4: 의미 검색 구현

### 2.4.1 벡터 검색 쿼리
**위치**: `backend/src/main/java/com/docst/repository/DocEmbeddingRepository.java`

```java
@Repository
public interface DocEmbeddingRepository extends JpaRepository<DocEmbedding, UUID> {

    @Query(value = """
        SELECT
            d.id AS document_id,
            d.path,
            dv.commit_sha,
            c.id AS chunk_id,
            c.heading_path,
            1 - (e.embedding <=> CAST(:queryEmbedding AS vector)) AS score,
            LEFT(c.content, 300) AS snippet
        FROM dm_doc_embedding e
        JOIN dm_doc_chunk c ON c.id = e.doc_chunk_id
        JOIN dm_document_version dv ON dv.id = c.document_version_id
        JOIN dm_document d ON d.id = dv.document_id
        JOIN dm_repository r ON r.id = d.repository_id
        WHERE r.project_id = :projectId
          AND e.model = :model
          AND dv.commit_sha = d.latest_commit_sha
        ORDER BY e.embedding <=> CAST(:queryEmbedding AS vector)
        LIMIT :topK
        """, nativeQuery = true)
    List<SemanticSearchResult> searchSemantic(
        UUID projectId,
        String model,
        float[] queryEmbedding,
        int topK
    );
}
```

### 2.4.2 하이브리드 검색 (Keyword + Semantic)
```java
@Service
public class HybridSearchService {

    public List<SearchResult> search(UUID projectId, String query, SearchMode mode, int topK) {
        return switch (mode) {
            case KEYWORD -> keywordSearch(projectId, query, topK);
            case SEMANTIC -> semanticSearch(projectId, query, topK);
            case HYBRID -> hybridSearch(projectId, query, topK);
        };
    }

    private List<SearchResult> hybridSearch(UUID projectId, String query, int topK) {
        // 1. 키워드 검색 (BM25/tsvector)
        List<SearchResult> keywordResults = keywordSearch(projectId, query, topK * 2);

        // 2. 의미 검색
        List<SearchResult> semanticResults = semanticSearch(projectId, query, topK * 2);

        // 3. RRF (Reciprocal Rank Fusion) 기반 점수 병합
        Map<UUID, Double> rrfScores = new HashMap<>();
        int k = 60; // RRF 상수

        for (int i = 0; i < keywordResults.size(); i++) {
            UUID docId = keywordResults.get(i).documentId();
            rrfScores.merge(docId, 1.0 / (k + i + 1), Double::sum);
        }

        for (int i = 0; i < semanticResults.size(); i++) {
            UUID docId = semanticResults.get(i).documentId();
            rrfScores.merge(docId, 1.0 / (k + i + 1), Double::sum);
        }

        // 4. 점수 기준 정렬 및 상위 K개 반환
        return rrfScores.entrySet().stream()
            .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
            .limit(topK)
            .map(entry -> buildResult(entry.getKey(), entry.getValue()))
            .toList();
    }
}
```

### 2.4.3 검색 결과 강화
```java
public record EnhancedSearchResult(
    UUID documentId,
    UUID repositoryId,
    String path,
    String title,
    String commitSha,
    UUID chunkId,
    String headingPath,     // 청크가 속한 섹션 경로
    double score,
    String snippet,
    String highlightedSnippet,
    List<String> matchedTerms  // 키워드 검색 시 매칭된 단어
) {}
```

---

## Sprint 2-5: 프론트엔드 검색 UI 개선

### 2.5.1 검색 모드 선택
```tsx
// components/SearchBar.tsx
export function SearchBar() {
  const [mode, setMode] = useState<'keyword' | 'semantic' | 'hybrid'>('hybrid');

  return (
    <div className="flex gap-2">
      <Input placeholder="Search documents..." />
      <Select value={mode} onValueChange={setMode}>
        <SelectItem value="keyword">Keyword</SelectItem>
        <SelectItem value="semantic">Semantic</SelectItem>
        <SelectItem value="hybrid">Hybrid (Recommended)</SelectItem>
      </Select>
      <Button>Search</Button>
    </div>
  );
}
```

### 2.5.2 검색 결과 표시 개선
```tsx
// components/SearchResults.tsx
export function SearchResult({ result }: { result: EnhancedSearchResult }) {
  return (
    <Card>
      <CardHeader>
        <div className="flex justify-between">
          <CardTitle className="text-sm">{result.path}</CardTitle>
          <Badge variant="outline">{(result.score * 100).toFixed(1)}%</Badge>
        </div>
        {result.headingPath && (
          <p className="text-xs text-muted-foreground">{result.headingPath}</p>
        )}
      </CardHeader>
      <CardContent>
        <p
          className="text-sm"
          dangerouslySetInnerHTML={{ __html: result.highlightedSnippet }}
        />
      </CardContent>
    </Card>
  );
}
```

---

## Sprint 2-6: MCP Semantic Search 지원

### 2.6.1 search_documents Tool 확장
```json
{
  "name": "search_documents",
  "description": "프로젝트 문서 검색 (keyword/semantic/hybrid)",
  "inputSchema": {
    "type": "object",
    "required": ["projectId", "query"],
    "properties": {
      "projectId": { "type": "string", "format": "uuid" },
      "query": { "type": "string", "minLength": 1 },
      "mode": {
        "type": "string",
        "enum": ["keyword", "semantic", "hybrid"],
        "default": "hybrid"
      },
      "topK": { "type": "integer", "minimum": 1, "maximum": 50, "default": 10 },
      "includeContent": { "type": "boolean", "default": false }
    }
  }
}
```

### 2.6.2 응답 예시
```json
{
  "results": [
    {
      "documentId": "550e8400-e29b-41d4-a716-446655440000",
      "path": "docs/architecture/overview.md",
      "title": "Architecture Overview",
      "headingPath": "# Architecture > ## Component Diagram",
      "score": 0.92,
      "snippet": "The system consists of three main components: API Gateway, Document Service, and Search Engine...",
      "content": "..."  // includeContent: true 시 전체 청크 내용
    }
  ],
  "metadata": {
    "mode": "hybrid",
    "totalResults": 15,
    "queryTime": "0.123s"
  }
}
```

---

## Sprint 2-7: docker-compose 확장

### 2.7.1 Ollama 서비스 추가 (선택)
```yaml
services:
  ollama:
    image: ollama/ollama:latest
    volumes:
      - ollama_data:/root/.ollama
    ports:
      - "11434:11434"
    deploy:
      resources:
        reservations:
          devices:
            - driver: nvidia
              count: 1
              capabilities: [gpu]

  backend:
    environment:
      DOCST_EMBEDDING_PROVIDER: ollama
      DOCST_OLLAMA_BASE_URL: http://ollama:11434
    depends_on:
      - postgres
      - ollama

volumes:
  ollama_data:
```

### 2.7.2 임베딩 모델 사전 로드
```yaml
# docker-compose.yml 또는 init 스크립트
services:
  ollama-init:
    image: ollama/ollama:latest
    entrypoint: ["ollama", "pull", "nomic-embed-text"]
    depends_on:
      - ollama
```

---

## 완료 기준 (Definition of Done)

### 기능
- [ ] 문서 동기화 시 자동 청킹 수행
- [ ] 청킹된 내용에 대해 임베딩 생성
- [ ] 의미 검색(semantic) 동작 확인
- [ ] 하이브리드 검색(hybrid) 동작 확인
- [ ] MCP search_documents에서 semantic 모드 지원
- [ ] 검색 결과에 headingPath 표시

### 성능
- [ ] 청크 임베딩 처리량: 100 chunks/min 이상
- [ ] 의미 검색 응답 시간: < 1초 (1000개 청크 기준)
- [ ] IVFFlat 인덱스 적용 확인

### 품질
- [ ] 검색 관련성 테스트 (수동 QA)
- [ ] 토큰 카운트 정확성 검증

---

## 파일 구조 (추가)

```
backend/
├── src/main/java/com/docst/
│   ├── domain/
│   │   ├── DocChunk.java           # 추가
│   │   └── DocEmbedding.java       # 추가
│   ├── repository/
│   │   ├── DocChunkRepository.java     # 추가
│   │   └── DocEmbeddingRepository.java # 추가
│   ├── chunking/                    # 추가
│   │   ├── ChunkingService.java
│   │   ├── MarkdownChunker.java
│   │   └── TokenCounter.java
│   ├── embedding/                   # 추가
│   │   ├── EmbeddingProvider.java
│   │   ├── EmbeddingService.java
│   │   ├── OpenAiEmbeddingProvider.java
│   │   └── OllamaEmbeddingProvider.java
│   └── service/
│       ├── HybridSearchService.java    # 추가
│       └── EmbeddingJobService.java    # 추가
└── src/main/resources/
    └── db/migration/
        ├── V4__add_doc_chunk.sql       # 추가
        └── V5__add_doc_embedding.sql   # 추가
```

---

## 기술적 고려사항

### 임베딩 모델 선택
| 모델 | 차원 | 성능 | 비용 | 권장 |
|------|------|------|------|------|
| text-embedding-3-small | 1536 | 높음 | $0.02/1M tokens | 프로덕션 |
| nomic-embed-text | 768 | 중간 | 무료 (로컬) | 개발/로컬 |
| all-MiniLM-L6-v2 | 384 | 낮음 | 무료 (로컬) | 테스트 |

### 인덱스 전략
- **초기 (< 10만 벡터)**: IVFFlat (lists=100)
- **중기 (10만~100만)**: HNSW (m=16, ef_construction=64)
- **대규모 (> 100만)**: Approximate Nearest Neighbor 서비스 고려 (Pinecone, Milvus)

### 청킹 최적화
- 헤딩 기반 분할 우선
- 코드 블록은 별도 청크로 분리
- 테이블은 컨텍스트 보존을 위해 통째로 유지
