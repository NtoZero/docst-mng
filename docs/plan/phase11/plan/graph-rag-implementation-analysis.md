# Graph RAG 시스템 구현 분석

## 개요

Docst 프로젝트의 Graph RAG 시스템은 Neo4j 기반의 문서 그래프 검색과 LLM 기반 엔티티 추출을 결합한 RAG(Retrieval-Augmented Generation) 아키텍처입니다.

---

## 1. 아키텍처 개요

```
┌─────────────────────────────────────────────────────────────────┐
│                      Search Controller                           │
│  GET /api/projects/{id}/search?mode=<keyword|semantic|graph|hybrid|auto>
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    RagSearchStrategy (Strategy Pattern)          │
├─────────────────┬──────────────────┬───────────────────────────┤
│  PgVectorSearch │  Neo4jSearch     │  HybridSearch             │
│  (semantic)     │  (graph)         │  (fusion)                 │
└─────────────────┴──────────────────┴───────────────────────────┘
         │                  │                    │
         ▼                  ▼                    ▼
┌─────────────┐    ┌─────────────┐    ┌───────────────────┐
│  PgVector   │    │   Neo4j     │    │  HybridSearchSvc  │
│  (Vector)   │    │  (Graph)    │    │  (RRF Fusion)     │
└─────────────┘    └─────────────┘    └───────────────────┘
```

---

## 2. 핵심 컴포넌트

### 2.1 Neo4j Integration

**파일**: `backend/src/main/java/com/docst/rag/neo4j/Neo4jConfig.java`

**활성화 조건**:
```yaml
docst:
  rag:
    neo4j:
      enabled: true
```

**초기화되는 인덱스** (ApplicationReadyEvent 시점):
```cypher
-- 1. Fulltext 검색용 인덱스
CREATE FULLTEXT INDEX chunk_content_fulltext IF NOT EXISTS
FOR (c:Chunk) ON EACH [c.content, c.headingPath]

-- 2. Entity 이름 검색용
CREATE INDEX entity_name_index IF NOT EXISTS
FOR (e:Entity) ON (e.name)

-- 3. Chunk ID 조회용
CREATE INDEX chunk_id_index IF NOT EXISTS
FOR (c:Chunk) ON (c.chunkId)

-- 4. Document ID 필터링용
CREATE INDEX document_id_index IF NOT EXISTS
FOR (d:Document) ON (d.documentId)
```

---

### 2.2 Graph 데이터 모델

#### Node Types

| Node | Properties | 설명 |
|------|-----------|------|
| **Chunk** | chunkId, documentId, projectId, content, headingPath, chunkIndex | 문서 청크 |
| **Entity** | name, type, description | 추출된 엔티티 |
| **Document** | documentId, path, title | 문서 메타데이터 |

#### Relationship Types

| Relationship | From → To | 설명 |
|--------------|-----------|------|
| `HAS_ENTITY` | Chunk → Entity | 청크가 엔티티를 포함 |
| `BELONGS_TO` | Chunk → Document | 청크가 문서에 소속 |
| `RELATED_TO` | Entity → Entity | 일반 연관 관계 |
| `DEPENDS_ON` | Entity → Entity | 의존 관계 |
| `USES` | Entity → Entity | 사용 관계 |
| `PART_OF` | Entity → Entity | 구성 요소 관계 |

#### Entity Types

| Type | 설명 | 예시 |
|------|------|------|
| `Concept` | 개념/용어 | "RAG", "Vector Search" |
| `API` | API/엔드포인트 | "REST API", "GraphQL" |
| `Component` | 컴포넌트/모듈 | "SearchService", "Neo4jDriver" |
| `Technology` | 기술/도구 | "Neo4j", "PostgreSQL" |

---

### 2.3 Entity Extraction Service

**파일**: `backend/src/main/java/com/docst/rag/EntityExtractionService.java`

**핵심 메서드**:
```java
public ExtractionResult extractEntitiesAndRelations(
    String content,
    String headingPath,
    String extractionModel  // 동적 모델 선택
)
```

**LLM 설정**:
- **기본 모델**: `gpt-4o-mini`
- **Temperature**: 0.0 (일관성 보장)
- **출력 형식**: JSON

**추출 결과 구조**:
```java
record ExtractionResult(
    List<EntityInfo> entities,    // [{name, type, description}]
    List<RelationInfo> relations  // [{source, target, type, description}]
)
```

**에러 처리**:
- Markdown 코드 블록 제거 (```json...```)
- JSON 파싱 실패 시 빈 결과 반환 (graceful degradation)

---

### 2.4 Graph Search Strategy

**파일**: `backend/src/main/java/com/docst/rag/neo4j/Neo4jSearchStrategy.java`

#### 검색 로직 (search 메서드)

**Stage 1: Neo4j Fulltext Search**
```cypher
CALL db.index.fulltext.queryNodes('chunk_content_fulltext', $query)
YIELD node AS chunk, score
WHERE chunk.projectId = $projectId
RETURN chunk.chunkId AS chunkId, chunk.content AS content,
       chunk.headingPath AS headingPath, score
ORDER BY score DESC
LIMIT $topK
```

**Stage 2: PostgreSQL Enrichment**
- Neo4j에서 chunkId와 score만 조회
- PostgreSQL의 `dm_doc_chunk` 테이블에서 전체 데이터 로드
- 문서 메타데이터 조합

#### 인덱싱 로직 (indexDocument 메서드)

```
DocumentVersion 입력
    ↓
1. DocChunk 목록 조회 (PostgreSQL)
    ↓
2. 각 Chunk에 대해:
    ├─ Chunk 노드 생성/병합 (Neo4j)
    ├─ Entity 추출 (LLM)
    ├─ Entity 노드 생성/병합 (Neo4j)
    └─ Entity 관계 생성 (Neo4j)
    ↓
3. Document 노드 연결 (BELONGS_TO)
```

---

### 2.5 Text-to-Cypher Service

**파일**: `backend/src/main/java/com/docst/rag/neo4j/Text2CypherService.java`

**핵심 메서드**:
```java
public String generateCypher(String question, String model)
```

**Self-Healing 재시도 메커니즘**:
- 최대 3회 재시도
- Cypher 실행 오류 시 에러 메시지를 프롬프트에 추가하여 재생성
- 유효성 검증: `LIMIT 1`로 실행하여 문법 확인

**제공되는 스키마 정보**:
```
- (:Chunk {chunkId, documentId, content, headingPath, embedding: Vector})
- (:Entity {name, type, description})
- (:Document {documentId, path, title})
- (Chunk)-[:HAS_ENTITY]->(Entity)
- (Entity)-[:RELATED_TO|DEPENDS_ON|USES|PART_OF]->(Entity)
- (Chunk)-[:BELONGS_TO]->(Document)
```

---

### 2.6 Hybrid Search Service

**파일**: `backend/src/main/java/com/docst/service/HybridSearchService.java`

**Fusion Strategies**:

| Strategy | 알고리즘 | 상태 |
|----------|---------|------|
| **RRF** | `Score(d) = Σ 1/(k + rank(d))` | ✅ 구현됨 |
| **WeightedSum** | `Score = w1*vector + w2*graph` | 🔄 프레임워크만 |

**Fusion Parameters**:
```java
record FusionParams(
    int rrfK,              // RRF 상수 (기본: 60)
    double vectorWeight,   // Vector 가중치
    double graphWeight,    // Graph 가중치
    int topK               // 결과 제한
)
```

---

### 2.7 RAG Configuration

**파일**: `backend/src/main/java/com/docst/rag/RagConfigService.java`

**설정 우선순위**:
```
Request Parameters  →  Project Settings  →  Global Defaults
    (API 호출)         (Project.ragConfig)   (application.yml)
```

**Project.ragConfig (JSONB) 구조**:
```json
{
  "version": "1.1",
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

## 3. 구현 현황

### ✅ 완료된 기능

| 기능 | 설명 | 파일 |
|------|------|------|
| Neo4j 드라이버 관리 | 동적 연결, 세션 관리 | `Neo4jDriverManager.java` |
| 인덱스 초기화 | Fulltext, Entity, Chunk 인덱스 | `Neo4jConfig.java` |
| Entity 추출 | LLM 기반 엔티티/관계 추출 | `EntityExtractionService.java` |
| Graph 구축 | Chunk, Entity, Document 노드 생성 | `Neo4jSearchStrategy.java` |
| Fulltext 검색 | Neo4j Fulltext Index 쿼리 | `Neo4jSearchStrategy.java` |
| Hybrid Fusion | RRF 알고리즘 구현 | `HybridSearchService.java` |
| 설정 관리 | 3단계 설정 우선순위 | `RagConfigService.java` |
| Text-to-Cypher | 자연어 → Cypher 변환 | `Text2CypherService.java` |

### 🔄 부분 구현

| 기능 | 현황 | 누락 사항 |
|------|------|----------|
| **Text-to-Cypher** | 서비스 구현됨 | API endpoint 없음 |
| **Graph Hop 탐색** | maxHop 파라미터 존재 | 실제 탐색 로직 없음 |
| **WeightedSum Fusion** | 인터페이스 정의됨 | 구현체 없음 |

### ❌ 미구현

| 기능 | 설명 |
|------|------|
| **NL Query Execution** | Text2Cypher로 생성된 쿼리 실행 endpoint |
| **Entity Expansion** | 초기 결과에서 관련 엔티티로 확장 검색 |
| **QueryRouter (Auto Mode)** | 쿼리 특성에 따른 최적 검색 모드 자동 선택 |
| **Vector Index (Neo4j)** | Neo4j 자체 벡터 인덱스 (현재 PgVector만 사용) |

---

## 4. 데이터 흐름

### 4.1 문서 인덱싱 흐름

```
Repository Sync 시작
    │
    ▼
GitSyncService.processDocument()
    │
    ├─► ChunkingService: 문서 → 청크 분할
    │       └─► dm_doc_chunk (PostgreSQL)
    │
    ├─► EmbeddingService: 청크 → 벡터 임베딩
    │       └─► dm_doc_embedding (PostgreSQL/PgVector)
    │
    └─► Neo4jSearchStrategy.indexDocument()
            ├─► Chunk 노드 생성 (Neo4j)
            ├─► EntityExtractionService: 엔티티 추출 (LLM)
            ├─► Entity 노드 생성 (Neo4j)
            └─► 관계 생성 (Neo4j)
```

### 4.2 검색 흐름

```
GET /api/projects/{id}/search?q=query&mode=graph
    │
    ▼
SearchController.search()
    │
    ├─ mode=keyword → SearchService (PostgreSQL ILIKE)
    ├─ mode=semantic → PgVectorSearchStrategy (PgVector)
    ├─ mode=graph → Neo4jSearchStrategy (Neo4j Fulltext)
    └─ mode=hybrid → HybridSearchService (RRF Fusion)
            │
            ├─► PgVectorSearch (Vector 결과)
            └─► Neo4jSearch (Graph 결과)
                    │
                    ▼
                RRF Fusion
                    │
                    ▼
                SearchResult[]
```

---

## 5. 관련 파일 목록

### Core RAG

| 파일 | 역할 |
|------|------|
| `rag/RagSearchStrategy.java` | 검색 전략 인터페이스 |
| `rag/RagConfigService.java` | RAG 설정 관리 |
| `rag/ResolvedRagConfig.java` | 설정 값 객체 |
| `rag/EntityExtractionService.java` | LLM 엔티티 추출 |

### Neo4j Integration

| 파일 | 역할 |
|------|------|
| `rag/neo4j/Neo4jConfig.java` | Neo4j 설정 및 인덱스 초기화 |
| `rag/neo4j/Neo4jSearchStrategy.java` | Graph 검색 전략 |
| `rag/neo4j/Text2CypherService.java` | 자연어 → Cypher 변환 |

### PgVector Integration

| 파일 | 역할 |
|------|------|
| `rag/pgvector/PgVectorSearchStrategy.java` | Vector 검색 전략 |
| `rag/pgvector/PgVectorDataSourceManager.java` | DataSource 관리 |

### Services

| 파일 | 역할 |
|------|------|
| `service/SemanticSearchService.java` | 의미 검색 |
| `service/HybridSearchService.java` | Hybrid 검색 (RRF) |
| `service/ChunkingService.java` | 문서 청킹 |
| `service/EmbeddingService.java` | 벡터 임베딩 |

### Domain Entities

| 파일 | 역할 |
|------|------|
| `domain/DocChunk.java` | 청크 엔티티 |
| `domain/DocEntity.java` | 엔티티 백업 (PostgreSQL) |
| `domain/DocEntityRelation.java` | 관계 백업 (PostgreSQL) |

---

## 6. 개선 필요 사항

### 6.1 MCP Tools 연동 관점

현재 Graph RAG 기능이 REST API로는 제공되지만 MCP Tools로는 노출되지 않음:

| 기능 | REST | MCP | 필요성 |
|------|------|-----|--------|
| Graph Search | ✅ `mode=graph` | ❌ | **높음** |
| Hybrid Search | ✅ `mode=hybrid` | ❌ | **높음** |
| Entity 조회 | ❌ | ❌ | 중간 |
| Graph 탐색 | ❌ | ❌ | 중간 |

### 6.2 미완성 기능

| 기능 | 우선순위 | 설명 |
|------|---------|------|
| Text-to-Cypher Endpoint | 높음 | 자연어 그래프 쿼리 |
| Graph Hop Traversal | 높음 | maxHop 기반 엔티티 확장 |
| Auto Mode QueryRouter | 중간 | 최적 검색 모드 자동 선택 |
| WeightedSum Strategy | 낮음 | 가중치 기반 융합 |

### 6.3 테스트 커버리지

| 테스트 파일 | 커버리지 |
|------------|---------|
| `EntityExtractionServiceTest.java` | JSON 파싱, 엔티티 검증 |
| `Neo4jSearchStrategyTest.java` | Fulltext 검색, 인덱싱 |
| `Text2CypherServiceTest.java` | 쿼리 생성, 검증 |

---

## 7. 결론

Docst의 Graph RAG 시스템은 **Phase 4 수준의 production-ready** 상태입니다:

**강점**:
- 깔끔한 Strategy Pattern 기반 검색 모드 분리
- LLM 기반 엔티티 추출 파이프라인 완성
- RRF 기반 Hybrid 검색 구현
- 3단계 설정 우선순위 시스템

**개선 필요**:
- Graph Hop 탐색 실제 구현
- Text-to-Cypher API endpoint
- MCP Tools 연동
- Auto Mode QueryRouter

이 분석을 바탕으로 Phase 11에서 MCP Tools 확장 시 Graph RAG 관련 Tool을 추가할 수 있습니다.