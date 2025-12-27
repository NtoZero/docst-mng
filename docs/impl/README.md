# Docst 구현 문서

구현 완료된 기능들에 대한 상세 문서입니다.

---

## 문서 목록

| Phase | 문서 | 상태 | 설명 |
|-------|------|------|------|
| Phase 1 | [phase-1-backend.md](./phase-1-backend.md) | 완료 | 백엔드 JPA, JGit, MCP 구현 |
| Phase 1 | [phase-1-frontend.md](./phase-1-frontend.md) | 완료 | 프론트엔드 UI 구현 |
| Phase 1 | [credential-management.md](./credential-management.md) | 완료 | 자격증명 관리 시스템 |
| Phase 2 | phase-2-semantic.md | 미작성 | 청킹, 임베딩, 의미 검색 |
| Phase 3 | phase-3-advanced.md | 미작성 | OAuth, Webhook, 그래프 |

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
| MCP Tools | ✅ 완료 | `mcp/` |
| REST API | ✅ 완료 | `api/` |
| 키워드 검색 | ✅ 완료 | `SearchService` |
| **Credential 암호화** | ✅ 완료 | `EncryptionService` |
| **Credential 관리** | ✅ 완료 | `CredentialService` |
| **Git 인증 연동** | ✅ 완료 | `GitService` |
| 의미 검색 | ❌ Phase 2 | - |
| GitHub OAuth | ❌ Phase 3 | - |
| Webhook | ❌ Phase 3 | - |

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
| SSE 동기화 진행률 | ✅ 완료 | `hooks/use-sync.ts` |

### Infrastructure

| 기능 | 상태 |
|------|------|
| docker-compose | ✅ 완료 |
| PostgreSQL + pgvector | ✅ 완료 |
| Dockerfile (backend) | ✅ 완료 |
| Dockerfile (frontend) | ✅ 완료 |

---

## 최근 변경사항

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
