# Claude Code Hook 실행 에러 트러블슈팅

## 현상

Claude Code에서 Hook이 등록되어 있고, Hook 실행이 시도되지만 **항상 에러가 발생**함.

### 에러 메시지
```
Running PostToolUse hooks... (1/2 done)
PostToolUse:Task hook error
PostToolUse:Task hook error
```

또는:
```
UserPromptSubmit hook error
```

### 특징
- Hook이 **실행 시도는 됨** (터미널에 "Running hooks..." 메시지 표시)
- 하지만 **항상 실패**함 ("hook error" 메시지)
- **실제 에러 내용이 표시되지 않음** (stderr 출력이 숨겨짐)
- 로그 파일이 전혀 생성되지 않음

---

## 환경

- OS: Windows 11
- Claude Code 실행 환경: VS Code 터미널 (또는 Windows Terminal)
- Node.js: 설치됨
- Bash: Git Bash / WSL 사용 가능

---

## 추정 원인

### 1. `$CLAUDE_PROJECT_DIR` 환경 변수 미설정 (유력)

Claude Code 문서에는 Hook 실행 시 `$CLAUDE_PROJECT_DIR` 환경 변수가 설정된다고 명시되어 있으나, **실제로 설정되지 않거나 확장되지 않는 것으로 보임**.

**증거**:
- `$CLAUDE_PROJECT_DIR`를 사용한 경로가 작동하지 않음
- 환경 변수가 없으면 경로가 `/.claude/hooks/script.js`가 되어 파일을 찾지 못함

### 2. 작업 디렉토리(cwd) 문제

Hook 실행 시 작업 디렉토리가 **프로젝트 루트가 아닐 수 있음**.

**증거**:
- 상대 경로 `node .claude/hooks/usage-log.js`도 작동하지 않음

### 3. Windows 환경에서의 경로/쉘 호환성 문제

- `$` 환경 변수 문법이 Windows cmd에서 인식되지 않을 수 있음
- 경로 구분자 문제 (`\` vs `/`)

### 4. stderr 출력 숨김

- Claude Code가 Hook의 stderr 출력을 **표시하지 않음**
- 디버깅이 매우 어려움

---

## 시도한 해결 방법

### 시도 1: `$CLAUDE_PROJECT_DIR` 환경 변수 사용

**settings.json**:
```json
{
  "hooks": {
    "PostToolUse": [
      {
        "matcher": "Skill|Task",
        "hooks": [
          {
            "type": "command",
            "command": "node \"$CLAUDE_PROJECT_DIR/.claude/hooks/usage-log.js\"",
            "timeout": 30
          }
        ]
      }
    ]
  }
}
```

**결과**: 실패 - 환경 변수가 확장되지 않거나 설정되지 않음

---

### 시도 2: 상대 경로 사용

**settings.json**:
```json
{
  "command": "node .claude/hooks/usage-log.js"
}
```

**결과**: 실패 - 작업 디렉토리가 프로젝트 루트가 아닌 것으로 추정

---

### 시도 3: 스크립트에서 `process.cwd()` fallback 사용

**usage-log.js**:
```javascript
const projectDir = process.env.CLAUDE_PROJECT_DIR || process.cwd();
```

**결과**: 실패 - 스크립트 자체가 실행되지 않음 (경로를 찾지 못함)

---

### 시도 4: 스크립트에서 `__dirname` 사용

**usage-log.js**:
```javascript
// __dirname = /project/.claude/hooks
const projectDir = path.resolve(__dirname, '..', '..');
const logDir = path.join(projectDir, '.claude', 'logs');
```

**결과**: 실패 - 스크립트 자체가 실행되지 않음

---

### 시도 5: bash를 통한 환경 변수 확장

**settings.json**:
```json
{
  "command": "bash -c \"node \\\"$CLAUDE_PROJECT_DIR/.claude/hooks/usage-log.js\\\"\""
}
```

**결과**: 실패 - 여전히 동일한 에러

---

### 시도 6: 홈 디렉토리에 디버그 로그 작성

**usage-log.js** (스크립트 시작 부분):
```javascript
const homeDir = process.env.HOME || process.env.USERPROFILE;
const debugLogPath = path.join(homeDir, '.claude', 'hook-debug.log');

function writeDebug(msg) {
  fs.appendFileSync(debugLogPath, `[${new Date().toISOString()}] ${msg}\n`);
}

writeDebug('Script started');
```

**결과**: 실패 - 디버그 로그 파일이 생성되지 않음 → **스크립트 자체가 실행되지 않음 확인**

---

## 결론

**스크립트 파일을 찾지 못해서 실행 자체가 되지 않음**.

원인은 다음 중 하나로 추정:
1. `$CLAUDE_PROJECT_DIR` 환경 변수가 Windows에서 확장되지 않음
2. 상대 경로 실행 시 작업 디렉토리가 예상과 다름
3. Claude Code의 Hook 실행 메커니즘이 Windows에서 다르게 작동

---

## 추가 확인 필요 사항

### 1. Claude Code 디버그 모드
```bash
claude --debug
```
또는
```bash
claude --verbose
```

### 2. Hook 실행 환경 확인
Hook에서 실제로 어떤 환경에서 실행되는지 확인:
```json
{
  "command": "bash -c \"echo CWD=$PWD; echo CLAUDE_PROJECT_DIR=$CLAUDE_PROJECT_DIR\" > /tmp/hook-debug.txt"
}
```

### 3. 절대 경로로 테스트 (임시)
```json
{
  "command": "node C:/Dev/project/docst-mng/.claude/hooks/usage-log.js"
}
```

### 4. Claude Code GitHub Issues 확인
- Windows에서의 Hook 관련 알려진 이슈가 있는지 확인
- https://github.com/anthropics/claude-code/issues

---

## 관련 파일

| 파일 | 설명 |
|------|------|
| `.claude/settings.json` | Hook 설정 |
| `.claude/hooks/usage-log.js` | 로깅 스크립트 |
| `.claude/hooks/lint-format.js` | 린트/포맷 스크립트 |

---

---

## 해결 방법 (2026-01-11)

### 원인

`$CLAUDE_PROJECT_DIR` 환경 변수를 **따옴표 없이** 사용하면 Windows에서 경로에 공백이 있을 경우 파싱 오류가 발생합니다.

### 해결책

**환경 변수를 따옴표로 감싸기**:

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
    ]
  }
}
```

**핵심 차이점**:
- ❌ `node $CLAUDE_PROJECT_DIR/.claude/hooks/...` (따옴표 없음)
- ✅ `node "$CLAUDE_PROJECT_DIR"/.claude/hooks/...` (따옴표 있음)

### 검증

Hook 실행 후 로그 파일 확인:
```bash
cat .claude/logs/$(date +%Y-%m-%d)-usage.jsonl | tail -5
```

### 참고

- Claude Code는 Windows에서 Git Bash를 사용하여 명령어 실행
- Git Bash가 `$CLAUDE_PROJECT_DIR` 환경 변수를 확장함
- 따옴표는 공백이 포함된 경로를 올바르게 처리하기 위해 필요

---

## 참고

- [Claude Code Hooks 문서](https://docs.anthropic.com/claude-code/hooks)
- [기존 Hook 로깅 시스템 문서](../hook-logging-system.md)
