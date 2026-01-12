# Hook 템플릿 모음

> **권장**: 상대 경로 사용 (`node .claude/hooks/...`) - 크로스 플랫폼에서 안정적

## PostToolUse - 로깅
```json
{
  "hooks": {
    "PostToolUse": [
      {
        "matcher": "Skill",
        "hooks": [{
          "type": "command",
          "command": "node .claude/hooks/log.js",
          "timeout": 30
        }]
      }
    ]
  }
}
```

## PreToolUse - 차단
```json
{
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Bash",
        "hooks": [{
          "type": "command",
          "command": "node .claude/hooks/block-dangerous.js",
          "timeout": 30
        }]
      }
    ]
  }
}
```

## Node.js 스크립트 기본 구조

```javascript
const fs = require('fs');
const path = require('path');

// stdin에서 hook 입력 읽기
const input = JSON.parse(fs.readFileSync(0, 'utf-8'));

// ✅ 크로스 플랫폼 경로 처리 (path.join 필수)
const projectDir = process.env.CLAUDE_PROJECT_DIR || process.cwd();
const logDir = path.join(projectDir, '.claude', 'logs');

// ❌ 위험: 문자열 연결 (Windows에서 문제 발생 가능)
// const logDir = projectDir + '/.claude/logs';

// 디렉토리 생성 (없으면)
if (!fs.existsSync(logDir)) {
  fs.mkdirSync(logDir, { recursive: true });
}

// 처리 로직
```

### 경로 처리 규칙

| 상황 | 올바른 방법 | 잘못된 방법 |
|------|------------|------------|
| 경로 결합 | `path.join(a, b, c)` | `a + '/' + b + '/' + c` |
| 홈 디렉토리 | `process.env.HOME \|\| process.env.USERPROFILE` | `process.env.HOME` |
| 프로젝트 루트 | `process.env.CLAUDE_PROJECT_DIR \|\| process.cwd()` | `process.env.CLAUDE_PROJECT_DIR` |