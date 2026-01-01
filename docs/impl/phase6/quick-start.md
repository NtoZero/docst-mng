# Phase 6 빠른 시작 가이드

> 5분 안에 LLM 통합 기능 실행하기

---

## 사전 준비

### 1. OpenAI API Key 발급

1. https://platform.openai.com/api-keys 접속
2. "Create new secret key" 클릭
3. 키 복사 (한 번만 표시됨!)

### 2. 환경 변수 설정

```bash
# 프로젝트 루트에 .env 파일 생성
cd C:\Dev\project\docst-mng

# backend/.env 파일 (권장)
cat > backend/.env << EOF
OPENAI_API_KEY=sk-proj-your-actual-key-here
EOF

# 또는 시스템 환경 변수 설정
set OPENAI_API_KEY=sk-proj-your-actual-key-here  # Windows
export OPENAI_API_KEY=sk-proj-your-actual-key-here  # Linux/Mac
```

---

## 백엔드 실행

### Option 1: Gradle 직접 실행 (권장)

```bash
cd backend

# Windows
gradlew.bat bootRun

# Linux/Mac
./gradlew bootRun
```

**실행 확인:**
```
2025-01-02 15:30:00 INFO  DocstApplication - Started DocstApplication in 3.2 seconds
2025-01-02 15:30:00 INFO  TomcatWebServer - Tomcat started on port(s): 8342 (http)
```

### Option 2: Docker Compose

```bash
cd C:\Dev\project\docst-mng

# 백엔드 + PostgreSQL 실행
docker-compose up -d backend db

# 로그 확인
docker-compose logs -f backend
```

### 실행 검증

```bash
# Health Check
curl http://localhost:8342/actuator/health

# 응답 예시
{
  "status": "UP"
}
```

---

## 프론트엔드 실행

### 1. 의존성 설치 (최초 1회)

```bash
cd frontend

npm install
```

### 2. 개발 서버 실행

```bash
npm run dev
```

**실행 확인:**
```
▲ Next.js 16.1.0
- Local:        http://localhost:3000
- Environments: .env.local

✓ Ready in 2.3s
```

---

## 첫 번째 채팅 테스트

### 1. Playground 접속

브라우저에서 다음 URL 접속:
```
http://localhost:3000/ko/playground
```

**주의**: 프로젝트가 생성되어 있어야 합니다!

### 2. 프로젝트 생성 (필요한 경우)

```bash
# cURL로 프로젝트 생성
curl -X POST http://localhost:8342/api/projects \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your-token>" \
  -d '{
    "name": "Test Project",
    "description": "LLM 테스트용 프로젝트"
  }'

# 응답에서 projectId 확인
{
  "id": "abc-123-...",
  "name": "Test Project",
  ...
}
```

### 3. 첫 채팅

**프롬프트 입력:**
```
프로젝트의 모든 문서를 나열해줘
```

**예상 응답:**
```
프로젝트에 총 X개의 문서가 있습니다:
1. README.md - Project Overview
2. docs/architecture.md - System Architecture
...
```

---

## 샘플 프롬프트

### 문서 검색

```
authentication에 관한 문서를 찾아줘
```

```
README 파일을 모두 검색해줘
```

### 문서 조회

```
README.md 파일 내용을 보여줘
```

```
docs/architecture.md의 내용을 요약해줘
```

### 복합 작업

```
프로젝트에서 API 관련 문서를 찾고, 그 중 하나의 내용을 요약해줘
```

---

## 문제 해결

### 1. "No Project Selected" 오류

**원인**: Playground 페이지가 프로젝트 컨텍스트 없이 접속됨

**해결:**
1. 좌측 사이드바에서 프로젝트 선택
2. 또는 프로젝트 페이지에서 Playground 링크 클릭

### 2. "OPENAI_API_KEY not set" 오류

**원인**: OpenAI API Key 환경 변수 미설정

**해결:**
```bash
# 백엔드 재시작 전에 환경 변수 설정
cd backend
set OPENAI_API_KEY=sk-proj-...  # Windows
./gradlew bootRun
```

### 3. "Chat API error: 401" 오류

**원인**: 잘못된 OpenAI API Key 또는 크레딧 부족

**해결:**
1. https://platform.openai.com/api-keys 에서 키 확인
2. https://platform.openai.com/usage 에서 크레딧 확인

### 4. "Tool: searchDocuments - No documents found"

**원인**: 프로젝트에 문서가 없음

**해결:**
1. 레포지토리 연결: Projects → [프로젝트] → Repositories → Add
2. 동기화 실행: Repositories → [레포지토리] → Sync

---

## 다음 단계

### 1. 레포지토리 연결

```bash
# GitHub 레포지토리 연결 예시
curl -X POST http://localhost:8342/api/projects/{projectId}/repositories \
  -H "Authorization: Bearer <token>" \
  -d '{
    "provider": "GITHUB",
    "owner": "anthropics",
    "name": "claude-code",
    "defaultBranch": "main"
  }'
```

### 2. 문서 동기화

```bash
# 동기화 실행
curl -X POST http://localhost:8342/api/repositories/{repoId}/sync \
  -H "Authorization: Bearer <token>"
```

### 3. 고급 프롬프트 시도

```
프로젝트의 아키텍처 문서를 찾아서, 주요 컴포넌트들을 정리해줘
```

```
최근에 변경된 문서 목록을 보여주고, 각각 어떤 내용인지 설명해줘
```

---

## 개발 팁

### 1. 백엔드 로그 확인

```bash
# 실시간 로그
cd backend
./gradlew bootRun | grep -E "LLM|Tool"
```

**주요 로그:**
```
LlmService - LLM chat request: projectId=abc, sessionId=session-1
LlmToolsConfig - Tool: searchDocuments - query=README, projectId=abc
```

### 2. 프론트엔드 개발자 도구

- **Network 탭**: SSE 스트리밍 확인
- **Console 탭**: 에러 메시지 확인

### 3. API 직접 테스트

```bash
# 동기 채팅 테스트
curl -X POST http://localhost:8342/api/llm/chat \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{
    "message": "Hello",
    "projectId": "abc-123",
    "sessionId": "test-1"
  }'

# 스트리밍 채팅 테스트
curl -N -X POST http://localhost:8342/api/llm/chat/stream \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{
    "message": "Hello",
    "projectId": "abc-123",
    "sessionId": "test-1"
  }'
```

---

## 비용 관리

### OpenAI API 사용량 확인

https://platform.openai.com/usage

### 예상 비용

**GPT-4o 가격 (2025년 1월 기준):**
- Input: $2.50 / 1M tokens
- Output: $10.00 / 1M tokens

**예시:**
- 일반 질문 (100 tokens 입력 + 200 tokens 출력): $0.0025
- 문서 조회 (500 tokens 입력 + 1000 tokens 출력): $0.0113

**절약 팁:**
1. 짧은 프롬프트 사용
2. 필요한 정보만 요청
3. 개발/테스트 시 gpt-3.5-turbo 사용 고려

---

## 유용한 명령어

### 백엔드

```bash
# 빌드 (테스트 제외)
./gradlew build -x test

# 테스트만 실행
./gradlew test

# 컴파일만
./gradlew compileJava

# 클린 빌드
./gradlew clean build
```

### 프론트엔드

```bash
# 개발 서버
npm run dev

# 프로덕션 빌드
npm run build

# 빌드 결과 실행
npm run start

# TypeScript 타입 체크
npm run type-check
```

### Docker

```bash
# 전체 실행
docker-compose up -d

# 특정 서비스만
docker-compose up -d backend

# 로그 확인
docker-compose logs -f backend

# 중지
docker-compose stop

# 삭제
docker-compose down
```

---

## 자주 묻는 질문

### Q1. 프로젝트가 없으면 Playground를 사용할 수 없나요?

**A:** 네, 현재는 프로젝트 컨텍스트가 필요합니다. Week 3-4에 프로젝트 선택 UI를 추가할 예정입니다.

### Q2. 대화 히스토리가 저장되나요?

**A:** 백엔드의 MessageWindowChatMemory에 일시적으로 저장되지만, 서버 재시작 시 초기화됩니다. Week 3-4에 영속화를 추가할 예정입니다.

### Q3. 다른 LLM Provider를 사용할 수 있나요?

**A:** 네, Spring AI를 통해 Anthropic Claude, Ollama 등을 사용할 수 있습니다. application.yml에서 설정을 변경하면 됩니다.

### Q4. Tool이 호출되는지 어떻게 확인하나요?

**A:** 백엔드 로그에 "Tool: searchDocuments - ..." 형태로 출력됩니다. 또는 프론트엔드 개발자 도구의 Network 탭에서 확인 가능합니다.

### Q5. 스트리밍 응답이 느린데 정상인가요?

**A:** LLM 응답 속도는 네트워크, OpenAI 서버 상태, 프롬프트 복잡도에 따라 다릅니다. 일반적으로 1-3초 내에 첫 응답이 시작됩니다.

---

## 추가 리소스

- [Phase 6 전체 개요](./README.md)
- [백엔드 구현 상세](./backend.md)
- [프론트엔드 구현 상세](./frontend.md)
- [문제 해결 가이드](./troubleshooting.md)
- [Spring AI 문서](https://docs.spring.io/spring-ai/reference/)
