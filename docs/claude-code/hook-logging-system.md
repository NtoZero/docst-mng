# Claude Code Hook 로깅 시스템

> Claude Code에서 Skill, Task(Agent), Slash Command 사용 내역을 추적하는 로깅 시스템

## 개요

Claude Code 세션에서 발생하는 주요 이벤트를 JSON Lines 형식으로 기록하여 사용 패턴 분석 및 디버깅에 활용합니다.

## 로깅 대상

| 대상 | 설명 | 예시 |
|-----|------|-----|
| **Skill** | 등록된 스킬 실행 | `/branch-create`, `/test-run` |
| **Task (Agent)** | 서브에이전트 호출 | `test-generator`, `Explore` |
| **Slash Command** | 슬래시 명령어 실행 | `/commit`, `/help` |

## Hook 이벤트 및 감지 방법

### 1. Skill 감지

- **Hook 이벤트**: `PostToolUse`
- **Matcher**: `"Skill"`
- **감지 조건**: `tool_name === "Skill"`

```json
{
  "hook_event_name": "PostToolUse",
  "tool_name": "Skill",
  "tool_input": {
    "skill": "/branch-create",
    "args": "feature my-feature"
  },
  "session_id": "abc123"
}
```

### 2. Task (Agent) 감지

- **Hook 이벤트**: `PostToolUse`
- **Matcher**: `"Task"`
- **감지 조건**: `tool_name === "Task"`

```json
{
  "hook_event_name": "PostToolUse",
  "tool_name": "Task",
  "tool_input": {
    "subagent_type": "test-generator",
    "prompt": "Write unit tests for...",
    "description": "Generate tests"
  },
  "session_id": "abc123"
}
```

### 3. Slash Command 감지

- **Hook 이벤트**: `UserPromptSubmit`
- **감지 조건**: `prompt.startsWith('/')`

```json
{
  "hook_event_name": "UserPromptSubmit",
  "prompt": "/commit -m \"fix bug\"",
  "session_id": "abc123"
}
```

## 시스템 아키텍처

```
┌─────────────────────────────────────────────────────────────┐
│                     로깅 시스템 구조                          │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  [PostToolUse]                    [UserPromptSubmit]        │
│       │                                  │                   │
│       ▼                                  ▼                   │
│  ┌─────────┐                      ┌─────────────┐           │
│  │ Skill   │ matcher: "Skill"    │ /command 감지│           │
│  │ Task    │ matcher: "Task"     │ (prompt 분석)│           │
│  └────┬────┘                      └──────┬──────┘           │
│       │                                  │                   │
│       └──────────────┬───────────────────┘                   │
│                      ▼                                       │
│              ┌──────────────┐                                │
│              │ usage-log.js │ (통합 로깅 스크립트)            │
│              └──────┬───────┘                                │
│                     ▼                                        │
│   ~/.claude/logs/YYYY-MM-DD-usage.jsonl                     │
└─────────────────────────────────────────────────────────────┘
```

## 로그 파일 형식

### 저장 위치

```
~/.claude/logs/
├── 2026-01-11-usage.jsonl
├── 2026-01-12-usage.jsonl
└── ...
```

### JSON Lines 형식

각 라인이 독립적인 JSON 객체로, 스트리밍 처리 및 분석이 용이합니다.

```jsonl
{"timestamp":"2026-01-11T10:30:00.000Z","session_id":"abc123","event":"skill","name":"/branch-create","args":"feature x"}
{"timestamp":"2026-01-11T10:31:00.000Z","session_id":"abc123","event":"task","agent":"test-generator","description":"Generate tests"}
{"timestamp":"2026-01-11T10:32:00.000Z","session_id":"abc123","event":"slash_command","command":"/commit","args":"-m fix"}
```

### 로그 필드 정의

| 필드 | 타입 | 설명 |
|-----|------|------|
| `timestamp` | string (ISO 8601) | 이벤트 발생 시각 |
| `session_id` | string | Claude Code 세션 ID |
| `event` | string | 이벤트 타입: `skill`, `task`, `slash_command` |
| `name` | string | Skill 이름 (event=skill) |
| `agent` | string | Agent 타입 (event=task) |
| `command` | string | 슬래시 명령어 (event=slash_command) |
| `args` | string | 인자/설명 |

## 구현 파일

| 파일 | 용도 |
|-----|-----|
| `.claude/hooks/usage-log.js` | 통합 로깅 스크립트 (Node.js, 크로스 플랫폼) |
| `.claude/settings.json` | Hook 등록 설정 |

## settings.json 설정

```json
{
  "hooks": {
    "PostToolUse": [
      {
        "matcher": "Skill|Task",
        "hooks": [
          {
            "type": "command",
            "command": "node \"$CLAUDE_PROJECT_DIR\"/.claude/hooks/usage-log.js",
            "timeout": 30
          }
        ]
      }
    ],
    "UserPromptSubmit": [
      {
        "hooks": [
          {
            "type": "command",
            "command": "node \"$CLAUDE_PROJECT_DIR\"/.claude/hooks/usage-log.js",
            "timeout": 30
          }
        ]
      }
    ]
  }
}
```

> **주의**: `$CLAUDE_PROJECT_DIR`를 따옴표로 감싸야 합니다 (`"$CLAUDE_PROJECT_DIR"`).
> Windows 경로에 공백이 있을 경우 파싱 오류를 방지합니다.

## 환경 변수

Hook 스크립트에서 사용 가능한 환경 변수:

| 변수 | 설명 |
|-----|------|
| `CLAUDE_PROJECT_DIR` | 프로젝트 루트 경로 (절대경로) |
| `HOME` / `USERPROFILE` | 사용자 홈 디렉토리 |

**주의**: `session_id`는 환경 변수가 아닌 stdin JSON에서 추출해야 합니다.

## stdin JSON 구조

모든 Hook 이벤트에서 stdin으로 전달되는 공통 필드:

```json
{
  "session_id": "abc123",
  "transcript_path": "/path/to/session.jsonl",
  "cwd": "/current/working/dir",
  "permission_mode": "default",
  "hook_event_name": "PostToolUse",
  "tool_name": "Skill",
  "tool_input": { ... },
  "tool_response": { ... }
}
```

## 로그 분석 예시

### jq를 이용한 쿼리

```bash
# 오늘 Skill 사용 내역
jq 'select(.event == "skill")' ~/.claude/logs/2026-01-11-usage.jsonl

# 가장 많이 사용된 Agent
jq -s 'map(select(.event == "task")) | group_by(.agent) | map({agent: .[0].agent, count: length}) | sort_by(-.count)' ~/.claude/logs/*.jsonl

# 세션별 이벤트 수
jq -s 'group_by(.session_id) | map({session: .[0].session_id, count: length})' ~/.claude/logs/2026-01-11-usage.jsonl

# Slash command 통계
jq -s 'map(select(.event == "slash_command")) | group_by(.command) | map({cmd: .[0].command, count: length})' ~/.claude/logs/*.jsonl
```

### PowerShell 분석

```powershell
# 로그 파일 읽기
Get-Content ~/.claude/logs/2026-01-11-usage.jsonl | ForEach-Object { $_ | ConvertFrom-Json } | Where-Object { $_.event -eq "skill" }

# 이벤트 타입별 카운트
Get-Content ~/.claude/logs/*.jsonl | ForEach-Object { $_ | ConvertFrom-Json } | Group-Object event | Select-Object Name, Count
```

## Best Practices

1. **JSON Lines 형식 사용** - 라인 단위 파싱으로 대용량 로그 처리 가능
2. **민감 정보 제외** - prompt 전문, 토큰, 비밀번호 등은 로깅하지 않음
3. **세션 ID 활용** - 대화 흐름 재구성 및 디버깅에 활용
4. **Non-blocking 처리** - 항상 `exit(0)`으로 종료하여 메인 작업 차단 방지
5. **짧은 timeout** - 로깅은 30초 이내로 완료되어야 함
6. **일별 로그 분리** - 파일 크기 관리 및 로테이션 용이

## 참고 자료

- [Claude Code Hooks 공식 문서](https://docs.anthropic.com/en/docs/claude-code/hooks)
- [Hook Creator Skill](.claude/skills/hook-creator/SKILL.md)
