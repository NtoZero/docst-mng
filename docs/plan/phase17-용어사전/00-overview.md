# Phase 17: 프로젝트별 용어 사전 (Glossary) - 전체 개요

## 목적

프로젝트 문서에서 사용하는 용어를 일관되게 관리하고, AI 에이전트와 사용자 모두가 용어 정의를 쉽게 참조할 수 있는 시스템 구축.

## 단계별 구현 계획

| Phase | 제목 | 상태 | 설명 |
|-------|------|------|------|
| 17-A | 기본 용어 관리 | ✅ 완료 | CRUD, 검색, 카테고리 필터링 |
| 17-B | 상세 조회 UI | 🔲 예정 | 용어 상세 다이얼로그/페이지 |
| 17-C | 일괄 등록 | 🔲 예정 | CSV/JSON 파일 기반 batch import |
| 17-D | MCP 확장 | 🔲 예정 | 생성/수정/삭제/일괄등록 MCP Tools |

## 구현 완료 항목 (Phase 17-A)

### Backend
- `V19__add_glossary_term.sql` - DB 마이그레이션
- `GlossaryTerm.java` - JPA Entity (JSONB 컬럼 지원)
- `GlossaryTermRepository.java` - Repository
- `GlossaryService.java` - 비즈니스 로직 (키워드/시맨틱 검색)
- `GlossaryController.java` - REST API
- `McpGlossaryTools.java` - MCP 조회 도구 (list, search, get)

### Frontend
- `glossary/page.tsx` - 메인 페이지
- `glossary-term-card.tsx` - 카드 컴포넌트
- `glossary-form-dialog.tsx` - 추가/수정 폼
- `glossary-search.tsx` - 검색 컴포넌트
- `use-glossary.ts` - TanStack Query hooks
- `sidebar.tsx` - 사이드바 메뉴 추가

### API Endpoints
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/projects/{projectId}/glossary` | 목록 조회 |
| GET | `/api/projects/{projectId}/glossary/{termId}` | 상세 조회 |
| POST | `/api/projects/{projectId}/glossary` | 생성 |
| PUT | `/api/projects/{projectId}/glossary/{termId}` | 수정 |
| DELETE | `/api/projects/{projectId}/glossary/{termId}` | 삭제 |
| GET | `/api/projects/{projectId}/glossary/search` | 검색 |
| GET | `/api/projects/{projectId}/glossary/categories` | 카테고리 목록 |

## 기술 스택

- **Backend**: Spring Boot 3.5.x, JPA, PostgreSQL (JSONB + tsvector)
- **Search**: pgvector 기반 시맨틱 검색
- **MCP**: Spring AI `@Tool` annotation
- **Frontend**: Next.js 16, TanStack Query, shadcn/ui

## 관련 문서

- [Phase 17-A: 기본 용어 관리](./17-A-basic-crud.md)
- [Phase 17-B: 상세 조회 UI](./17-B-detail-view.md)
- [Phase 17-C: 일괄 등록](./17-C-batch-import.md)
- [Phase 17-D: MCP 확장](./17-D-mcp-extension.md)
