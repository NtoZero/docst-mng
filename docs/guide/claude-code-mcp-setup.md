# Claude Code 전역 설정에 Docst MCP 추가 가이드

Docst MCP 서버를 Claude Code 전역 설정에 등록하면, 모든 프로젝트에서 Docst MCP 도구(문서 검색, 동기화 등)를 사용할 수 있습니다.

## 전제 조건

- Docst 백엔드가 `http://localhost:8342`에서 실행 중
- 관리자 계정으로 로그인 가능

## Step 1: API Key 생성

### 방법 A: 웹 UI (권장)

1. http://localhost:3000 접속 후 로그인
2. **Settings → API Keys** 이동
3. **Create API Key** 클릭
4. Name: `claude-code-global`
5. 생성된 API Key(`docst_ak_...`)를 복사하여 보관

### 방법 B: REST API

```bash
# 1. JWT 토큰 획득
TOKEN=$(curl -s -X POST http://localhost:8342/api/auth/local/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@docst.local","password":"YOUR_PASSWORD"}' \
  | jq -r '.token')

# 2. API Key 생성
curl -s -X POST http://localhost:8342/api/auth/api-keys \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"claude-code-global"}' | jq .
```

생성된 API Key(`docst_ak_...`)를 복사하여 보관합니다.

## Step 2: Claude Code 전역 설정에 MCP 추가

### 방법 A: CLI 명령어 (권장)

```bash
claude mcp add --transport sse docst http://localhost:8342/sse \
  --header "X-API-Key: docst_ak_xxxxxxxx..."
```

### 방법 B: 설정 파일 직접 편집

`~/.claude/settings.json`에 `mcpServers` 섹션을 추가합니다:

```json
{
  "skipDangerousModePermissionPrompt": true,
  "mcpServers": {
    "docst": {
      "type": "sse",
      "url": "http://localhost:8342/sse",
      "headers": {
        "X-API-Key": "docst_ak_xxxxxxxx..."
      }
    }
  }
}
```

> `docst_ak_xxxxxxxx...` 부분을 Step 1에서 생성한 실제 API Key로 교체하세요.

## Step 3: 연결 확인

### MCP 서버 목록 확인

```bash
claude mcp list
```

출력에 `docst`가 표시되면 등록 성공입니다.

### SSE 연결 직접 테스트

```bash
curl -N -H "X-API-Key: docst_ak_..." http://localhost:8342/sse
```

SSE 이벤트 스트림이 수신되면 연결이 정상입니다. (`Ctrl+C`로 종료)

### Claude Code에서 도구 호출 테스트

Claude Code 대화에서 다음을 시도해보세요:

- "Docst에서 프로젝트 목록 조회해줘" → `list_projects` 도구 호출
- "문서 검색해줘: kubernetes" → `search_documents` 도구 호출

## 사용 가능한 MCP 도구

| Tool | Description |
|------|-------------|
| `list_projects` | 프로젝트 목록 조회 |
| `list_documents` | 문서 목록 조회 |
| `get_document` | 문서 내용 조회 |
| `list_document_versions` | 문서 버전 히스토리 |
| `diff_document` | 두 버전 비교 |
| `search_documents` | 키워드/semantic/hybrid 검색 |
| `sync_repository` | 레포지토리 동기화 |
| `create_document` | 새 문서 생성 |
| `update_document` | 문서 업데이트 |
| `push_to_remote` | 원격 푸시 |

## 문제 해결

| 증상 | 원인 | 해결 |
|------|------|------|
| `claude mcp list`에 docst 미표시 | 설정 파일 JSON 구문 오류 | `settings.json` JSON 유효성 검사 |
| 도구 호출 시 401 오류 | API Key 무효 또는 만료 | 새 API Key 생성 후 재등록 |
| 연결 타임아웃 | 백엔드 미실행 | `docker-compose up -d`로 백엔드 시작 |
| SSE 연결 즉시 끊김 | MCP SSE 엔드포인트 경로 오류 | URL이 `http://localhost:8342/sse`인지 확인 |
