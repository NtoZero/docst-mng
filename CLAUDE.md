# Docst - Unified Documentation Hub

## Project Overview

Docst는 분산된 Git 레포지토리의 문서를 통합 관리하고, AI 기반 의미 검색을 제공하는 문서 허브 플랫폼입니다.

### Core Features
- **문서 동기화**: GitHub/Local Git 레포지토리 연결 및 자동 동기화
- **버전 관리**: Git 커밋 기반 문서 버전 추적 및 Diff 비교
- **검색**: 키워드 검색 + 의미 검색(Semantic Search) + 하이브리드 검색
- **MCP Tools**: AI 에이전트가 문서를 조회/검색할 수 있는 MCP 인터페이스
- **문서 관계 그래프**: 문서 간 링크 분석 및 영향도 파악

---

## Tech Stack

### Backend
| Category | Technology |
|----------|------------|
| Framework | Spring Boot 3.5.x (Java 21) |
| Database | PostgreSQL 16 + pgvector |
| ORM | Spring Data JPA + Hibernate |
| Migration | Flyway |
| Git Integration | JGit |
| Markdown Parsing | Flexmark |
| Build | Gradle (Kotlin DSL) |

### Frontend
| Category | Technology |
|----------|------------|
| Framework | Next.js 16 (App Router) |
| Language | TypeScript |
| State Management | TanStack Query + Zustand |
| Styling | Tailwind CSS 3.4 |
| UI Components | shadcn/ui pattern |
| Icons | Lucide React |

### Infrastructure
| Category | Technology |
|----------|------------|
| Container | Docker + docker-compose |
| Database | pgvector/pgvector:pg16 |
| Embedding (옵션) | Ollama / OpenAI API |

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        Frontend                              │
│  Next.js 16 + TanStack Query + Zustand + Tailwind           │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                     REST API / MCP Tools                     │
│  Spring Boot 3.5.x                                           │
├─────────────────────────────────────────────────────────────┤
│  Controllers: Auth, Projects, Repositories, Documents,      │
│               Search, Sync, MCP                              │
├─────────────────────────────────────────────────────────────┤
│  Services: UserService, ProjectService, RepositoryService,  │
│            DocumentService, SearchService, SyncService,     │
│            GitSyncService, (Phase2: ChunkingService,        │
│            EmbeddingService, HybridSearchService)           │
├─────────────────────────────────────────────────────────────┤
│  Git Integration: GitService, GitFileScanner, DocumentParser│
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                     PostgreSQL + pgvector                    │
│  Tables: dm_user, dm_project, dm_project_member,            │
│          dm_repository, dm_document, dm_document_version,   │
│          dm_sync_job, (Phase2: dm_doc_chunk, dm_doc_embedding)│
└─────────────────────────────────────────────────────────────┘
```

---

## Project Structure

```
docst-mng/
├── backend/
│   ├── src/main/java/com/docst/
│   │   ├── DocstApplication.java
│   │   ├── api/                    # REST Controllers
│   │   │   ├── AuthController.java
│   │   │   ├── ProjectsController.java
│   │   │   ├── RepositoriesController.java
│   │   │   ├── DocumentsController.java
│   │   │   ├── SearchController.java
│   │   │   ├── SyncController.java
│   │   │   └── ApiModels.java
│   │   ├── domain/                 # JPA Entities
│   │   │   ├── User.java
│   │   │   ├── Project.java
│   │   │   ├── ProjectMember.java
│   │   │   ├── Repository.java
│   │   │   ├── Document.java
│   │   │   ├── DocumentVersion.java
│   │   │   └── SyncJob.java
│   │   ├── repository/             # Spring Data JPA
│   │   ├── service/                # Business Logic
│   │   ├── git/                    # JGit Integration
│   │   │   ├── GitService.java
│   │   │   ├── GitFileScanner.java
│   │   │   └── DocumentParser.java
│   │   └── mcp/                    # MCP Tools
│   │       ├── McpController.java
│   │       └── McpModels.java
│   └── src/main/resources/
│       ├── application.yml
│       └── db/migration/           # Flyway
│           ├── V1__init_schema.sql
│           └── V2__add_indexes.sql
│
├── frontend/
│   ├── app/                        # Next.js App Router
│   │   ├── layout.tsx
│   │   ├── page.tsx
│   │   ├── globals.css
│   │   ├── login/
│   │   ├── projects/
│   │   └── documents/
│   ├── components/
│   │   ├── ui/                     # shadcn/ui components
│   │   ├── header.tsx
│   │   └── sidebar.tsx
│   ├── hooks/
│   │   └── use-api.ts              # TanStack Query hooks
│   ├── lib/
│   │   ├── api.ts                  # API client
│   │   ├── types.ts                # TypeScript types
│   │   ├── store.ts                # Zustand stores
│   │   └── utils.ts                # Utilities (cn)
│   └── providers/
│       └── query-provider.tsx
│
├── docs/
│   ├── plan/                       # Phase plans
│   └── impl/                       # Implementation docs
│
├── docker-compose.yml
└── CLAUDE.md
```

---

## Core Principles

### 1. Git-Native Document Management
- 모든 문서는 Git 레포지토리에서 관리됨
- 버전 히스토리는 Git 커밋을 그대로 활용
- 로컬 미러링으로 빠른 조회 성능 보장

### 2. Search-First Design
- 키워드 검색: PostgreSQL tsvector/ILIKE
- 의미 검색: pgvector 기반 벡터 유사도
- 하이브리드 검색: RRF(Reciprocal Rank Fusion)로 결과 병합

### 3. AI-Ready Architecture
- MCP Tools로 AI 에이전트 연동
- 청킹(Chunking)으로 LLM 컨텍스트 최적화
- 헤딩 경로(headingPath)로 문서 구조 보존

### 4. Incremental Sync
- Webhook 기반 자동 동기화
- 변경된 파일만 증분 처리
- SSE로 실시간 진행 상태 전달

---

## Development Phases

### Phase 1: MVP (Current)
- [x] PostgreSQL + JPA 연동
- [x] JGit 기반 Git 동기화
- [x] REST API 구현
- [x] MCP Tools 기초
- [ ] 프론트엔드 UI 구현 (진행 중)
- [ ] 키워드 검색 (tsvector)

### Phase 2: Semantic Search
- [ ] DocChunk, DocEmbedding 엔티티
- [ ] 청킹 시스템 (헤딩 기반)
- [ ] 임베딩 파이프라인 (Ollama/OpenAI)
- [ ] pgvector 벡터 검색
- [ ] 하이브리드 검색

### Phase 3: Advanced Features
- [ ] GitHub OAuth 연동
- [ ] Webhook 자동 동기화
- [ ] 문서 관계 그래프
- [ ] 영향 분석 (Impact Analysis)
- [ ] 역할 기반 권한 관리

---

## Key APIs

### REST Endpoints
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/auth/local/login` | 로컬 로그인 |
| GET | `/api/projects` | 프로젝트 목록 |
| POST | `/api/projects` | 프로젝트 생성 |
| POST | `/api/projects/{id}/repositories` | 레포 연결 |
| POST | `/api/repositories/{id}/sync` | 동기화 실행 |
| GET | `/api/repositories/{id}/documents` | 문서 목록 |
| GET | `/api/documents/{id}` | 문서 상세 |
| GET | `/api/documents/{id}/versions` | 버전 목록 |
| GET | `/api/projects/{id}/search?q=...` | 검색 |
| POST | `/api/llm/chat` | LLM 채팅 (동기) |
| POST | `/api/llm/chat/stream` | LLM 채팅 (스트리밍) |
| GET | `/api/llm/templates` | 프롬프트 템플릿 목록 |

### MCP Tools
| Tool | Description |
|------|-------------|
| `list_documents` | 문서 목록 조회 |
| `get_document` | 문서 내용 조회 |
| `list_document_versions` | 버전 목록 |
| `diff_document` | 두 버전 비교 |
| `search_documents` | 키워드/의미 검색 |
| `sync_repository` | 동기화 실행 |

---

## Database Schema (Core Tables)

```sql
-- 사용자
dm_user (id, provider, provider_user_id, email, display_name, created_at)

-- 프로젝트
dm_project (id, name, description, active, created_at)
dm_project_member (id, project_id, user_id, role, created_at)

-- 레포지토리
dm_repository (id, project_id, provider, external_id, owner, name,
               clone_url, default_branch, local_mirror_path, active, created_at)

-- 문서
dm_document (id, repository_id, path, title, doc_type,
             latest_commit_sha, deleted, created_at)
dm_document_version (id, document_id, commit_sha, author_name, author_email,
                     committed_at, message, content_hash, content)

-- 동기화 작업
dm_sync_job (id, repository_id, status, target_branch,
             last_synced_commit, error_message, started_at, finished_at)
```

---

## Configuration


### API Key 관리 정책

**중요**: Docst는 모든 외부 서비스 API Key를 프로그램 내부에서 관리합니다.

- **환경 변수나 yml 파일에 API Key를 저장하지 않습니다**
- **모든 API Key는 웹 UI의 Credential 관리 메뉴에서 암호화하여 저장합니다**
- **프로젝트별 또는 시스템 레벨로 API Key 관리 가능**

지원 Credential 타입:
- `OPENAI_API_KEY`: OpenAI API (LLM Chat + Embedding 통합)
- `ANTHROPIC_API_KEY`: Anthropic Claude API
- `GITHUB_PAT`: GitHub Personal Access Token
- `NEO4J_AUTH`: Neo4j 인증 정보 (`{"username": "...", "password": "..."}`)
- `PGVECTOR_AUTH`: PgVector DB 인증 정보 (`{"username": "...", "password": "..."}`)
- `CUSTOM_API_KEY`: 커스텀 API Key

**Credential 등록 방법**:
1. 웹 UI 로그인 후 Settings → Credentials 이동
2. "Add Credential" 클릭
3. 타입 선택 (예: OPENAI_API_KEY)
4. API Key 입력 (AES-256으로 암호화 저장)
5. 프로젝트별 설정 또는 시스템 공용으로 선택

### 데이터소스 관리 정책

**중요**: Docst는 외부 데이터소스(PgVector, Neo4j) 연결 정보를 **yml 파일에서 정적으로 관리하지 않습니다**.

- **모든 데이터소스 연결 정보는 웹 UI의 Admin Settings에서 동적으로 관리합니다**
- **연결 정보 변경 시 애플리케이션 재시작 없이 즉시 반영됩니다**
- **인증 정보는 Credential로 암호화하여 저장합니다**

#### PgVector (Semantic Search용)

| 설정 위치 | 키 | 설명 |
|----------|---|------|
| Admin Settings → PgVector | `pgvector.enabled` | 활성화 여부 |
| Admin Settings → PgVector | `pgvector.host` | PostgreSQL 호스트 |
| Admin Settings → PgVector | `pgvector.port` | PostgreSQL 포트 |
| Admin Settings → PgVector | `pgvector.database` | 데이터베이스명 |
| Admin Settings → PgVector | `pgvector.schema` | 스키마명 |
| Admin Settings → PgVector | `pgvector.table` | 벡터 테이블명 |
| Admin Settings → PgVector | `pgvector.dimensions` | 벡터 차원 |
| Credentials | `PGVECTOR_AUTH` | DB 인증 정보 |

#### Neo4j (Graph RAG용)

| 설정 위치 | 키 | 설명 |
|----------|---|------|
| Admin Settings → Neo4j | `neo4j.enabled` | 활성화 여부 |
| Admin Settings → Neo4j | `neo4j.uri` | Bolt URI |
| Admin Settings → Neo4j | `neo4j.max-hop` | 그래프 탐색 깊이 |
| Credentials | `NEO4J_AUTH` | 인증 정보 |

**주의**: `application.yml`에 PgVector/Neo4j 연결 정보를 추가하지 마세요. 모든 설정은 Admin UI(`/admin/settings`)에서 관리합니다.

### Backend Environment Variables

**환경 변수 목록**
```bash
# Application
DOCST_BASE_URL=http://localhost:8342   # 배포 시: https://api.docst.com

# Database
DB_HOST=localhost
DB_PORT=5434
DB_NAME=docst
DB_USERNAME=postgres
DB_PASSWORD=postgres

# Git Storage
DOCST_GIT_ROOT=/data/git

# Authentication
DOCST_AUTH_MODE=local  # local, github
JWT_SECRET=your-secret-key

# Encryption (Credential 암복호화에 사용)
DOCST_ENCRYPTION_KEY=your-encryption-key-32-bytes-minimum
```

**제거된 환경 변수** (웹 UI Credential 관리로 이동):
- ~~OPENAI_API_KEY~~ → 웹 UI에서 관리
- ~~OPENAI_EMBEDDING_ENABLED~~ → 웹 UI에서 관리
- ~~OPENAI_EMBEDDING_MODEL~~ → 웹 UI에서 관리
- ~~OLLAMA_EMBEDDING_ENABLED~~ → 웹 UI에서 관리
- ~~OLLAMA_BASE_URL~~ → 웹 UI에서 관리
- ~~OLLAMA_EMBEDDING_MODEL~~ → 웹 UI에서 관리


### application.yml (자동 적용)

**주요 변경**: Spring AI Auto-configuration을 비활성화하고, 동적 Factory 패턴 사용

```yaml
spring:
  autoconfigure:
    exclude:
      # Spring AI: 모든 AI 관련 Auto-configuration 비활성화
      # 동적 Credential 기반 Factory에서 ChatModel, EmbeddingModel, VectorStore 생성
      - org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration
      - org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration
      - org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration
      - org.springframework.ai.model.ollama.autoconfigure.OllamaEmbeddingAutoConfiguration
      - org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration

docst:
  llm:
    enabled: true  # LLM 기능 활성화 (Phase 6)
```

**제거된 yml 설정** (Admin UI에서 동적 관리):
- ~~spring.ai.vectorstore.pgvector.*~~ → Admin Settings → PgVector
- ~~spring.neo4j.*~~ → Admin Settings → Neo4j

**API Key/DB 인증 설정**: 환경 변수가 아닌 웹 UI Credential 관리 사용

### Frontend (.env.local)
```
NEXT_PUBLIC_API_BASE=http://localhost:8080
```

---

## LLM Integration

### Tool Definition with @Tool Annotation

**Spring AI 1.1.0+** 권장 방식: `@Tool` annotation을 사용한 선언적 Tool 정의

**위치**: `backend/src/main/java/com/docst/llm/tools/`

#### Pattern

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentTools {

    private final DocumentService documentService;

    @Tool(description = "Search documents in a project using keywords. Returns top matching documents with snippets.")
    public List<DocumentSearchResult> searchDocuments(
        @ToolParam(description = "The search query keywords") String query,
        @ToolParam(description = "The project ID to search within") String projectId,
        @ToolParam(description = "Maximum number of results (default: 10)", required = false) Integer topK
    ) {
        UUID projId = UUID.fromString(projectId);
        int limit = (topK != null && topK > 0) ? topK : 10;
        List<SearchResult> results = searchService.searchByKeyword(projId, query, limit);
        return results.stream()
            .map(r -> new DocumentSearchResult(r.documentId().toString(), r.path(), r.snippet(), r.score()))
            .toList();
    }
}
```

#### 사용 방법

```java
@Service
@RequiredArgsConstructor
public class LlmService {
    private final DocumentTools documentTools;
    private final GitTools gitTools;

    public String chat(String userMessage, UUID projectId, String sessionId) {
        ChatClient chatClient = chatClientFactory.getChatClient(projectId);
        return chatClient.prompt()
            .user(userMessage)
            .tools(documentTools, gitTools)  // @Tool annotation 기반
            .call()
            .content();
    }
}
```

#### Available Tools

**DocumentTools** (`DocumentTools.java`):
- `searchDocuments`: 키워드 검색
- `listDocuments`: 문서 목록 조회
- `getDocument`: 문서 내용 조회
- `updateDocument`: 문서 내용 업데이트 (새 버전 생성)
- `createDocument`: 새 문서 생성

**GitTools** (`GitTools.java`):
- `listBranches`: 브랜치 목록
- `createBranch`: 브랜치 생성
- `switchBranch`: 브랜치 전환
- `getCurrentBranch`: 현재 브랜치 조회
- `syncRepository`: 레포지토리 동기화

#### 장점

- **간결성**: Function Bean 방식 대비 74% 코드 감소
- **타입 안정성**: 컴파일 타임 검증
- **자동 스캔**: Spring이 @Tool 메서드 자동 감지
- **가독성**: 비즈니스 로직과 Tool 정의가 통합

#### Legacy Approach (Deprecated)

`LlmToolsConfig.java`의 Function Bean 방식은 Spring AI 1.1.0+에서 deprecated:

```java
// ❌ Deprecated: Function Bean with Records
@Bean
@Description("Search documents...")
public Function<SearchDocumentsRequest, SearchDocumentsResponse> searchDocuments() {
    return request -> { /* ... */ };
}
```

---

## Running Locally

### 1. 서버 실행

#### With Docker Compose (권장)
```bash
docker-compose up -d

# Backend: http://localhost:8342
# Frontend: http://localhost:3000
# PostgreSQL: localhost:5434
```

#### Development Mode
```bash
# Backend
cd backend && ./gradlew bootRun

# Frontend
cd frontend && npm run dev
```

### 2. API Key 설정 (웹 UI)

**중요**: 환경 변수가 아닌 웹 UI에서 API Key를 설정합니다.

1. **웹 UI 접속**: http://localhost:3000
2. **로그인**: 관리자 계정으로 로그인
3. **Settings → Credentials 이동**
4. **Add Credential 클릭**
5. **API Key 입력**:
   - **Type**: `OPENAI_API_KEY`
   - **Name**: "OpenAI Main Key" (식별용)
   - **Secret**: `sk-proj-your-actual-key-here`
   - **Scope**: `SYSTEM` (전체 프로젝트 공용) 또는 `PROJECT` (특정 프로젝트 전용)
6. **Save**

**OpenAI API Key 발급**:
1. https://platform.openai.com/api-keys 접속
2. "Create new secret key" 클릭
3. 생성된 키를 웹 UI Credential 관리에 입력

### 3. Ollama 사용 (선택)

로컬 LLM을 사용하려면:

1. **Ollama 설치**: https://ollama.ai/download
2. **모델 다운로드**:
```bash
ollama pull nomic-embed-text  # Embedding용
ollama pull llama3.2          # Chat용
```
3. **웹 UI에서 설정**:
   - Settings → System Configuration
   - `llm.provider` = `ollama`
   - `llm.ollama.base-url` = `http://localhost:11434`
   - `embedding.provider` = `ollama`
   - `embedding.ollama.model` = `nomic-embed-text`

**주의**: Ollama는 API Key가 필요 없습니다.

---

## Coding Conventions

### Backend (Java)
- 엔티티: `@Entity` + JPA annotations
- 서비스: `@Service` + `@Transactional`
- 컨트롤러: `@RestController` + `@RequestMapping`
- Record 사용: DTO, 불변 데이터
- 테이블 prefix: `dm_` (docst management)

### Frontend (TypeScript)
- Components: PascalCase (`Header.tsx`)
- Hooks: camelCase with `use` prefix (`useProjects`)
- API client: `lib/api.ts`에 집중
- State: Zustand for client, TanStack Query for server
- Styling: Tailwind utility classes
- Path alias: `@/` → project root

### Git
- Branch: `feat/`, `fix/`, `refactor/`
- Commit: conventional commits 권장
- PR: squash merge

---

## Security Architecture

### 인증 정보 조회: SecurityUtils 패턴

**중요**: `@AuthenticationPrincipal` 대신 `SecurityUtils` 유틸리티를 사용합니다.

```java
// ✓ 권장: SecurityUtils 사용
UUID userId = SecurityUtils.requireCurrentUserId();
UserPrincipal principal = SecurityUtils.requireCurrentUserPrincipal();

// ✗ 사용하지 않음: @AuthenticationPrincipal
public ResponseEntity<?> get(@AuthenticationPrincipal User user) { ... }
```

#### 채택 사유

| 사유 | 설명 |
|------|------|
| **LazyInitializationException 회피** | `User` 엔티티 대신 `UserPrincipal` record DTO 사용 |
| **AOP 권한 체크 통합** | `@RequireProjectRole` + `ProjectPermissionAspect`에서 활용 |
| **다중 인증 방식 지원** | JWT, API Key 등 여러 인증 필터의 Principal 통합 처리 |
| **서비스 레이어 재사용** | Controller뿐 아니라 Service, AOP에서도 동일 방식 사용 |

#### 주요 메서드

| 메서드 | 설명 |
|--------|------|
| `getCurrentUserId()` | 현재 사용자 ID (nullable) |
| `requireCurrentUserId()` | 인증 필수, 없으면 SecurityException |
| `getCurrentUserPrincipal()` | UserPrincipal DTO (nullable) |
| `requireCurrentUserPrincipal()` | 인증 필수, 없으면 SecurityException |
| `isAuthenticated()` | 인증 여부 확인 |

#### 관련 파일

- `SecurityUtils.java`: 인증 정보 조회 유틸리티
- `UserPrincipal.java`: Principal DTO (record)
- `JwtAuthenticationFilter.java`: JWT 인증 필터
- `ProjectPermissionAspect.java`: 프로젝트 권한 AOP

> 상세 분석: `docs/plan/core/security/security-utils-architecture.md`

---

## Document Patterns

문서 파일 탐지 패턴:
```
README.md, readme.md
docs/**/*.md, docs/**/*.adoc
architecture/**/*.md
adr/**/*.md, adrs/**/*.md
*.openapi.yaml, *.openapi.json
CHANGELOG.md, CONTRIBUTING.md
```

문서 타입:
- `MD`: Markdown
- `ADOC`: AsciiDoc
- `OPENAPI`: OpenAPI Specification
- `ADR`: Architecture Decision Record
- `OTHER`: 기타
