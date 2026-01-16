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

- **Git 연동**: GitHub/Local 레포지토리 문서 동기화
- **버전 관리**: Git 커밋 기반 문서 버전 추적
- **AI 검색**: 키워드/시맨틱/하이브리드 검색 지원
- **MCP Server**: AI 에이전트(Claude Desktop/Code) 연동
  - SSE Transport
  - 10개 Tool 제공 (문서 조회/검색/수정/Git 연동)

## 기술 스택

### Backend
- Java 21, Spring Boot 3.5
- PostgreSQL 16 + pgvector
- Spring AI 1.1.0+
- JGit, Flexmark

### Frontend
- Next.js 16, TypeScript
- TanStack Query, Zustand
- Tailwind CSS, shadcn/ui

### Infrastructure
- Docker + docker-compose
- Flyway (DB migrations)

## 문서

- [CLAUDE.md](CLAUDE.md) - 프로젝트 가이드
- [MCP Server 연결 가이드](docs/core/mcp-connect.md) - AI 에이전트 연동
- [통합 테스트 가이드](docs/impl/integration-test-guide.md)

## 환경 변수

**중요**: API 키는 환경 변수가 아닌 **웹 UI (Settings → Credentials)**에서 관리합니다.

### 필수 환경 변수 (.env 파일)

```bash
# Database
DB_HOST=localhost
DB_PORT=5434
DB_NAME=docst
DB_USERNAME=postgres
DB_PASSWORD=postgres

# Security (프로덕션에서 반드시 변경)
JWT_SECRET=your-secret-key-change-in-production
DOCST_ENCRYPTION_KEY=your-encryption-key-change-in-production
```

전체 목록은 [.env.example](.env.example)을 참고하세요.

## API 엔드포인트

### REST API

| 카테고리 | 엔드포인트 | 설명 |
|---------|-----------|------|
| 인증 | `POST /api/auth/local/login` | 로컬 로그인 |
| 프로젝트 | `GET /api/projects` | 프로젝트 목록 |
| 프로젝트 | `POST /api/projects` | 프로젝트 생성 |
| 레포지토리 | `POST /api/projects/{id}/repositories` | 레포지토리 연결 |
| 레포지토리 | `POST /api/repositories/{id}/sync` | 동기화 실행 |
| 문서 | `GET /api/repositories/{id}/documents` | 문서 목록 |
| 문서 | `GET /api/documents/{id}` | 문서 상세 |
| 문서 | `GET /api/documents/{id}/versions` | 버전 목록 |
| 검색 | `GET /api/projects/{id}/search` | 문서 검색 (keyword/semantic/hybrid) |

### MCP Server

AI 에이전트(Claude Desktop/Code)가 문서를 조회하고 관리할 수 있는 10개의 Tool을 제공합니다.

#### Claude Code 연동 (권장)

SSE transport를 직접 지원:

```bash
claude mcp add --transport sse docst http://localhost:8342/sse \
  --header "X-API-Key: YOUR_API_KEY"
```

#### Claude Desktop 연동

Supergateway를 통한 STDIO ↔ SSE 변환:

```bash
# 1. Supergateway 설치
npm install -g supergateway

# 2. 설정 파일 편집 (Windows: %APPDATA%\Claude\claude_desktop_config.json)
```

**claude_desktop_config.json**:
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

**주요 Tools**: `list_projects`, `search_documents`, `get_document`, `update_document`, `sync_repository` 등

자세한 내용: [MCP 연결 가이드](docs/core/mcp-connect.md)

## 라이선스

MIT License

## 기여

이슈 및 PR을 환영합니다!

---

**Made with ❤️ using Spring AI and pgvector**
