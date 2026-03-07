# Docst MCP 설정 가이드 (Claude Code / Claude Desktop)

Docst MCP 서버를 등록하면 AI 에이전트에서 Docst 문서 검색, 동기화 등의 도구를 사용할 수 있습니다.

## 전제 조건

- Docst 백엔드 실행 중 (`http://localhost:8342`)
- API Key 발급 완료 (웹 UI → Settings → API Keys → `docst_ak_...` 형식)

---

## Claude Code 설정

> **주의**: `claude mcp add --header` 옵션은 CLI 인수 파싱 문제가 있으므로 `add-json` 방식을 사용합니다.

### 전역 설정 (모든 프로젝트에서 사용)

```bash
claude mcp add-json docst \
  '{"type":"sse","url":"http://localhost:8342/sse","headers":{"X-API-Key":"docst_ak_xxxxxxxx..."}}' \
  --scope user
```

### 현재 프로젝트에서만 사용

```bash
claude mcp add-json docst \
  '{"type":"sse","url":"http://localhost:8342/sse","headers":{"X-API-Key":"docst_ak_xxxxxxxx..."}}'
```

### scope 옵션

| scope | 설명 | 저장 위치 |
|-------|------|----------|
| `--scope user` | 전역 (모든 프로젝트) | `~/.claude.json` |
| `--scope local` | 현재 프로젝트만 (기본값) | `~/.claude.json` (프로젝트 경로 하위) |
| `--scope project` | 팀 공유용 | `.mcp.json` (Git 커밋 대상) |

### 확인 / 삭제

```bash
# 등록 확인
claude mcp list

# 삭제 (전역)
claude mcp remove -s user docst

# 삭제 (로컬)
claude mcp remove docst
```

---

## Claude Desktop 설정

`claude_desktop_config.json` 파일을 편집합니다.

**파일 위치**:
- macOS: `~/Library/Application Support/Claude/claude_desktop_config.json`
- Windows: `%APPDATA%\Claude\claude_desktop_config.json`

```json
{
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

> 편집 후 Claude Desktop을 **재시작**하세요.

---

## 연결 확인

### SSE 연결 직접 테스트

```bash
curl -N -H "X-API-Key: docst_ak_..." http://localhost:8342/sse
```

SSE 이벤트 스트림이 수신되면 연결이 정상입니다. (`Ctrl+C`로 종료)

### Claude Code에서 테스트

대화에서 다음을 시도해보세요:

- "Docst에서 프로젝트 목록 조회해줘" → `list_projects` 호출
- "문서 검색해줘: kubernetes" → `search_documents` 호출

### Claude Desktop에서 확인

설정 → MCP 탭에서 `docst` 서버가 연결됨(초록색)으로 표시되는지 확인합니다.

---

## 사용 가능한 MCP 도구

| 도구 | 설명 |
|------|------|
| `list_projects` | 프로젝트 목록 조회 |
| `list_documents` | 문서 목록 조회 |
| `get_document` | 문서 내용 조회 |
| `list_document_versions` | 문서 버전 히스토리 |
| `diff_document` | 두 버전 비교 |
| `search_documents` | 키워드/semantic/hybrid 검색 |
| `sync_repository` | 레포지토리 동기화 |
| `create_document` | 새 문서 생성 |
| `update_document` | 문서 업데이트 |
| `push_to_remote` | 원격 저장소에 푸시 |

---

## 문제 해결

| 증상 | 원인 | 해결 |
|------|------|------|
| `claude mcp add --header` 실패 | CLI 인수 파싱 버그 | `claude mcp add-json` 방식 사용 |
| `claude mcp list`에 docst 미표시 | JSON 구문 오류 | `~/.claude.json` 유효성 검사 |
| 도구 호출 시 401 오류 | API Key 무효 또는 만료 | 새 API Key 생성 후 재등록 |
| 연결 타임아웃 | 백엔드 미실행 | `docker-compose up -d`로 백엔드 시작 |
| SSE 연결 즉시 끊김 | 엔드포인트 경로 오류 | URL이 `http://localhost:8342/sse`인지 확인 |
| Claude Desktop 빨간색 표시 | 설정 오류 또는 백엔드 미실행 | JSON 확인 후 Claude Desktop 재시작 |
