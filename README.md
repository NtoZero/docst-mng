# Docst - Unified Documentation Hub

통합 문서 관리 및 AI 기반 검색 플랫폼

## Quick Start

### 1. 환경 설정

```bash
# .env 파일 생성
cp .env.example .env

# OpenAI API Key 설정 (필수)
# .env 파일을 열어 OPENAI_API_KEY 설정
nano .env
```

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

### 4. 프론트엔드 실행 (추후)

```bash
cd frontend
npm install
npm run dev
```

프론트엔드: http://localhost:3000

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
# OPENAI_API_KEY 환경 변수 설정
export OPENAI_API_KEY=sk-proj-your-api-key

# 시맨틱 서치 통합 테스트 실행
./gradlew test --tests "com.docst.integration.SemanticSearchIntegrationTest"
```

**주의**: 통합 테스트는 실제 OpenAI API를 호출하므로 소량의 비용이 발생합니다.

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
- ✅ **MCP Tools**: AI 에이전트 연동

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
- [Phase 2 구현 계획](docs/plan/phase-2-3-implementation-plan.md)
- [통합 테스트 가이드](docs/impl/integration-test-guide.md)

## 환경 변수

필수 환경 변수:

```bash
# OpenAI (기본)
OPENAI_API_KEY=sk-proj-...

# Database
DB_HOST=localhost
DB_PORT=5434
DB_NAME=docst
DB_USERNAME=postgres
DB_PASSWORD=postgres
```

선택적 환경 변수:

```bash
# Ollama (로컬 사용 시)
OLLAMA_EMBEDDING_ENABLED=true
OLLAMA_BASE_URL=http://localhost:11434
EMBEDDING_DIMENSIONS=768  # nomic-embed-text
```

전체 목록은 [.env.example](.env.example)을 참고하세요.

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

### MCP Tools
- `POST /mcp/tools/list_documents` - 문서 목록
- `POST /mcp/tools/get_document` - 문서 조회
- `POST /mcp/tools/search_documents` - 문서 검색
- `POST /mcp/tools/sync_repository` - 동기화

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
