# Hook 템플릿 모음

## PostToolUse - 로깅
```json
{
  "hooks": {
    "PostToolUse": [
      {
        "matcher": "Skill",
        "hooks": [{ "type": "command", "command": "node .claude/hooks/log.js" }]
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
        "hooks": [{ "type": "command", "command": "node .claude/hooks/block-dangerous.js" }]
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