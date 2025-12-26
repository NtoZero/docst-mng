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

### Backend (application.yml)
```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:5432/${DB_NAME:docst}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}

docst:
  git:
    root-path: ${DOCST_GIT_ROOT:/data/git}
  auth:
    mode: ${DOCST_AUTH_MODE:local}  # local, github
  embedding:
    provider: ${DOCST_EMBEDDING_PROVIDER:ollama}  # ollama, openai, local
```

### Frontend (.env.local)
```
NEXT_PUBLIC_API_BASE=http://localhost:8080
```

---

## Running Locally

### With Docker Compose
```bash
docker-compose up -d
# Backend: http://localhost:8080
# Frontend: http://localhost:3000
```

### Development
```bash
# Backend
cd backend && ./gradlew bootRun

# Frontend
cd frontend && npm run dev
```

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
