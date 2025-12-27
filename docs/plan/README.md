# Docst 구현 계획 Overview

> 분산된 레포지토리 문서를 하나의 지식 인덱스로 묶는 통합 문서 관리 플랫폼

---

## 현재 상태 요약

### Backend (`backend/src/main/java/com/docst/`)
| 구성요소 | 상태 | 설명 |
|----------|------|------|
| Spring Boot App | ✅ 완료 | 기본 스캐폴딩 |
| REST Controllers | ✅ 완료 | Auth, Projects, Repositories, Documents, Search, Sync |
| API Models | ✅ 완료 | Request/Response DTO (record) |
| Store Entities | ✅ 완료 | Project, Repository, Document, DocumentVersion, SyncJob |
| InMemoryStore | ✅ 완료 | 스텁 구현 (DB 연동 전) |
| JPA Entities | ❌ 미구현 | PostgreSQL 연동 필요 |
| JGit 연동 | ❌ 미구현 | 실제 Git clone/fetch |
| MCP Server | ❌ 미구현 | MCP Tools 엔드포인트 |

### Frontend (`frontend/`)
| 구성요소 | 상태 | 설명 |
|----------|------|------|
| Next.js App Router | ✅ 완료 | 기본 스캐폴딩 |
| Layout/Page | ✅ 완료 | 최소한의 플레이스홀더 |
| UI Components | ❌ 미구현 | shadcn/ui 통합 필요 |
| 페이지 라우팅 | ❌ 미구현 | 프로젝트/문서/검색 페이지 |

### Infrastructure
| 구성요소 | 상태 | 설명 |
|----------|------|------|
| docker-compose | ⚠️ 부분 | 골격만 존재 |
| PostgreSQL | ❌ 미구현 | pgvector 포함 설정 필요 |
| Flyway 마이그레이션 | ❌ 미구현 | DDL 스크립트 |

---

## Phase 로드맵

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           Phase 1: MVP                                  │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐ │
│  │ DB Layer │→ │ Services │→ │  JGit    │→ │ Frontend │→ │   MCP    │ │
│  │   JPA    │  │ Refactor │  │  Sync    │  │   UI     │  │  Tools   │ │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘  └──────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
                                    ↓
┌─────────────────────────────────────────────────────────────────────────┐
│                     Phase 2: Semantic Search                            │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐               │
│  │ Chunking │→ │Embedding │→ │ pgvector │→ │ Hybrid   │               │
│  │ System   │  │ Pipeline │  │  Search  │  │ Search   │               │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘               │
└─────────────────────────────────────────────────────────────────────────┘
                                    ↓
┌─────────────────────────────────────────────────────────────────────────┐
│                    Phase 3: Advanced Features                           │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐               │
│  │  GitHub  │→ │ Webhook  │→ │  Doc     │→ │  Roles   │               │
│  │  OAuth   │  │Auto Sync │  │  Graph   │  │ Permissions│              │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘               │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Phase별 상세 문서

| Phase | 문서 | 핵심 목표 |
|-------|------|-----------|
| **Phase 1** | [phase-1-mvp.md](./phase-1-mvp.md) | DB 연동, JGit 동기화, 기본 UI, 키워드 검색, MCP 기초 |
| **Phase 2** | [phase-2-semantic-search.md](./phase-2-semantic-search.md) | 청킹, 임베딩, pgvector 의미 검색, 하이브리드 검색 |
| **Phase 3** | [phase-3-advanced-features.md](./phase-3-advanced-features.md) | GitHub OAuth, Webhook, 문서 그래프, 권한 고도화 |

## Feature 계획 문서

| Feature | 문서 | 핵심 목표 |
|---------|------|-----------|
| **Sync Mode** | [sync-mode-enhancement.md](./sync-mode-enhancement.md) | 동기화 모드 확장 (Full/Incremental/Specific Commit), 커밋 히스토리 UI |

---

## 기술 스택 요약

### Backend
- **Runtime**: Java 21
- **Framework**: Spring Boot 3.5.x
- **Database**: PostgreSQL 16 + pgvector
- **ORM**: Spring Data JPA + Flyway
- **Git**: JGit
- **Embedding**: Spring AI / Ollama / OpenAI

### Frontend
- **Framework**: Next.js 16 (App Router)
- **Language**: TypeScript
- **UI**: shadcn/ui + Tailwind CSS
- **State**: TanStack Query + Zustand
- **Graph**: react-force-graph

### Infrastructure
- **Container**: Docker + docker-compose
- **Monitoring**: Spring Boot Actuator + Prometheus (Phase 3)

---

## 핵심 데이터 모델

```
Project
  └── Repository (1:N)
        └── Document (1:N)
              └── DocumentVersion (1:N, commit 기반)
                    └── DocChunk (1:N, Phase 2)
                          └── DocEmbedding (1:1, Phase 2)

Project
  └── ProjectMember (1:N)
        └── User (N:1)

Document
  └── DocumentRelation (N:N, Phase 3)
```

---

## 실행 방법

### 개발 환경 (Phase 1 완료 후)
```bash
# 전체 스택 기동
docker-compose up -d

# 접속
# - Frontend: http://localhost:3000
# - Backend API: http://localhost:8080
# - PostgreSQL: localhost:5432
```

### 개발 서버 (핫 리로드)
```bash
# Backend
cd backend
./gradlew bootRun --continuous

# Frontend
cd frontend
npm run dev
```

---

## 주요 API 엔드포인트

### REST API
| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/auth/local/login` | 로컬 로그인 |
| GET | `/api/projects` | 프로젝트 목록 |
| POST | `/api/projects/{id}/repositories` | 레포 연결 |
| POST | `/api/repositories/{id}/sync` | 동기화 실행 |
| GET | `/api/repositories/{id}/documents` | 문서 목록 |
| GET | `/api/documents/{id}` | 문서 상세 (최신 버전) |
| GET | `/api/documents/{id}/versions` | 버전 목록 |
| GET | `/api/documents/{id}/diff?from=...&to=...` | Diff |
| GET | `/api/projects/{id}/search?q=...&mode=...` | 검색 |

### MCP Tools
| Tool | 설명 |
|------|------|
| `list_documents` | 문서 목록 조회 |
| `get_document` | 문서 내용 조회 |
| `list_document_versions` | 버전 목록 |
| `search_documents` | 검색 (keyword/semantic/hybrid) |
| `get_document_graph` | 관계 그래프 (Phase 3) |
| `analyze_impact` | 영향 분석 (Phase 3) |

---

## 참고 문서

- [PRD & 설계 패키지](../docst_prd_and_design.md) - 전체 요구사항 및 ERD, API 스펙
