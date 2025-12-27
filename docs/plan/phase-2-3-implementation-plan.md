# Phase 2-3: 미구현 기능 통합 구현 계획

> **작성일**: 2025-12-27
> **현재 상태**: Phase 1 MVP 완료, Phase 2-3 기능 0% 구현
> **목표**: 의미 검색, OAuth, Webhook, 문서 그래프 기능 구현

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

## Phase 2-A: 청킹 시스템 구현

### 1. 의존성 추가

**build.gradle.kts**:
```kotlin
dependencies {
    // Tokenization (tiktoken 호환)
    implementation("com.knuddels:jtokkit:1.0.0")
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

## Phase 2-B: 임베딩 시스템 구현

### 1. 의존성 추가

**build.gradle.kts**:
```kotlin
dependencies {
    // pgvector
    implementation("org.postgresql:postgresql:42.7.0")
    implementation("com.pgvector:pgvector:0.1.6")

    // HTTP client for Ollama/OpenAI
    implementation("org.springframework.boot:spring-boot-starter-webflux")
}
```

### 2. DocEmbedding 엔티티

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
    private String model;

    @Column(nullable = false, columnDefinition = "vector(1536)")
    private float[] embedding;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
```

### 3. 임베딩 서비스 구조

**위치**: `backend/src/main/java/com/docst/embedding/`

```
embedding/
├── EmbeddingProvider.java          # 인터페이스
├── EmbeddingConfig.java            # 설정 클래스
├── OllamaEmbeddingProvider.java    # Ollama 구현
├── OpenAiEmbeddingProvider.java    # OpenAI 구현
├── EmbeddingService.java           # 오케스트레이션
└── EmbeddingJobService.java        # 비동기 배치 처리
```

### 4. 설정

**application.yml 추가**:
```yaml
docst:
  embedding:
    provider: ollama  # ollama, openai
    model: nomic-embed-text
    dimension: 768
    batch-size: 32

  ollama:
    base-url: http://localhost:11434

  openai:
    api-key: ${OPENAI_API_KEY:}
    model: text-embedding-3-small
```

### 5. Flyway 마이그레이션

**파일**: `V6__add_doc_embedding.sql`

```sql
-- pgvector 확장 활성화
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE dm_doc_embedding (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    doc_chunk_id uuid NOT NULL REFERENCES dm_doc_chunk(id) ON DELETE CASCADE,
    model text NOT NULL,
    embedding vector(1536) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (doc_chunk_id, model)
);

-- IVFFlat 인덱스
CREATE INDEX idx_embedding_ivfflat
    ON dm_doc_embedding
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);
```

### 6. 작업 목록

- [ ] DocEmbedding 엔티티 생성
- [ ] DocEmbeddingRepository 생성 (벡터 검색 쿼리 포함)
- [ ] EmbeddingProvider 인터페이스 정의
- [ ] OllamaEmbeddingProvider 구현
- [ ] OpenAiEmbeddingProvider 구현 (선택)
- [ ] EmbeddingService 구현
- [ ] EmbeddingJobService 구현 (비동기 배치)
- [ ] V6__add_doc_embedding.sql 마이그레이션
- [ ] application.yml 설정 추가
- [ ] SyncService에 임베딩 파이프라인 통합
- [ ] docker-compose.yml에 Ollama 서비스 추가 (선택)

---

## Phase 2-C: 의미/하이브리드 검색 구현

### 1. 벡터 검색 쿼리

**DocEmbeddingRepository.java**:
```java
@Query(value = """
    SELECT
        d.id AS document_id,
        d.path,
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
```

### 2. HybridSearchService

**위치**: `backend/src/main/java/com/docst/service/HybridSearchService.java`

RRF (Reciprocal Rank Fusion) 기반 점수 병합:
- 키워드 결과 + 의미 결과 병합
- `score = sum(1 / (k + rank))` 공식 적용
- k = 60 상수 사용

### 3. 프론트엔드 검색 모드 UI

**수정 파일**: `frontend/app/[locale]/projects/[projectId]/search/page.tsx`

```tsx
// 검색 모드 선택 추가
<Select value={mode} onValueChange={setMode}>
  <SelectItem value="keyword">Keyword</SelectItem>
  <SelectItem value="semantic">Semantic</SelectItem>
  <SelectItem value="hybrid">Hybrid (Recommended)</SelectItem>
</Select>
```

### 4. MCP search_documents 확장

**수정 파일**: `McpController.java`

- `mode` 파라미터 실제 처리
- semantic/hybrid 모드 시 HybridSearchService 호출
- 응답에 `headingPath`, `chunkId` 포함

### 5. 작업 목록

- [ ] SemanticSearchResult 프로젝션 인터페이스 생성
- [ ] DocEmbeddingRepository에 벡터 검색 쿼리 추가
- [ ] HybridSearchService 구현
- [ ] SearchService에서 HybridSearchService 통합
- [ ] SearchController에서 mode 파라미터 처리
- [ ] McpController에서 mode 파라미터 실제 처리
- [ ] 프론트엔드 검색 모드 셀렉트 추가
- [ ] 검색 결과에 headingPath 표시
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
