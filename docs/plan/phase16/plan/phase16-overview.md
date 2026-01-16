# Phase 16: MCP Tools 확장

## 개요

백엔드에 구현된 기능 중 MCP 도구로 노출되지 않은 기능들을 MCP 인터페이스로 제공하여 AI 에이전트의 문서 작업 능력을 강화한다.

## 배경

### 현재 MCP 도구 현황 (10개)

| 도구 | 클래스 | 설명 |
|------|--------|------|
| `list_projects` | McpProjectTools | 프로젝트 목록 조회 |
| `list_documents` | McpDocumentTools | 문서 목록 조회 |
| `get_document` | McpDocumentTools | 문서 내용 조회 |
| `list_document_versions` | McpDocumentTools | 버전 이력 조회 |
| `diff_document` | McpDocumentTools | 두 버전 비교 |
| `search_documents` | McpDocumentTools | 문서 검색 (keyword/semantic/graph/hybrid) |
| `sync_repository` | McpGitTools | 레포지토리 동기화 |
| `create_document` | McpGitTools | 문서 생성 |
| `update_document` | McpGitTools | 문서 수정 |
| `push_to_remote` | McpGitTools | 원격 푸시 |

### MCP로 노출되지 않은 백엔드 기능

| 카테고리 | 서비스 | 주요 기능 |
|----------|--------|----------|
| 브랜치 관리 | BranchService | listBranches, createBranch, switchBranch, getCurrentBranch |
| 문서 관계 그래프 | GraphService | getDocumentGraph, analyzeImpact |
| 문서 링크 | DocumentLinkService | getOutgoingLinks, getIncomingLinks, getBrokenLinks |
| 레포지토리 관리 | RepositoryService | findByProjectId, getFolderTree |
| 커밋 관리 | CommitService | listCommits, getChangedFiles, diff between commits |
| 통계 | StatsService | 대시보드 통계 |

## 목표

1. **문서 관계 이해**: AI 에이전트가 문서 간 링크 관계를 파악하고 영향 분석 수행
2. **Git 워크플로우 지원**: 브랜치 생성/전환, 커밋 이력 조회 기능 제공
3. **레포지토리 탐색**: 폴더 구조 파악 및 레포지토리 메타데이터 조회

## 구현 범위

### Phase 16-A: 그래프 및 링크 도구 (HIGH Priority)

- **McpGraphTools.java** (신규)
  - `get_document_graph`: 문서 관계 그래프 조회
  - `analyze_impact`: 문서 변경 영향 분석
  - `get_document_links`: 문서 링크 조회 (outgoing/incoming)
  - `get_broken_links`: 깨진 링크 목록

- **McpProjectTools.java** (확장)
  - `list_repositories`: 프로젝트의 레포지토리 목록
  - `get_folder_tree`: 레포지토리 폴더 구조

### Phase 16-B: 브랜치 및 커밋 도구 (MEDIUM Priority)

- **McpBranchTools.java** (신규)
  - `list_branches`: 브랜치 목록
  - `get_current_branch`: 현재 브랜치
  - `create_branch`: 브랜치 생성
  - `switch_branch`: 브랜치 전환

- **McpCommitTools.java** (신규)
  - `list_commits`: 커밋 이력 (페이지네이션)
  - `get_commit_changes`: 커밋의 변경 파일
  - `diff_commits`: 두 커밋 간 변경 파일
  - `list_unpushed_commits`: 미푸시 커밋

### Phase 16-C: 관리 도구 (LOW Priority)

- **McpProjectTools.java** (추가 확장)
  - `get_repository`: 레포지토리 상세 정보
  - `get_stats`: 대시보드 통계

## 최종 결과

| 항목 | Before | After |
|------|--------|-------|
| 총 MCP 도구 수 | 10 | 26 |
| 도구 클래스 수 | 3 | 6 |
| Graph 도구 | 0 | 4 |
| Branch 도구 | 0 | 4 |
| Commit 도구 | 0 | 4 |
| Repository 도구 | 0 | 4 |

## 관련 문서

- [Phase 16-A: 그래프 도구](./phase16-a-graph-tools.md)
- [Phase 16-B: 브랜치/커밋 도구](./phase16-b-branch-commit-tools.md)
- [Phase 16-C: 관리 도구](./phase16-c-admin-tools.md)