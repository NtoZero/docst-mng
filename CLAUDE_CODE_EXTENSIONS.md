# Claude Code 확장 기능 계획서

> Docst 문서 허브 플랫폼을 위한 Claude Code 커맨드, 훅, 서브에이전트, 스킬 설계

---

## 목차

1. [개요](#개요)
2. [범용 확장 기능](#part-1-범용-확장-기능)
   - [Pre-commit Hooks](#11-pre-commit-hooks)
   - [Post-build Hooks](#12-post-build-hooks)
   - [개발 커맨드](#13-개발-커맨드)
3. [도메인 특화 확장 기능](#part-2-도메인-특화-확장-기능)
   - [동기화 테스트 스킬](#21-동기화-테스트)
   - [검색 검증 스킬](#22-검색-검증)
   - [LLM/MCP 검증 스킬](#23-llmmcp-검증)
   - [서브에이전트](#24-서브에이전트)
4. [파일 구조](#part-3-파일-구조)
5. [구현 우선순위](#part-4-구현-우선순위)
6. [검증 방법](#part-5-검증-방법)

---

## 개요

### 프로젝트 컨텍스트

| 구성 요소 | 기술 스택 |
|----------|----------|
| Backend | Spring Boot 3.5.x, Java 21, Spring AI 1.1.0 |
| Frontend | Next.js 16, TypeScript, TanStack Query, Zustand |
| Database | PostgreSQL 16 + pgvector, Neo4j |
| 주요 기능 | Git 문서 동기화, RAG 검색, LLM Chat, MCP Tools |

### 확장 기능 분류

| 분류 | 개수 | 대상 |
|-----|-----|------|
| 범용 확장 | 8개 | 모든 풀스택 프로젝트 |
| 도메인 특화 | 7개 | Docst 문서 플랫폼 |

---

## Part 1: 범용 확장 기능

### 1.1 Pre-commit Hooks

#### `pre-commit-lint-format`

커밋 전 코드 품질을 자동 검증합니다.

| 항목 | 내용 |
|------|------|
| **타입** | Hook (pre-commit) |
| **목적** | 린트 및 포맷 검사로 일관된 코드 스타일 유지 |
| **트리거** | `git commit` 실행 시 자동 |

**동작 흐름**:

```
git commit 실행
    │
    ├─ .java 파일 변경됨?
    │   └─ YES → ./gradlew checkstyleMain
    │            실패 시 커밋 차단
    │
    ├─ .ts/.tsx 파일 변경됨?
    │   ├─ npm run lint
    │   │   실패 시 커밋 차단
    │   └─ npm run format:check
    │       실패 시 경고 (차단 안함)
    │
    └─ 모든 검사 통과 → 커밋 진행
```

**설정 예시** (`.claude/hooks/pre-commit-lint-format.yaml`):

```yaml
name: pre-commit-lint-format
type: pre-commit
description: 커밋 전 린트 및 포맷 검사

actions:
  - name: Backend Java Lint
    condition: "changed_files.any(f => f.endsWith('.java'))"
    command: cd backend && ./gradlew checkstyleMain --quiet
    on_failure: block

  - name: Frontend ESLint
    condition: "changed_files.any(f => f.endsWith('.ts') || f.endsWith('.tsx'))"
    command: cd frontend && npm run lint
    on_failure: block

  - name: Frontend Prettier
    condition: "changed_files.any(f => f.endsWith('.ts') || f.endsWith('.tsx'))"
    command: cd frontend && npm run format:check
    on_failure: warn
```

---

#### `pre-commit-test-affected`

변경된 파일에 영향받는 테스트만 선택적으로 실행합니다.

| 항목 | 내용 |
|------|------|
| **타입** | Hook (pre-commit, 선택적) |
| **목적** | 빠른 피드백을 위한 관련 테스트만 실행 |
| **활성화** | 사용자가 명시적으로 활성화 |

**설정 예시**:

```yaml
name: pre-commit-test-affected
type: pre-commit
optional: true

actions:
  - name: Run Affected Backend Tests
    condition: "changed_files.any(f => f.includes('backend/src/'))"
    command: |
      cd backend
      CHANGED=$(git diff --cached --name-only | grep '\.java$' | xargs basename -a | sed 's/\.java/Test/')
      ./gradlew test --tests "*${CHANGED}*" --quiet || true
    timeout: 120000

  - name: Frontend Type Check
    condition: "changed_files.any(f => f.endsWith('.ts') || f.endsWith('.tsx'))"
    command: cd frontend && npx tsc --noEmit
    on_failure: warn
```

---

### 1.2 Post-build Hooks

#### `post-build-verify`

빌드 완료 후 산출물을 자동 검증합니다.

| 항목 | 내용 |
|------|------|
| **타입** | Hook (post-build) |
| **목적** | 빌드 산출물 존재 및 크기 확인 |
| **트리거** | `./gradlew build` 또는 `npm run build` 완료 후 |

**검증 항목**:

| 대상 | 검증 내용 |
|------|----------|
| Backend JAR | `backend/build/libs/*.jar` 존재 여부 + 크기 출력 |
| Frontend Build | `frontend/.next` 디렉토리 존재 여부 + 크기 출력 |
| Bundle Size | Next.js 번들 크기 분석 (선택적) |

**설정 예시**:

```yaml
name: post-build-verify
type: post-build

actions:
  - name: Verify Backend JAR
    trigger: "command.includes('gradlew build')"
    command: |
      JAR=$(ls backend/build/libs/*.jar 2>/dev/null | head -1)
      [ -z "$JAR" ] && echo "ERROR: JAR not found" && exit 1
      echo "Backend JAR: $JAR ($(du -h $JAR | cut -f1))"

  - name: Verify Frontend Build
    trigger: "command.includes('npm run build')"
    command: |
      [ ! -d "frontend/.next" ] && echo "ERROR: .next not found" && exit 1
      echo "Frontend build: $(du -sh frontend/.next | cut -f1)"
```

---

### 1.3 개발 커맨드

#### `/dev-start` - 개발 환경 시작

| 항목 | 내용 |
|------|------|
| **타입** | Command |
| **목적** | 개발 환경 원클릭 시작 |
| **사용법** | `/dev-start [mode]` |

**옵션**:

| 모드 | 동작 |
|-----|------|
| `--full` (기본) | 인프라 + 백엔드 + 프론트엔드 전체 시작 |
| `--backend-only` | 백엔드만 시작 |
| `--frontend-only` | 프론트엔드만 시작 |
| `--infra-only` | Docker 인프라만 시작 |

**실행 흐름**:

```
/dev-start --full
    │
    ├─ 1. Docker 인프라 시작
    │   docker-compose up -d postgres neo4j ollama
    │   (10초 대기)
    │
    ├─ 2. Backend 시작 (백그라운드)
    │   cd backend && ./gradlew bootRun &
    │   → http://localhost:8342
    │
    ├─ 3. Frontend 시작 (백그라운드)
    │   cd frontend && npm run dev &
    │   → http://localhost:3002
    │
    └─ 4. 헬스 체크 (15초 후)
        curl http://localhost:8342/actuator/health
        curl http://localhost:3002
```

**예시**:

```bash
# 전체 환경 시작
/dev-start

# 백엔드만 시작 (인프라는 이미 실행 중일 때)
/dev-start --backend-only

# Docker 인프라만 시작
/dev-start --infra-only
```

---

#### `/branch-create` - 브랜치 생성

| 항목 | 내용 |
|------|------|
| **타입** | Command |
| **목적** | 컨벤션에 맞는 Git 브랜치 생성 |
| **사용법** | `/branch-create <type> <description>` |

**브랜치 타입**:

| 타입 | 용도 | 예시 |
|-----|------|-----|
| `feat` | 새 기능 | `feat/add-dark-mode` |
| `fix` | 버그 수정 | `fix/login-redirect` |
| `refactor` | 리팩토링 | `refactor/auth-service` |
| `docs` | 문서 작업 | `docs/api-guide` |
| `test` | 테스트 추가 | `test/search-service` |
| `chore` | 설정/빌드 | `chore/update-deps` |

**예시**:

```bash
/branch-create feat add-dark-mode
# → git checkout -b feat/add-dark-mode

/branch-create fix login-redirect
# → git checkout -b fix/login-redirect
```

---

#### `/test-run` - 테스트 실행

| 항목 | 내용 |
|------|------|
| **타입** | Command |
| **목적** | 테스트 실행 (범위/대상/커버리지 선택) |
| **사용법** | `/test-run [options]` |

**옵션**:

| 옵션 | 값 | 기본값 |
|-----|---|-------|
| `--scope` | unit, integration, all | unit |
| `--target` | backend, frontend, both | both |
| `--coverage` | (플래그) | false |

**예시**:

```bash
# 백엔드 단위 테스트
/test-run --scope unit --target backend

# 전체 통합 테스트 + 커버리지
/test-run --scope integration --coverage

# 프론트엔드 린트 검사
/test-run --target frontend
```

---

#### `/db-migrate` - DB 마이그레이션

| 항목 | 내용 |
|------|------|
| **타입** | Command |
| **목적** | Flyway 마이그레이션 관리 |
| **사용법** | `/db-migrate <action>` |

**액션**:

| 액션 | 설명 | 명령어 |
|-----|------|-------|
| `status` | 현재 마이그레이션 상태 | `./gradlew flywayInfo` |
| `migrate` | 마이그레이션 실행 | `./gradlew flywayMigrate` |
| `repair` | 실패한 마이그레이션 복구 | `./gradlew flywayRepair` |
| `clean` | 모든 객체 삭제 (위험!) | `./gradlew flywayClean` |

**예시**:

```bash
# 마이그레이션 상태 확인
/db-migrate status

# 새 마이그레이션 적용
/db-migrate migrate
```

---

#### `/deps-update` - 의존성 업데이트

| 항목 | 내용 |
|------|------|
| **타입** | Command |
| **목적** | 의존성 업데이트 확인 및 적용 |
| **사용법** | `/deps-update [--check | --apply]` |

**동작**:

```bash
# 업데이트 가능한 의존성 확인
/deps-update --check
# Backend: ./gradlew dependencyUpdates
# Frontend: npx npm-check-updates

# 프론트엔드 의존성 업데이트 적용
/deps-update --apply
# npx npm-check-updates -u && npm install
```

---

## Part 2: 도메인 특화 확장 기능

### 2.1 동기화 테스트

#### `/sync-test` - Git 동기화 검증

| 항목 | 내용 |
|------|------|
| **타입** | Skill |
| **목적** | 레포지토리 동기화 기능 E2E 검증 |
| **사용법** | `/sync-test <repository-id> [--mode MODE]` |

**동기화 모드**:

| 모드 | 설명 |
|-----|------|
| `full` | 전체 재스캔 |
| `incremental` | 마지막 커밋 이후 변경분만 |
| `specific-commit` | 특정 커밋으로 고정 |

**워크플로우**:

```
/sync-test abc123 --mode full
    │
    ├─ 1. 레포지토리 존재 확인
    │   GET /api/repositories/{id}
    │   → 200 OK 확인
    │
    ├─ 2. 동기화 트리거
    │   POST /api/repositories/{id}/sync
    │   body: { mode: "FULL_SCAN", enableEmbedding: false }
    │   → 200 OK, status: RUNNING
    │
    ├─ 3. 진행 상태 모니터링
    │   GET /api/repositories/{id}/sync-status
    │   (2초 간격 폴링, 최대 2분)
    │   → status: COMPLETED 대기
    │
    ├─ 4. 문서 수 검증
    │   GET /api/repositories/{id}/documents
    │   → documents.length > 0 확인
    │
    └─ 5. 결과 리포트 출력
        - 동기화 상태
        - 동기화된 문서 수
        - 소요 시간
```

**출력 예시**:

```markdown
## 동기화 테스트 결과
- 레포지토리: abc123-uuid
- 모드: FULL_SCAN
- 상태: COMPLETED
- 동기화된 문서: 24개
- 소요 시간: 8,432ms
```

---

### 2.2 검색 검증

#### `/search-validate` - 검색 품질 검증

| 항목 | 내용 |
|------|------|
| **타입** | Skill |
| **목적** | 키워드/시맨틱/하이브리드 검색 품질 비교 |
| **사용법** | `/search-validate <project-id> --query "검색어" [--expectedPath PATH]` |

**워크플로우**:

```
/search-validate proj123 --query "JWT 인증" --expectedPath "auth.md"
    │
    ├─ 1. 키워드 검색
    │   POST /mcp/tools/search_documents
    │   { query: "JWT 인증", mode: "keyword", topK: 5 }
    │
    ├─ 2. 시맨틱 검색
    │   POST /mcp/tools/search_documents
    │   { query: "JWT 인증", mode: "semantic", topK: 5 }
    │
    ├─ 3. 하이브리드 검색
    │   POST /mcp/tools/search_documents
    │   { query: "JWT 인증", mode: "hybrid", topK: 5 }
    │
    ├─ 4. 결과 분석
    │   - 각 모드별 결과 수
    │   - 결과 중복률 계산
    │   - 예상 문서 순위 확인
    │
    └─ 5. 비교 리포트 출력
```

**출력 예시**:

```markdown
## 검색 검증 결과: "JWT 인증"

| 검색 모드 | 결과 수 | 최상위 문서 | 점수 |
|----------|--------|------------|------|
| 키워드 | 5 | docs/auth/jwt.md | 0.89 |
| 시맨틱 | 5 | docs/auth/authentication.md | 0.82 |
| 하이브리드 | 5 | docs/auth/jwt.md | 0.91 |

### 분석
- 결과 중복률: 60%
- 예상 문서 매칭: YES (auth.md가 상위 3위 이내)
```

---

#### `/embedding-check` - 임베딩 상태 확인

| 항목 | 내용 |
|------|------|
| **타입** | Skill |
| **목적** | 문서 임베딩 커버리지 확인 및 재처리 |
| **사용법** | `/embedding-check <project-id> [--reembed]` |

**출력 예시**:

```markdown
## 임베딩 상태: proj123

- 전체 문서: 48개
- 임베딩 완료: 45개
- 커버리지: 93.8%
- 미처리 문서: 3개
  - docs/new-feature.md
  - docs/changelog/v2.0.md
  - README.md

**--reembed 옵션 사용 시 재임베딩 시작됨**
```

---

### 2.3 LLM/MCP 검증

#### `/llm-tool-test` - LLM Tool Calling 테스트

| 항목 | 내용 |
|------|------|
| **타입** | Skill |
| **목적** | Spring AI @Tool 기능 동작 검증 |
| **사용법** | `/llm-tool-test <project-id> [--tool TOOL_NAME]` |

**테스트 대상 Tools**:

| Tool | 클래스 | 테스트 방법 |
|------|-------|-----------|
| `searchDocuments` | DocumentTools | "인증 관련 문서 찾아줘" |
| `listDocuments` | DocumentTools | "모든 문서 목록 보여줘" |
| `getDocument` | DocumentTools | "README 내용 보여줘" |
| `listBranches` | GitTools | "브랜치 목록 알려줘" |
| `createBranch` | GitTools | "새 브랜치 만들어줘" |

**워크플로우**:

```
/llm-tool-test proj123 --tool searchDocuments
    │
    ├─ 1. LLM 채팅 요청
    │   POST /api/llm/chat
    │   { message: "docs 폴더에서 인증 관련 문서 찾아줘" }
    │
    ├─ 2. Tool 호출 검증
    │   - 응답에 문서 경로 포함 여부
    │   - 응답 길이 적절성
    │
    └─ 3. 결과 리포트
```

---

#### `/mcp-verify` - MCP 엔드포인트 검증

| 항목 | 내용 |
|------|------|
| **타입** | Skill |
| **목적** | MCP Tools API 전체 동작 검증 |
| **사용법** | `/mcp-verify [--endpoint ENDPOINT]` |

**검증 대상 엔드포인트**:

| 엔드포인트 | 메서드 | 설명 |
|-----------|-------|------|
| `/mcp/tools/list_documents` | POST | 문서 목록 조회 |
| `/mcp/tools/get_document` | POST | 문서 내용 조회 |
| `/mcp/tools/search_documents` | POST | 문서 검색 |
| `/mcp/tools/list_document_versions` | POST | 버전 목록 |
| `/mcp/tools/diff_document` | POST | 버전 비교 |
| `/mcp/tools/sync_repository` | POST | 동기화 트리거 |

**출력 예시**:

```markdown
## MCP 엔드포인트 검증 결과

| 엔드포인트 | 상태 | 응답 시간 |
|-----------|-----|----------|
| list_documents | PASS | 45ms |
| get_document | PASS | 32ms |
| search_documents | PASS | 128ms |
| list_document_versions | PASS | 28ms |
| diff_document | PASS | 156ms |
| sync_repository | PASS | 89ms |

**전체: 6/6 통과**
```

---

### 2.4 서브에이전트

#### `doc-quality-analyzer` - 문서 품질 분석

| 항목 | 내용 |
|------|------|
| **타입** | Subagent |
| **목적** | 프로젝트 문서 품질 분석 및 개선 제안 |
| **트리거** | `/analyze-docs <project-id> [--depth LEVEL]` |

**분석 깊이**:

| 레벨 | 분석 항목 |
|-----|----------|
| `quick` | 구조만 (헤딩, README 존재) |
| `standard` | + 링크 유효성 검사 |
| `deep` | + 내용 분석 (길이, 오래된 문서, 코드 블록) |

**분석 항목 상세**:

| 카테고리 | 검사 항목 | 심각도 |
|---------|---------|-------|
| 구조 | 헤딩 계층 (H1→H2→H3) | Warning |
| 구조 | 디렉토리별 README 존재 | Info |
| 링크 | 내부 링크 대상 존재 | Critical |
| 링크 | 외부 링크 유효성 | Warning |
| 내용 | 문서 길이 < 200자 | Warning |
| 내용 | 마지막 수정 6개월+ | Info |
| 내용 | 코드 블록 언어 지정 | Info |

**출력 예시**:

```markdown
## 문서 품질 분석 결과

### 요약
- 분석 문서: 24개
- 발견된 이슈: 7개 (Critical: 1, Warning: 4, Info: 2)

### Critical Issues
1. **깨진 링크** - `docs/api/endpoints.md:45`
   - `../auth/oauth.md` 파일이 존재하지 않음
   - 제안: 링크 대상 확인 또는 문서 생성

### Warnings
1. **헤딩 계층 문제** - `docs/setup.md`
   - H1 없이 H2로 시작
   - 제안: `# Setup Guide` 헤딩 추가

2. **짧은 문서** - `docs/faq.md` (89자)
   - 내용이 너무 짧음
   - 제안: 내용 보강 또는 다른 문서와 병합

### Info
1. **오래된 문서** - `docs/deployment/docker.md`
   - 마지막 수정: 8개월 전
   - 제안: 내용 검토 필요

### 권장 사항 (우선순위순)
1. [ ] 깨진 링크 수정 (Critical)
2. [ ] 헤딩 구조 정리 (Warning)
3. [ ] 오래된 문서 검토 (Info)
```

---

#### `e2e-workflow-tester` - E2E 워크플로우 테스트

| 항목 | 내용 |
|------|------|
| **타입** | Subagent |
| **목적** | 주요 사용자 시나리오 E2E 자동 테스트 |
| **트리거** | `/e2e-test [scenario]` |

**시나리오**:

| 시나리오 | 설명 | 테스트 단계 |
|---------|------|-----------|
| `new-project` | 신규 프로젝트 셋업 | 프로젝트 생성 → 레포 연결 → 동기화 → 문서 확인 → 정리 |
| `search-flow` | 검색 워크플로우 | 키워드 검색 → 시맨틱 검색 → 하이브리드 검색 → 결과 비교 |
| `llm-chat` | LLM 채팅 흐름 | 채팅 시작 → Tool 호출 → 후속 질문 → 인용 확인 |
| `version-history` | 버전 히스토리 | 문서 조회 → 버전 목록 → Diff 비교 |

**`new-project` 시나리오 상세**:

```
/e2e-test new-project
    │
    ├─ 1. 테스트 프로젝트 생성
    │   POST /api/projects
    │   { name: "E2E Test Project" }
    │
    ├─ 2. 레포지토리 연결
    │   POST /api/projects/{id}/repositories
    │   { provider: "LOCAL", path: "/tmp/test-repo" }
    │
    ├─ 3. 동기화 실행
    │   POST /api/repositories/{id}/sync
    │   → 완료 대기
    │
    ├─ 4. 문서 생성 확인
    │   GET /api/repositories/{id}/documents
    │   → documents.length > 0
    │
    ├─ 5. 검색 동작 확인
    │   POST /mcp/tools/search_documents
    │   → 결과 반환 확인
    │
    └─ 6. 정리 (Cleanup)
        DELETE /api/projects/{id}
```

---

## Part 3: 파일 구조

```
docst-mng/
├── .claude/
│   ├── hooks/
│   │   ├── pre-commit-lint-format.yaml
│   │   ├── pre-commit-test-affected.yaml
│   │   └── post-build-verify.yaml
│   │
│   ├── commands/
│   │   ├── dev-start.yaml
│   │   ├── branch-create.yaml
│   │   ├── test-run.yaml
│   │   ├── db-migrate.yaml
│   │   └── deps-update.yaml
│   │
│   ├── skills/
│   │   ├── sync-test.yaml
│   │   ├── search-validate.yaml
│   │   ├── embedding-check.yaml
│   │   ├── llm-tool-test.yaml
│   │   └── mcp-verify.yaml
│   │
│   ├── subagents/
│   │   ├── doc-quality-analyzer.yaml
│   │   └── e2e-workflow-tester.yaml
│   │
│   ├── config.yaml              # 전역 설정
│   └── settings.local.json      # 기존 권한 설정
│
├── CLAUDE_CODE_EXTENSIONS.md    # 이 문서
└── CLAUDE.md                    # 프로젝트 가이드
```

### config.yaml 예시

```yaml
# .claude/config.yaml
version: "1.0"

defaults:
  timeout: 120000
  retry: 2

environment:
  backend:
    root: ./backend
    build: ./gradlew build
    test: ./gradlew test

  frontend:
    root: ./frontend
    build: npm run build
    test: npm run lint

api:
  base_url: http://localhost:8342
  timeout: 30000

hooks:
  pre-commit:
    enabled: true

  post-build:
    enabled: true
```

---

## Part 4: 구현 우선순위

### Phase 1: 필수 범용 확장 (1주)

| 순위 | 확장 | 타입 | 이유 |
|-----|-----|-----|-----|
| 1 | `pre-commit-lint-format` | Hook | 코드 품질 기본 보장 |
| 2 | `/dev-start` | Command | 개발 환경 설정 간소화 |
| 3 | `/test-run` | Command | 테스트 실행 표준화 |
| 4 | `/branch-create` | Command | Git 워크플로우 일관성 |

### Phase 2: 도메인 특화 스킬 (2주)

| 순위 | 확장 | 타입 | 이유 |
|-----|-----|-----|-----|
| 1 | `/sync-test` | Skill | 핵심 기능 검증 |
| 2 | `/mcp-verify` | Skill | MCP 인터페이스 신뢰성 |
| 3 | `/search-validate` | Skill | RAG 품질 보장 |
| 4 | `/llm-tool-test` | Skill | LLM 통합 검증 |

### Phase 3: 고급 자동화 (2주)

| 순위 | 확장 | 타입 | 이유 |
|-----|-----|-----|-----|
| 1 | `doc-quality-analyzer` | Subagent | 문서 품질 자동화 |
| 2 | `e2e-workflow-tester` | Subagent | 회귀 테스트 자동화 |
| 3 | `/embedding-check` | Skill | 벡터 DB 상태 모니터링 |

---

## Part 5: 검증 방법

### 범용 확장 검증

```bash
# 1. Pre-commit Hook 테스트
echo "test" >> frontend/src/test.ts
git add .
git commit -m "test: hook verification"
# → lint 에러 발생 시 커밋 차단 확인

# 2. /dev-start 테스트
/dev-start --full
# → http://localhost:8342/actuator/health 접속
# → http://localhost:3002 접속

# 3. /test-run 테스트
/test-run --scope unit --target backend --coverage
# → 테스트 실행 및 커버리지 리포트 생성 확인
```

### 도메인 확장 검증

```bash
# 1. 동기화 테스트
/sync-test <repository-id> --mode full
# → 문서 동기화 완료 및 카운트 확인

# 2. 검색 검증
/search-validate <project-id> --query "인증 방법" --expectedPath "auth.md"
# → 3가지 검색 모드 결과 비교표 확인

# 3. MCP 검증
/mcp-verify --endpoint all
# → 모든 엔드포인트 PASS 확인

# 4. LLM Tool 테스트
/llm-tool-test <project-id> --tool all
# → 각 Tool 호출 성공 확인
```

### E2E 워크플로우 검증

```bash
# 신규 프로젝트 시나리오
/e2e-test new-project
# → 모든 단계 PASS 확인

# 검색 워크플로우
/e2e-test search-flow
# → 검색 결과 비교 확인
```

---

## 주요 참조 파일

| 파일 | 위치 | 용도 |
|------|------|------|
| build.gradle.kts | `backend/` | 빌드/테스트 명령어, Checkstyle 설정 |
| package.json | `frontend/` | npm 스크립트 (lint, format, build) |
| McpController.java | `backend/.../mcp/` | MCP 엔드포인트 구현 |
| DocumentTools.java | `backend/.../llm/tools/` | @Tool 어노테이션 패턴 |
| GitTools.java | `backend/.../llm/tools/` | Git 관련 Tool 구현 |
| docker-compose.yml | 프로젝트 루트 | 인프라 서비스 정의 |

---

## 결론

이 계획서는 Docst 프로젝트의 개발 생산성과 품질 향상을 위한 Claude Code 확장 기능을 정의합니다.

**총 15개 확장 기능**:
- 범용 확장 8개: 모든 풀스택 프로젝트에서 재사용 가능
- 도메인 스킬 5개: 문서 동기화, RAG, LLM, MCP 기능 검증
- 서브에이전트 2개: 문서 품질 분석, E2E 워크플로우 테스트

구현 후 `.claude/` 디렉토리에 YAML 파일로 관리하며, `settings.local.json`에서 활성화/비활성화를 제어합니다.