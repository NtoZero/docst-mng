# Docst - Unified Documentation Hub

통합 문서 관리 및 AI 기반 검색 플랫폼

## Quick Start

### 1. 환경 설정

```bash
# .env 파일 생성 (인프라 설정만 포함)
cp .env.example .env

# 필수: 보안 키 생성 (JWT_SECRET, DOCST_ENCRYPTION_KEY)
# .env 파일을 열어 최소한 아래 값들을 설정하세요:
#   - JWT_SECRET (openssl rand -base64 32)
#   - DOCST_ENCRYPTION_KEY (openssl rand -base64 32)
nano .env
```

**참고**: OpenAI/Ollama API 키는 환경 변수가 아닌 웹 UI에서 설정합니다 (아래 4단계 참조).

### 2. Docker Compose 실행

```bash
# PostgreSQL + Ollama(optional) 시작
docker-compose up -d

# 로그 확인
docker-compose logs -f
```

### 3. 백엔드 실행

```bash
cd backend

# 빌드 및 실행
./gradlew bootRun

# 또는 빌드 후 실행
./gradlew build
java -jar build/libs/docst-0.1.0.jar
```

백엔드 서버: http://localhost:8342

### 4. 프론트엔드 실행 및 API 키 설정

```bash
cd frontend
npm install
npm run dev
```

프론트엔드: http://localhost:3000

**API 키 설정 (필수)**:
1. 웹 UI 접속: http://localhost:3000
2. 로그인 (기본 계정: admin@docst.local)
3. **Settings → Credentials** 이동
4. **Add Credential** 클릭
5. **Type**: `OPENAI_API_KEY` 선택
6. **Secret**: `sk-proj-your-actual-key-here` 입력
7. **Scope**: `SYSTEM` (전체 공용) 또는 `PROJECT` (프로젝트별)
8. **Save** 클릭

API 키는 AES-256-GCM으로 암호화되어 데이터베이스에 안전하게 저장됩니다.

## 테스트

### 단위 테스트

```bash
cd backend

# 모든 테스트 실행
./gradlew test

# 특정 테스트만 실행
./gradlew test --tests "com.docst.chunking.*"
```

### 통합 테스트 (OpenAI API 필요)

```bash
# 통합 테스트 실행 전 웹 UI에서 OPENAI_API_KEY 설정 필요
# (Settings → Credentials → Add Credential → OPENAI_API_KEY)

# 시맨틱 서치 통합 테스트 실행
cd backend
./gradlew test --tests "com.docst.integration.SemanticSearchIntegrationTest"
```

**주의**:
- 통합 테스트는 실제 OpenAI API를 호출하므로 소량의 비용이 발생합니다.
- 테스트 실행 전 웹 UI에서 OpenAI API 키를 설정해야 합니다.

자세한 내용은 [통합 테스트 가이드](docs/impl/integration-test-guide.md)를 참고하세요.

## 주요 기능

- ✅ **Git 연동**: GitHub/Local 레포지토리 문서 동기화
- ✅ **버전 관리**: Git 커밋 기반 문서 버전 추적
- ✅ **청킹**: 마크다운 헤딩 기반 스마트 청킹
- ✅ **임베딩**: OpenAI/Ollama 기반 벡터 임베딩
- ✅ **검색**:
  - 키워드 검색 (PostgreSQL ILIKE)
  - 시맨틱 검색 (pgvector 코사인 유사도)
  - 하이브리드 검색 (RRF 융합)
- ✅ **MCP Server**: Spring AI 1.1.0+ 기반 AI 에이전트 연동
  - SSE Transport (Claude Desktop/Claude Code)
  - 10개 Tool 제공 (문서 조회/검색/수정/Git 연동)
  - @Tool annotation 선언적 정의

## 기술 스택

### Backend
- Java 21, Spring Boot 3.5
- PostgreSQL 16 + pgvector
- Spring AI 1.0.0-M5
- JGit, Flexmark

### Frontend (추후)
- Next.js 16, TypeScript
- TanStack Query, Zustand
- Tailwind CSS, shadcn/ui

### Infrastructure
- Docker + docker-compose
- Flyway (DB migrations)

## 문서

- [CLAUDE.md](CLAUDE.md) - 전체 프로젝트 가이드
- [MCP Server 연결 가이드](docs/core/mcp-connect.md) - Claude Desktop/Code 연동
- [Phase 2 구현 계획](docs/plan/phase-2-3-implementation-plan.md)
- [통합 테스트 가이드](docs/impl/integration-test-guide.md)

## 환경 변수

**중요**: API 키는 환경 변수가 아닌 **웹 UI**에서 관리합니다.

### 인프라 변수 (.env 파일)

필수 환경 변수 (부트스트랩 전용):

```bash
# Database
DB_HOST=localhost
DB_PORT=5434
DB_NAME=docst
DB_USERNAME=postgres
DB_PASSWORD=postgres

# Security (REQUIRED - 프로덕션에서 반드시 변경)
JWT_SECRET=your-secret-key-change-in-production
DOCST_ENCRYPTION_KEY=your-encryption-key-change-in-production
```

선택적 변수:

```bash
# Application
DOCST_BASE_URL=http://localhost:8342
DOCST_GIT_ROOT=/data/git
DOCST_AUTH_MODE=local

# GitHub OAuth
GITHUB_CLIENT_ID=your-client-id
GITHUB_CLIENT_SECRET=your-client-secret

# Admin Auto-initialization
DOCST_ADMIN_AUTO_INIT=false
DOCST_ADMIN_EMAIL=admin@docst.local
```

전체 목록은 [.env.example](.env.example)을 참고하세요.

### API 키 설정 (웹 UI)

**환경 변수 대신 웹 UI에서 관리**:

1. 서버 시작: `docker-compose up -d`
2. 웹 UI 접속: http://localhost:3000
3. **Settings → Credentials** 이동
4. **Add Credential** 클릭하여 API 키 추가:
   - `OPENAI_API_KEY`: OpenAI API 키
   - `ANTHROPIC_API_KEY`: Claude API 키 (선택)
   - `NEO4J_AUTH`: Neo4j 인증 정보 (Graph RAG 사용 시)

자세한 내용: [docs/impl/phase6/dynamic-llm.md](docs/impl/phase6/dynamic-llm.md)

## API 엔드포인트

### 인증
- `POST /api/auth/local/login` - 로컬 로그인

### 프로젝트
- `GET /api/projects` - 프로젝트 목록
- `POST /api/projects` - 프로젝트 생성

### 레포지토리
- `POST /api/projects/{id}/repositories` - 레포지토리 연결
- `POST /api/repositories/{id}/sync` - 동기화 실행

### 문서
- `GET /api/repositories/{id}/documents` - 문서 목록
- `GET /api/documents/{id}` - 문서 상세
- `GET /api/documents/{id}/versions` - 버전 목록

### 검색
- `GET /api/projects/{id}/search?q=query&mode=keyword` - 키워드 검색
- `GET /api/projects/{id}/search?q=query&mode=semantic` - 시맨틱 검색
- `GET /api/projects/{id}/search?q=query&mode=hybrid` - 하이브리드 검색

### MCP Server (Spring AI 1.1.0+)

Docst는 **Spring AI MCP Server**를 통해 AI 에이전트(Claude Desktop/Claude Code)가 문서를 조회하고 관리할 수 있는 인터페이스를 제공합니다.

#### SSE Endpoint
- `GET /sse` - SSE 연결 (세션 생성)
- `POST /mcp/messages` - 클라이언트 메시지 수신

#### Available Tools

| Tool | Description |
|------|-------------|
| `list_projects` | 사용자가 접근 가능한 프로젝트 목록 조회 |
| `list_documents` | 프로젝트/레포지토리의 문서 목록 조회 |
| `get_document` | 문서 내용 조회 (버전 지정 가능) |
| `list_document_versions` | 문서 버전 히스토리 |
| `diff_document` | 두 버전 비교 (unified diff) |
| `search_documents` | 키워드/semantic/hybrid 검색 |
| `sync_repository` | 원격 Git에서 최신 변경사항 동기화 |
| `create_document` | 새 문서 생성 (commit 옵션) |
| `update_document` | 문서 내용 업데이트 (commit 옵션) |
| `push_to_remote` | 로컬 변경사항 원격 푸시 |

#### Claude Code 연동 (권장)

Claude Code CLI는 SSE transport를 직접 지원합니다:

```bash
# 기본 설정 (현재 프로젝트에서만 사용, 개인용)
claude mcp add --transport sse docst http://localhost:8342/sse --header "X-API-Key: YOUR_API_KEY"

# 글로벌 설정 (모든 프로젝트에서 사용)
claude mcp add --transport sse --scope user docst http://localhost:8342/sse --header "X-API-Key: YOUR_API_KEY"

# 등록된 서버 확인
claude mcp list
```

#### Claude Desktop 연동

Supergateway를 통한 STDIO ↔ SSE 변환:

```bash
# Supergateway 설치
npm install -g supergateway
```

**설정 파일** (`%APPDATA%\Claude\claude_desktop_config.json`):
```json
{
  "mcpServers": {
    "docst": {
      "command": "supergateway",
      "args": [
        "--sse", "http://localhost:8342/sse",
        "--header", "X-API-Key: YOUR_API_KEY"
      ]
    }
  }
}
```

자세한 내용: [docs/core/mcp-connect.md](docs/core/mcp-connect.md)

## 개발 현황

### Phase 1: MVP ✅
- [x] 기본 인증 및 사용자 관리
- [x] 프로젝트/레포지토리 관리
- [x] Git 동기화 (JGit)
- [x] 문서/버전 관리
- [x] 키워드 검색

### Phase 2: Semantic Search ✅
- [x] 문서 청킹 (마크다운 헤딩 기반)
- [x] OpenAI 임베딩 통합
- [x] pgvector VectorStore
- [x] 시맨틱 검색
- [x] 하이브리드 검색 (RRF)
- [x] 단위 테스트 (55개)
- [x] 통합 테스트

### Phase 3: Advanced Features (예정)
- [ ] GitHub OAuth
- [ ] Webhook 자동 동기화
- [ ] 문서 관계 그래프
- [ ] 영향 분석
- [ ] 프론트엔드 UI

## 라이선스

MIT License

## 기여

이슈 및 PR을 환영합니다!

---

**Made with ❤️ using Spring AI and pgvector**
