# Docst 구현 문서

구현 완료된 기능들에 대한 상세 문서입니다.

---

## 문서 목록

| Phase | 문서 | 상태 | 설명 |
|-------|------|------|------|
| Phase 1 | [phase-1-backend.md](./phase-1-backend.md) | ✅ 완료 | 백엔드 JPA, JGit, MCP 구현 |
| Phase 1 | [phase-1-frontend.md](./phase-1-frontend.md) | ✅ 완료 | 프론트엔드 UI 구현 |
| Phase 1 | [credential-management.md](./credential-management.md) | ✅ 완료 | 자격증명 관리 시스템 |
| Phase 2 | [phase-2-implementation.md](./phase-2-implementation.md) | ✅ 완료 | 청킹, 임베딩, 의미 검색 |
| Phase 3 | phase-3-advanced.md | 미작성 | OAuth, Webhook, 그래프 |
| Phase 4 | - | ✅ 완료 | RAG 파이프라인, 동적 설정 |
| **Phase 5** | **[phase-5-implementation.md](./phase-5-implementation.md)** | **✅ MVP 완료** | **MCP 통합, Playground UI** |

---

## 구현 현황

### Backend

| 기능 | 상태 | 파일/패키지 |
|------|------|------------|
| JPA 엔티티 | ✅ 완료 | `domain/` |
| Spring Data Repository | ✅ 완료 | `repository/` |
| Flyway 마이그레이션 | ✅ 완료 | `db/migration/` |
| 서비스 레이어 | ✅ 완료 | `service/` |
| JGit 연동 | ✅ 완료 | `git/` |
| MCP Tools (READ) | ✅ 완료 | `mcp/` |
| REST API | ✅ 완료 | `api/` |
| 키워드 검색 | ✅ 완료 | `SearchService` |
| 의미 검색 (Semantic) | ✅ 완료 | `SemanticSearchService` |
| 하이브리드 검색 | ✅ 완료 | `HybridSearchService` |
| **Credential 암호화** | ✅ 완료 | `EncryptionService` |
| **Credential 관리** | ✅ 완료 | `CredentialService` |
| **Git 인증 연동** | ✅ 완료 | `GitService` |
| **RAG 파이프라인** | ✅ 완료 | `EmbeddingService`, `ChunkingService` |
| **MCP Tools (WRITE)** | ✅ 완료 | `mcp/` (create, update, push) |
| **Git 쓰기 작업** | ✅ 완료 | `GitWriteService` |
| **문서 쓰기 서비스** | ✅ 완료 | `DocumentWriteService` |
| **권한 시스템** | ✅ 완료 | `PermissionService` |
| **JSON-RPC Transport** | ✅ 완료 | `McpTransportController` |
| **MCP Tool Dispatcher** | ✅ 완료 | `McpToolDispatcher` |
| GitHub OAuth | ❌ Phase 6 | - |
| Webhook | ❌ Phase 6 | - |
| LLM 통합 | ❌ Phase 6 | - |

### Frontend

| 기능 | 상태 | 파일/패키지 |
|------|------|------------|
| Next.js 스캐폴딩 | ✅ 완료 | `frontend/` |
| 프로젝트 목록 UI | ✅ 완료 | `app/projects/` |
| 레포지토리 연결 UI | ✅ 완료 | `app/projects/[id]/` |
| 문서 뷰어 | ✅ 완료 | `app/documents/` |
| Diff 뷰어 | ✅ 완료 | `app/documents/[id]/versions/` |
| 검색 UI | ✅ 완료 | `app/projects/[id]/search/` |
| **Credential 관리 UI** | ✅ 완료 | `app/credentials/` |
| **Repository Credential 연결** | ✅ 완료 | `app/projects/[id]/page.tsx` |
| **RAG 설정 UI** | ✅ 완료 | `app/projects/[id]/settings/rag/` |
| SSE 동기화 진행률 | ✅ 완료 | `hooks/use-sync.ts` |
| **MCP Protocol 클라이언트** | ✅ 완료 | `lib/mcp-protocol.ts` |
| **MCP Client 래퍼** | ✅ 완료 | `lib/mcp-client.ts` |
| **Playground UI** | ✅ 완료 | `app/playground/` |
| **Chat 컴포넌트** | ✅ 완료 | `components/playground/` |
| **use-mcp-tools Hook** | ✅ 완료 | `hooks/use-mcp-tools.ts` |

### Infrastructure

| 기능 | 상태 |
|------|------|
| docker-compose | ✅ 완료 |
| PostgreSQL + pgvector | ✅ 완료 |
| Dockerfile (backend) | ✅ 완료 |
| Dockerfile (frontend) | ✅ 완료 |

---

## 최근 변경사항

### 2026-01-01: Phase 5 - MCP 통합 및 Playground (MVP 완료)

LLM 에이전트가 MCP를 통해 문서를 읽고 쓸 수 있는 기능과 Playground UI 구현

**백엔드**:
- MCP Tools 확장: create_document, update_document, push_to_remote
- GitWriteService: 파일 쓰기, 커밋, 푸시
- DocumentWriteService: 문서 생성/수정 + 자동 동기화
- PermissionService: 역할 기반 권한 검사
- JSON-RPC 2.0 Transport: HTTP Streamable + SSE
- McpToolDispatcher: Enum 기반 자동 라우팅

**프론트엔드**:
- MCP Protocol 클라이언트 (JSON-RPC 2.0)
- Playground UI: 메시지 입력, Tool Call 시각화
- use-mcp-tools Hook: MCP 도구 직접 호출

**Phase 6 계획**: LLM 통합 (OpenAI/Anthropic API)

자세한 내용은 [phase-5-implementation.md](./phase-5-implementation.md) 참조

### 2025-12-27: Credential 관리 시스템

Private Git 레포지토리 인증을 위한 자격증명 관리 시스템 추가

- AES-256-GCM 암호화로 토큰 안전 저장
- GitHub PAT, Basic Auth 지원
- Repository별 Credential 연결
- 프론트엔드 관리 UI

자세한 내용은 [credential-management.md](./credential-management.md) 참조

---

## 관련 문서

- [계획 문서](../plan/README.md) - Phase별 구현 계획
- [PRD & 설계](../docst_prd_and_design.md) - 요구사항 및 API 스펙
- [트러블슈팅](../troubleshooting/README.md) - 문제 해결 가이드
