# Hook 템플릿 모음

> **주의**: `$CLAUDE_PROJECT_DIR`는 반드시 따옴표로 감싸야 합니다 (`"$CLAUDE_PROJECT_DIR"`)

## PostToolUse - 로깅
```json
{
  "hooks": {
    "PostToolUse": [
      {
        "matcher": "Skill",
        "hooks": [{
          "type": "command",
          "command": "node \"$CLAUDE_PROJECT_DIR\"/.claude/hooks/log.js",
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
          "command": "node \"$CLAUDE_PROJECT_DIR\"/.claude/hooks/block-dangerous.js",
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
const input = JSON.parse(fs.readFileSync(0, 'utf-8'));
// 처리 로직
```