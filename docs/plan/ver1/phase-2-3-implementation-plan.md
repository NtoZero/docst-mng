# Phase 2-3: 미구현 기능 통합 구현 계획 (Spring AI 기반)

> **작성일**: 2025-12-27
> **수정일**: 2025-12-27 (Spring AI 1.1.0+ 통합 전략 반영)
> **현재 상태**: Phase 1 MVP 완료, Phase 2-A 청킹 완료, Phase 2-B/C 0% 구현
> **목표**: 의미 검색, OAuth, Webhook, 문서 그래프 기능 구현

---

## 🔄 전략 변경: Spring AI 통합

**기존 계획**: pgvector 라이브러리 직접 사용, 커스텀 EmbeddingProvider 구현
**변경 계획**: **Spring AI 1.1.0+** 를 베이스로 pgvector + 임베딩 모델 통합

### Spring AI 도입 이유

1. **표준화된 추상화**
   - `VectorStore` 인터페이스: pgvector, Pinecone, Chroma 등 통일된 API
   - `EmbeddingModel` 인터페이스: Ollama, OpenAI, Azure 등 자동 전환
   - `Document` 모델: 벡터 DB용 표준 도메인 객체

2. **Phase 4 Graph RAG 일관성**
   - Neo4j VectorStore도 Spring AI로 제공
   - 하이브리드 RAG 구현 시 일관된 아키텍처 유지

3. **Spring 생태계 통합**
   - Auto-configuration: `PgVectorStore` 빈 자동 생성
   - `@ConfigurationProperties`: YAML 기반 설정
   - Transaction 관리, Connection Pool 자동 처리

4. **유지보수성**
   - Spring 팀이 pgvector 드라이버 업데이트 대응
   - 커뮤니티 레퍼런스 풍부

### 아키텍처 변경점

| 계층 | 기존 계획 | Spring AI 계획 |
|------|----------|---------------|
| **의존성** | pgvector:0.1.6, webflux | spring-ai-pgvector-store-spring-boot-starter |
| **임베딩** | 커스텀 `OllamaEmbeddingProvider` | `OllamaEmbeddingModel` 자동 주입 |
| **VectorStore** | Native SQL + JPA Repository | Spring AI `VectorStore` 인터페이스 |
| **청킹** | 커스텀 `MarkdownChunker` | `MarkdownChunker` 유지 + `TokenTextSplitter` 옵션 |
| **검색** | Native Query | `VectorStore.similaritySearch()` |

---

## 현재 구현 상황 분석

### 완료된 기능 (Phase 1)
- [x] PostgreSQL + JPA 연동
- [x] JGit 기반 Git 동기화 (Full/Incremental)
- [x] REST API 구현 (Projects, Repositories, Documents, Search)
- [x] MCP Tools 기초 (list_documents, get_document, diff_document, search_documents)
- [x] 키워드 검색 (ILIKE 패턴)
- [x] 자격증명 관리 (GitHub PAT, Basic Auth)
- [x] 프론트엔드 기본 UI (프로젝트, 레포, 문서, 검색)
- [x] 다국어 지원 (i18n)
- [x] 로컬 로그인 (이메일 기반)
- [x] 역할 모델 엔티티 (ProjectRole, ProjectMember)

### 미구현 기능

#### Phase 2: 의미 검색 (0%)
| 항목 | 상태 | 비고 |
|------|------|------|
| DocChunk 엔티티 | ❌ | 청킹 인프라 없음 |
| DocEmbedding 엔티티 | ❌ | pgvector 미설정 |
| ChunkingService | ❌ | 청킹 로직 없음 |
| EmbeddingProvider | ❌ | 임베딩 API 연동 없음 |
| HybridSearchService | ❌ | RRF 병합 로직 없음 |
| 프론트엔드 검색 모드 | ❌ | mode='keyword' 하드코딩 |

#### Phase 3: 고급 기능 (0%)
| 항목 | 상태 | 비고 |
|------|------|------|
| GitHub OAuth | ❌ | 로컬 로그인만 존재 |
| JWT 인증 | ❌ | "dev-token-" 하드코딩 |
| Webhook 자동 동기화 | ❌ | webhook/ 패키지 없음 |
| 문서 관계 그래프 | ❌ | graph/ 패키지 없음 |
| 영향 분석 | ❌ | 미구현 |
| 권한 체크 AOP | ❌ | 역할 모델만 존재, 미적용 |

---

## 구현 우선순위 제안

현재 상황을 고려하여 다음 순서로 구현을 권장합니다:

```
1. [우선] Phase 2-A: 청킹 시스템 (의미 검색의 기반)
2. [우선] Phase 2-B: 임베딩 시스템 (pgvector 연동)
3. [우선] Phase 2-C: 의미/하이브리드 검색
4. [중요] Phase 3-A: JWT 인증 고도화
5. [중요] Phase 3-B: GitHub OAuth
6. [보통] Phase 3-C: Webhook 자동 동기화
7. [보통] Phase 3-D: 문서 관계 그래프
8. [낮음] Phase 3-E: 권한 체크 AOP
```

---

## Phase 2-A: 청킹 시스템 구현 ✅ (완료)

> **상태**: 구현 완료 (2025-12-27)
> **옵션**: Spring AI `TokenTextSplitter` 대체 가능 (Phase 2-B 이후)

### 1. 의존성 추가 ✅

**build.gradle.kts**:
```kotlin
dependencies {
    // Tokenization (tiktoken 호환)
    implementation("com.knuddels:jtokkit:1.0.0")
}
```

**참고**: Phase 2-B 이후 Spring AI `TokenTextSplitter`로 대체 가능
```kotlin
dependencies {
    implementation("org.springframework.ai:spring-ai-transformers-spring-boot-starter")
}
```

### 2. DocChunk 엔티티

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
    private String headingPath;  // "# Title > ## Section"

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "token_count", nullable = false)
    private int tokenCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // Getters, setters, constructors
}
```

### 3. 청킹 서비스

**위치**: `backend/src/main/java/com/docst/chunking/`

| 파일 | 책임 |
|------|------|
| `ChunkingConfig.java` | 청킹 설정 (maxTokens, overlapTokens, minTokens) |
| `TokenCounter.java` | jtokkit 기반 토큰 수 계산 |
| `MarkdownChunker.java` | Flexmark AST 기반 헤딩 분할 |
| `ChunkingService.java` | 오케스트레이션 |

### 4. Flyway 마이그레이션

**파일**: `V5__add_doc_chunk.sql`

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

### 5. 작업 목록

- [ ] DocChunk 엔티티 생성
- [ ] DocChunkRepository 생성
- [ ] TokenCounter 구현 (jtokkit)
- [ ] MarkdownChunker 구현 (Flexmark AST 활용)
- [ ] ChunkingService 구현
- [ ] V5__add_doc_chunk.sql 마이그레이션
- [ ] SyncService에 청킹 파이프라인 통합
- [ ] 단위 테스트

---

## Phase 2-B: 임베딩 시스템 구현 (Spring AI 기반)

> **전략 변경**: Spring AI 1.1.0+을 활용하여 pgvector, 임베딩 모델, 벡터 스토어를 통합

### 1. Spring AI 의존성 추가

**build.gradle.kts**:
```kotlin
dependencies {
    // Spring AI BOM
    implementation(platform("org.springframework.ai:spring-ai-bom:1.0.0-M5"))

    // Spring AI 핵심 모듈
    implementation("org.springframework.ai:spring-ai-pgvector-store-spring-boot-starter")
    implementation("org.springframework.ai:spring-ai-ollama-spring-boot-starter")

    // 선택: OpenAI 지원
    // implementation("org.springframework.ai:spring-ai-openai-spring-boot-starter")
}
```

### 2. 아키텍처 개요

Spring AI는 다음을 제공:
- **자동 설정**: `PgVectorStore` 빈 자동 생성
- **EmbeddingModel 추상화**: Ollama/OpenAI 등 통일된 인터페이스
- **VectorStore 인터페이스**: `add()`, `similaritySearch()` 등 표준 API
- **Document/Metadata 모델**: 벡터 DB용 표준 도메인 객체

**우리의 통합 전략**:
1. **DocChunk** 엔티티는 유지 (문서 구조 추적용)
2. Spring AI의 `VectorStore`를 활용하여 임베딩 저장/검색
3. Spring AI의 `Document`와 우리의 `DocChunk` 매핑

### 3. Flyway 마이그레이션

**파일**: `V6__add_spring_ai_vector_store.sql`

```sql
-- pgvector 확장 활성화
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Spring AI VectorStore 테이블 (기본 스키마)
CREATE TABLE IF NOT EXISTS vector_store (
    id uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    content text,
    metadata json,
    embedding vector(768)  -- nomic-embed-text 기본 차원
);

-- HNSW 인덱스 (cosine distance)
CREATE INDEX ON vector_store USING HNSW (embedding vector_cosine_ops);

-- DocChunk와 VectorStore 연결용 확장
-- metadata JSON에 doc_chunk_id를 저장하여 연결
COMMENT ON TABLE vector_store IS 'Spring AI VectorStore table. metadata.doc_chunk_id links to dm_doc_chunk.id';
```

### 4. application.yml 설정

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/docst
    username: postgres
    password: postgres

  ai:
    # Ollama 임베딩 설정
    ollama:
      base-url: http://localhost:11434
      embedding:
        options:
          model: nomic-embed-text  # 768 dimensions
      init:
        pull-model-strategy: when_missing
        embedding:
          additional-models:
            - mxbai-embed-large  # 1024 dimensions

    # PgVector 벡터 스토어 설정
    vectorstore:
      pgvector:
        index-type: HNSW
        distance-type: COSINE_DISTANCE
        dimensions: 768  # nomic-embed-text 기본값
        remove-existing-vector-store-table: false
        schema-name: public
        table-name: vector_store

# 커스텀 설정
docst:
  embedding:
    batch-size: 32
    enabled: true
```

### 5. 서비스 구조 (Spring AI 기반)

**위치**: `backend/src/main/java/com/docst/embedding/`

```
embedding/
├── EmbeddingConfig.java           # VectorStore 커스터마이징 (선택)
├── DocstEmbeddingService.java     # DocChunk → Spring AI Document 변환
└── EmbeddingJobService.java       # 비동기 배치 임베딩
```

### 6. EmbeddingService 구현 예시

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
        // DocChunk를 Spring AI Document로 변환
        List<org.springframework.ai.document.Document> documents = chunks.stream()
            .map(chunk -> new org.springframework.ai.document.Document(
                chunk.getId().toString(),  // ID
                chunk.getContent(),         // 임베딩할 텍스트
                Map.of(
                    "doc_chunk_id", chunk.getId().toString(),
                    "heading_path", chunk.getHeadingPath(),
                    "document_version_id", chunk.getDocumentVersion().getId().toString(),
                    "token_count", chunk.getTokenCount()
                )
            ))
            .toList();

        // Spring AI VectorStore에 자동 임베딩 및 저장
        vectorStore.add(documents);
    }

    /**
     * 의미 검색 수행
     */
    public List<DocChunk> semanticSearch(String query, int topK) {
        // Spring AI의 VectorStore 검색
        List<org.springframework.ai.document.Document> results =
            vectorStore.similaritySearch(
                SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .similarityThreshold(0.7)
                    .build()
            );

        // Spring AI Document → DocChunk 변환
        List<UUID> chunkIds = results.stream()
            .map(doc -> UUID.fromString(doc.getMetadata().get("doc_chunk_id").toString()))
            .toList();

        return docChunkRepository.findAllById(chunkIds);
    }
}
```

### 7. 작업 목록

- [ ] Spring AI BOM 및 스타터 의존성 추가
- [ ] V6__add_spring_ai_vector_store.sql 마이그레이션 작성
- [ ] application.yml에 Spring AI 설정 추가
- [ ] DocstEmbeddingService 구현
- [ ] EmbeddingJobService 구현 (비동기 배치)
- [ ] GitSyncService에 임베딩 파이프라인 통합
- [ ] docker-compose.yml에 Ollama 서비스 추가
- [ ] Ollama 모델 자동 pull 설정 테스트

### 8. Spring AI vs 직접 구현 비교

| 항목 | Spring AI 방식 | 직접 구현 방식 |
|------|---------------|--------------|
| **VectorStore** | 자동 설정, 표준 API | 수동 Repository + Native Query |
| **임베딩** | EmbeddingModel 자동 주입 | HTTP 클라이언트 직접 구현 |
| **Provider 전환** | 설정 변경만으로 Ollama ↔ OpenAI | 코드 수정 필요 |
| **Document 모델** | Spring AI 표준 | 커스텀 DTO |
| **유지보수** | Spring 생태계 통합 | 독립적 관리 |
| **유연성** | 중간 (추상화 제약) | 높음 (완전 제어) |

**선택 이유**: Phase 4 Graph RAG에서 Neo4j VectorStore도 Spring AI로 통합할 예정이므로, 일관된 아키텍처 유지

---

## Phase 2-C: 의미/하이브리드 검색 구현 (Spring AI 기반)

### 1. 의미 검색 (Spring AI VectorStore)

Spring AI의 `VectorStore.similaritySearch()`를 활용하여 Native Query 없이 벡터 검색 수행:

```java
@Service
@RequiredArgsConstructor
public class SemanticSearchService {

    private final VectorStore vectorStore;

    public List<SearchResult> searchSemantic(UUID projectId, String query, int topK) {
        // Spring AI SearchRequest 구성
        SearchRequest request = SearchRequest.builder()
            .query(query)
            .topK(topK)
            .similarityThreshold(0.7)
            // Project 필터링
            .filterExpression(Filter.builder()
                .key("project_id")
                .value(projectId.toString())
                .build())
            .build();

        // 벡터 검색 실행
        List<org.springframework.ai.document.Document> results =
            vectorStore.similaritySearch(request);

        // SearchResult DTO로 변환
        return results.stream()
            .map(doc -> new SearchResult(
                UUID.fromString(doc.getMetadata().get("doc_chunk_id").toString()),
                doc.getContent(),
                doc.getMetadata().get("heading_path").toString(),
                (Double) doc.getMetadata().get("distance")  // 유사도 점수
            ))
            .toList();
    }
}
```

### 2. HybridSearchService (RRF 융합)

**위치**: `backend/src/main/java/com/docst/service/HybridSearchService.java`

RRF (Reciprocal Rank Fusion) 기반 점수 병합:
- 키워드 결과 + 의미 결과 병합
- `score = sum(1 / (k + rank))` 공식 적용
- k = 60 상수 사용

```java
@Service
@RequiredArgsConstructor
public class HybridSearchService {

    private final KeywordSearchService keywordSearchService;
    private final SemanticSearchService semanticSearchService;

    private static final int RRF_K = 60;

    public List<SearchResult> hybridSearch(UUID projectId, String query, int topK) {
        // 키워드 검색
        List<SearchResult> keywordResults =
            keywordSearchService.search(projectId, query, topK * 2);

        // 의미 검색
        List<SearchResult> semanticResults =
            semanticSearchService.searchSemantic(projectId, query, topK * 2);

        // RRF 점수 계산 및 병합
        Map<UUID, Double> rrfScores = new HashMap<>();

        // 키워드 결과 점수 추가
        for (int i = 0; i < keywordResults.size(); i++) {
            UUID chunkId = keywordResults.get(i).chunkId();
            double score = 1.0 / (RRF_K + i + 1);
            rrfScores.merge(chunkId, score, Double::sum);
        }

        // 의미 결과 점수 추가
        for (int i = 0; i < semanticResults.size(); i++) {
            UUID chunkId = semanticResults.get(i).chunkId();
            double score = 1.0 / (RRF_K + i + 1);
            rrfScores.merge(chunkId, score, Double::sum);
        }

        // 점수 기준 정렬 및 상위 topK 반환
        return rrfScores.entrySet().stream()
            .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
            .limit(topK)
            .map(entry -> findResultByChunkId(
                entry.getKey(),
                keywordResults,
                semanticResults
            ))
            .toList();
    }
}
```

### 3. 프론트엔드 검색 모드 UI

**수정 파일**: `frontend/app/[locale]/projects/[projectId]/search/page.tsx`

```tsx
// 검색 모드 선택 추가
<Select value={mode} onValueChange={setMode}>
  <SelectItem value="keyword">Keyword</SelectItem>
  <SelectItem value="semantic">Semantic (AI)</SelectItem>
  <SelectItem value="hybrid">Hybrid (Recommended)</SelectItem>
</Select>

// 검색 결과에 headingPath 표시
{results.map(result => (
  <div key={result.chunkId}>
    <div className="text-sm text-muted-foreground">
      {result.headingPath}
    </div>
    <div>{result.content}</div>
    <div className="text-xs">Score: {result.score.toFixed(3)}</div>
  </div>
))}
```

### 4. MCP search_documents 확장

**수정 파일**: `McpController.java`

```java
@PostMapping("/search_documents")
public McpResponse searchDocuments(@RequestBody SearchDocumentsRequest request) {
    String mode = request.mode() != null ? request.mode() : "keyword";

    List<SearchResult> results = switch (mode) {
        case "semantic" -> semanticSearchService.searchSemantic(
            request.projectId(),
            request.query(),
            request.topK()
        );
        case "hybrid" -> hybridSearchService.hybridSearch(
            request.projectId(),
            request.query(),
            request.topK()
        );
        default -> keywordSearchService.search(
            request.projectId(),
            request.query(),
            request.topK()
        );
    };

    return McpResponse.success(results);
}
```

### 5. Spring AI Filter Expression 활용

프로젝트/레포지토리 필터링을 Spring AI의 Filter 표현식으로 처리:

```java
// Project 필터
Filter projectFilter = Filter.builder()
    .key("project_id")
    .value(projectId.toString())
    .build();

// Repository 필터 (선택)
Filter repoFilter = Filter.builder()
    .key("repository_id")
    .value(repositoryId.toString())
    .build();

// AND 조건 결합
Filter combinedFilter = Filter.and(projectFilter, repoFilter);

SearchRequest request = SearchRequest.builder()
    .query(query)
    .topK(topK)
    .filterExpression(combinedFilter)
    .build();
```

### 6. 작업 목록

- [ ] SearchResult DTO 정의
- [ ] SemanticSearchService 구현 (Spring AI VectorStore)
- [ ] HybridSearchService 구현 (RRF)
- [ ] SearchController mode 파라미터 처리
- [ ] McpController search_documents 확장
- [ ] 프론트엔드 검색 모드 셀렉트 추가
- [ ] 검색 결과에 headingPath/score 표시
- [ ] Filter Expression 프로젝트 필터링 테스트
- [ ] E2E 테스트

---

## Phase 3-A: JWT 인증 고도화

### 1. 의존성 추가

**build.gradle.kts**:
```kotlin
dependencies {
    implementation("io.jsonwebtoken:jjwt-api:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.5")
}
```

### 2. JWT 서비스

**위치**: `backend/src/main/java/com/docst/auth/`

| 파일 | 책임 |
|------|------|
| `JwtService.java` | JWT 생성, 검증, 파싱 |
| `JwtAuthenticationFilter.java` | 요청 헤더에서 토큰 추출 및 인증 |
| `JwtConfig.java` | 시크릿, 만료시간 설정 |

### 3. 설정

**application.yml 추가**:
```yaml
docst:
  jwt:
    secret: ${JWT_SECRET:your-256-bit-secret-key-here}
    expiration: 86400  # 24시간 (초)
```

### 4. 작업 목록

- [ ] JwtConfig 설정 클래스 생성
- [ ] JwtService 구현 (generateToken, validateToken, parseToken)
- [ ] JwtAuthenticationFilter 구현
- [ ] SecurityConfig에 필터 등록
- [ ] AuthController에서 실제 JWT 발급
- [ ] 기존 "dev-token-" 하드코딩 제거
- [ ] 토큰 갱신 엔드포인트 (선택)

---

## Phase 3-B: GitHub OAuth 연동

### 1. 의존성 추가

**build.gradle.kts**:
```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
}
```

### 2. GitHub OAuth 서비스

**위치**: `backend/src/main/java/com/docst/auth/`

| 파일 | 책임 |
|------|------|
| `GitHubOAuthService.java` | OAuth 플로우 처리 |
| `GitHubOAuthController.java` | /api/auth/github/* 엔드포인트 |

### 3. OAuth 플로우

```
1. GET /api/auth/github/start
   → GitHub authorize URL 반환 (state 토큰 포함)

2. 사용자가 GitHub에서 로그인 및 승인

3. GET /api/auth/github/callback?code=...&state=...
   → code로 access_token 교환
   → GitHub API로 사용자 정보 조회
   → User 레코드 생성/업데이트 (provider='GITHUB')
   → JWT 발급 및 프론트엔드로 리다이렉트
```

### 4. 설정

**application.yml 추가**:
```yaml
docst:
  github:
    client-id: ${GITHUB_CLIENT_ID}
    client-secret: ${GITHUB_CLIENT_SECRET}
    callback-url: ${GITHUB_CALLBACK_URL:http://localhost:3000/auth/callback}
```

### 5. 프론트엔드 OAuth 페이지

**추가 파일**:
- `frontend/app/[locale]/login/page.tsx` - GitHub 로그인 버튼 추가
- `frontend/app/auth/callback/page.tsx` - OAuth 콜백 처리

### 6. 작업 목록

- [ ] GitHubOAuthService 구현
- [ ] GitHubOAuthController 구현
- [ ] User 엔티티에 GitHub 정보 필드 확인 (provider, providerUserId)
- [ ] 프론트엔드 GitHub 로그인 버튼 추가
- [ ] OAuth 콜백 페이지 구현
- [ ] 토큰 저장 로직 (localStorage/cookie)
- [ ] application.yml GitHub 설정 추가

---

## Phase 3-C: Webhook 자동 동기화

### 1. Webhook 컨트롤러

**위치**: `backend/src/main/java/com/docst/webhook/`

| 파일 | 책임 |
|------|------|
| `GitHubWebhookController.java` | POST /webhook/github 엔드포인트 |
| `WebhookService.java` | 시그니처 검증, 이벤트 처리 |
| `WebhookConfig.java` | Webhook 시크릿 설정 |

### 2. Push 이벤트 처리

```java
public void handlePush(GitHubPushEvent event) {
    // 1. 레포지토리 조회 (external_id 기준)
    // 2. default branch 확인
    // 3. 변경된 문서 파일 필터링
    // 4. 증분 동기화 실행
}
```

### 3. 설정

**application.yml 추가**:
```yaml
docst:
  webhook:
    secret: ${WEBHOOK_SECRET:your-webhook-secret}
```

### 4. 작업 목록

- [ ] GitHubWebhookController 구현
- [ ] WebhookService 구현 (시그니처 검증)
- [ ] Push 이벤트 DTO 정의
- [ ] 증분 동기화 연동
- [ ] Repository 엔티티에 webhook_id 필드 추가 (선택)
- [ ] Webhook 등록 자동화 서비스 (선택)

---

## Phase 3-D: 문서 관계 그래프

### 1. DocumentRelation 엔티티

**위치**: `backend/src/main/java/com/docst/domain/DocumentRelation.java`

```java
@Entity
@Table(name = "dm_document_relation")
public class DocumentRelation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_document_id", nullable = false)
    private Document sourceDocument;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_document_id")
    private Document targetDocument;

    @Column(name = "target_path")
    private String targetPath;  // broken link 시

    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type", nullable = false)
    private RelationType relationType;

    @Column(name = "link_text")
    private String linkText;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

public enum RelationType {
    REFERENCES, IMPORTS, EXTENDS
}
```

### 2. 그래프 서비스

**위치**: `backend/src/main/java/com/docst/graph/`

| 파일 | 책임 |
|------|------|
| `DocumentLinkExtractor.java` | Markdown 링크 추출 |
| `DocumentGraphService.java` | 그래프 조회 API |
| `ImpactAnalysisService.java` | 영향 분석 (역방향 탐색) |

### 3. Flyway 마이그레이션

**파일**: `V7__add_document_relation.sql`

```sql
CREATE TABLE dm_document_relation (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    source_document_id uuid NOT NULL REFERENCES dm_document(id) ON DELETE CASCADE,
    target_document_id uuid REFERENCES dm_document(id) ON DELETE SET NULL,
    target_path text,
    relation_type text NOT NULL,
    link_text text,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_relation_source ON dm_document_relation(source_document_id);
CREATE INDEX idx_relation_target ON dm_document_relation(target_document_id);
```

### 4. 그래프 API

```
GET /api/projects/{projectId}/graph
GET /api/projects/{projectId}/graph/document/{docId}?depth=2
GET /api/documents/{docId}/impact?maxDepth=3
```

### 5. 프론트엔드 그래프 시각화

**추가 의존성**:
```json
{
  "react-force-graph": "^1.44.0"
}
```

**추가 파일**:
- `frontend/app/[locale]/projects/[projectId]/graph/page.tsx`
- `frontend/components/document-graph.tsx`

### 6. 작업 목록

- [ ] DocumentRelation 엔티티 생성
- [ ] DocumentRelationRepository 생성
- [ ] DocumentLinkExtractor 구현 (정규식 기반)
- [ ] DocumentGraphService 구현
- [ ] ImpactAnalysisService 구현
- [ ] DocumentGraphController 구현
- [ ] V7__add_document_relation.sql 마이그레이션
- [ ] SyncService에 링크 추출 통합
- [ ] 프론트엔드 그래프 페이지 구현
- [ ] react-force-graph 연동

---

## Phase 3-E: 권한 체크 AOP

### 1. 권한 어노테이션

**위치**: `backend/src/main/java/com/docst/security/`

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresProjectRole {
    ProjectRole value();
}
```

### 2. 권한 체크 AOP

```java
@Aspect
@Component
public class ProjectAuthorizationAspect {

    @Before("@annotation(requiresProjectRole)")
    public void checkProjectRole(JoinPoint joinPoint, RequiresProjectRole requiresProjectRole) {
        UUID projectId = extractProjectId(joinPoint);
        UUID userId = getCurrentUserId();
        ProjectRole requiredRole = requiresProjectRole.value();

        ProjectMember member = memberRepository.findByProjectIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AccessDeniedException("Not a project member"));

        if (!member.getRole().hasPermission(requiredRole)) {
            throw new AccessDeniedException("Insufficient permissions");
        }
    }
}
```

### 3. 작업 목록

- [ ] RequiresProjectRole 어노테이션 생성
- [ ] ProjectRole에 hasPermission() 메서드 추가
- [ ] ProjectAuthorizationAspect 구현
- [ ] 컨트롤러 메서드에 어노테이션 적용
- [ ] 예외 핸들러에 AccessDeniedException 처리 추가

---

## 멤버 관리 API (Phase 3 추가)

### 1. 엔드포인트

```
GET    /api/projects/{projectId}/members       # 멤버 목록
POST   /api/projects/{projectId}/members       # 멤버 추가
PUT    /api/projects/{projectId}/members/{id}  # 역할 변경
DELETE /api/projects/{projectId}/members/{id}  # 멤버 제거
```

### 2. 프론트엔드 멤버 관리

**추가 파일**:
- `frontend/app/[locale]/projects/[projectId]/settings/page.tsx`
- `frontend/app/[locale]/projects/[projectId]/settings/members/page.tsx`
- `frontend/components/member-list.tsx`

### 3. 작업 목록

- [ ] ProjectMemberController 구현
- [ ] MemberService 구현
- [ ] 프론트엔드 멤버 관리 페이지
- [ ] 멤버 초대 UI
- [ ] 역할 변경 UI

---

## MCP Tools 확장 (Phase 2-3)

### 새로운 Tools

| Tool | Phase | 설명 |
|------|-------|------|
| `search_documents` (확장) | 2 | semantic/hybrid 모드 지원 |
| `get_document_graph` | 3 | 문서 관계 그래프 조회 |
| `analyze_impact` | 3 | 문서 변경 영향 분석 |
| `get_related_documents` | 3 | 관련 문서 추천 |
| `sync_repository` | 3 | 동기화 트리거 |
| `get_sync_status` | 3 | 동기화 상태 조회 |

---

## Flyway 마이그레이션 요약

| 버전 | 파일 | 내용 | Phase |
|------|------|------|-------|
| V1 | init_schema.sql | 기초 스키마 | 1 (완료) |
| V2 | add_indexes.sql | 인덱스 | 1 (완료) |
| V3 | add_credential.sql | 자격증명 | 1 (완료) |
| V4 | add_sync_mode.sql | 동기화 모드 | 1 (완료) |
| V5 | add_doc_chunk.sql | 청크 테이블 | 2-A |
| V6 | add_doc_embedding.sql | 임베딩 테이블 | 2-B |
| V7 | add_document_relation.sql | 문서 관계 | 3-D |

---

## 패키지 구조 (최종)

```
backend/src/main/java/com/docst/
├── DocstApplication.java
├── api/                          # (기존)
├── domain/
│   ├── DocChunk.java             # 추가 (Phase 2-A)
│   ├── DocEmbedding.java         # 추가 (Phase 2-B)
│   └── DocumentRelation.java     # 추가 (Phase 3-D)
├── repository/
│   ├── DocChunkRepository.java   # 추가 (Phase 2-A)
│   └── DocEmbeddingRepository.java # 추가 (Phase 2-B)
├── chunking/                     # 추가 (Phase 2-A)
│   ├── ChunkingConfig.java
│   ├── TokenCounter.java
│   ├── MarkdownChunker.java
│   └── ChunkingService.java
├── embedding/                    # 추가 (Phase 2-B)
│   ├── EmbeddingConfig.java
│   ├── EmbeddingProvider.java
│   ├── OllamaEmbeddingProvider.java
│   ├── OpenAiEmbeddingProvider.java
│   ├── EmbeddingService.java
│   └── EmbeddingJobService.java
├── auth/                         # 추가 (Phase 3-A, 3-B)
│   ├── JwtConfig.java
│   ├── JwtService.java
│   ├── JwtAuthenticationFilter.java
│   ├── GitHubOAuthService.java
│   └── GitHubOAuthController.java
├── webhook/                      # 추가 (Phase 3-C)
│   ├── GitHubWebhookController.java
│   ├── WebhookService.java
│   └── WebhookConfig.java
├── graph/                        # 추가 (Phase 3-D)
│   ├── DocumentLinkExtractor.java
│   ├── DocumentGraphService.java
│   ├── DocumentGraphController.java
│   └── ImpactAnalysisService.java
├── security/                     # 추가 (Phase 3-E)
│   ├── RequiresProjectRole.java
│   └── ProjectAuthorizationAspect.java
├── service/
│   └── HybridSearchService.java  # 추가 (Phase 2-C)
├── git/                          # (기존)
└── mcp/                          # (기존, 확장)
```

```
frontend/
├── app/
│   ├── auth/
│   │   └── callback/
│   │       └── page.tsx          # 추가 (Phase 3-B)
│   └── [locale]/
│       ├── login/
│       │   └── page.tsx          # 수정 (GitHub 버튼 추가)
│       └── projects/[projectId]/
│           ├── search/
│           │   └── page.tsx      # 수정 (검색 모드 추가)
│           ├── graph/
│           │   └── page.tsx      # 추가 (Phase 3-D)
│           └── settings/
│               ├── page.tsx      # 추가 (Phase 3)
│               └── members/
│                   └── page.tsx  # 추가 (Phase 3)
└── components/
    ├── search-mode-select.tsx    # 추가 (Phase 2-C)
    ├── document-graph.tsx        # 추가 (Phase 3-D)
    └── member-list.tsx           # 추가 (Phase 3)
```

---

## 예상 구현 작업량

| Phase | 주요 작업 | 예상 파일 수 |
|-------|----------|-------------|
| 2-A: 청킹 | 엔티티, 서비스, 마이그레이션 | ~8 파일 |
| 2-B: 임베딩 | 엔티티, 서비스, 프로바이더 | ~10 파일 |
| 2-C: 하이브리드 검색 | 서비스, 프론트엔드 | ~5 파일 |
| 3-A: JWT | 서비스, 필터, 설정 | ~4 파일 |
| 3-B: GitHub OAuth | 서비스, 컨트롤러, 프론트엔드 | ~5 파일 |
| 3-C: Webhook | 컨트롤러, 서비스 | ~4 파일 |
| 3-D: 문서 그래프 | 엔티티, 서비스, 프론트엔드 | ~8 파일 |
| 3-E: 권한 AOP | 어노테이션, Aspect | ~3 파일 |
| **총계** | | **~47 파일** |

---

## 다음 단계

구현을 시작하려면 다음 순서를 권장합니다:

1. **Phase 2-A (청킹)부터 시작** - 의미 검색의 기반
2. **Phase 2-B (임베딩)** - pgvector 연동
3. **Phase 2-C (하이브리드 검색)** - 검색 기능 완성
4. **Phase 3-A (JWT)** - 보안 기반 강화
5. 나머지 Phase 3 기능들

각 Phase 완료 후 테스트를 수행하고, 다음 Phase로 진행하는 것이 좋습니다.
