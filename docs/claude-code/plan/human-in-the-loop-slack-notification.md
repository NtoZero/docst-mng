# Human-in-the-Loop Slack Notification Hook 계획

## 개요

Claude Code가 사용자 입력을 기다리는 상황(Human-in-the-Loop)에서 Slack으로 알림을 보내는 Hook을 구현합니다.

## 목적

- Claude Code가 권한 승인, 질문 응답 등 사용자 입력 대기 시 Slack 알림
- 개발자가 다른 작업 중에도 Claude Code 상태를 인지
- 장시간 작업 중 응답 지연 최소화

---

## Hook 이벤트 분석

### 사용 가능한 이벤트

| 이벤트 | 설명 | 적합성 |
|--------|------|--------|
| `Stop` | Claude가 응답 완료 후 대기 상태 진입 | **적합** |
| `PreToolUse` | 도구 실행 전 (권한 요청 시) | 적합 |
| `Notification` | 알림 발생 시 | 적합 |

### Human-in-the-Loop 상황

1. **권한 승인 대기**: 민감한 도구 실행 전 사용자 승인 필요
2. **AskUserQuestion**: Claude가 명시적으로 질문
3. **작업 완료**: Claude가 작업을 마치고 다음 지시 대기
4. **오류 발생**: 작업 중 오류로 사용자 개입 필요

---

## 구현 계획

### 1. Hook 스크립트 생성

**파일**: `.claude/hooks/slack-notify.js`

```javascript
#!/usr/bin/env node
/**
 * Human-in-the-Loop Slack Notification Hook
 * Sends Slack message when Claude is waiting for user input
 */

const fs = require('fs');
const https = require('https');
const path = require('path');

// Configuration
const CONFIG_FILE = path.join(
  process.env.HOME || process.env.USERPROFILE,
  '.claude',
  'slack-config.json'
);

function readConfig() {
  try {
    return JSON.parse(fs.readFileSync(CONFIG_FILE, 'utf-8'));
  } catch {
    return null;
  }
}

function readInput() {
  try {
    return JSON.parse(fs.readFileSync(0, 'utf-8'));
  } catch {
    return null;
  }
}

function sendSlack(webhookUrl, message) {
  return new Promise((resolve, reject) => {
    const url = new URL(webhookUrl);
    const payload = JSON.stringify({ text: message });

    const req = https.request({
      hostname: url.hostname,
      path: url.pathname,
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(payload)
      }
    }, (res) => {
      resolve(res.statusCode);
    });

    req.on('error', reject);
    req.write(payload);
    req.end();
  });
}

async function main() {
  const config = readConfig();
  if (!config?.webhookUrl) {
    // No config, skip silently
    process.exit(0);
  }

  const input = readInput();
  if (!input) {
    process.exit(0);
  }

  const hookEvent = input.hook_event_name;
  const projectDir = process.env.CLAUDE_PROJECT_DIR || 'Unknown Project';
  const projectName = path.basename(projectDir);

  let message = null;

  // Stop event: Claude finished and waiting for input
  if (hookEvent === 'Stop') {
    const stopReason = input.stop_reason || 'unknown';

    // Only notify for specific stop reasons
    if (stopReason === 'end_turn' || stopReason === 'tool_use') {
      message = `:robot_face: *Claude Code* is waiting for your input\n` +
                `> Project: \`${projectName}\`\n` +
                `> Reason: ${stopReason}`;
    }
  }

  // PreToolUse with permission required
  if (hookEvent === 'PreToolUse') {
    const toolName = input.tool_name;
    // Could detect permission-required tools
    // This depends on Claude Code's permission model
  }

  // Notification event
  if (hookEvent === 'Notification') {
    const notificationType = input.notification_type;
    if (notificationType === 'permission_request') {
      message = `:warning: *Claude Code* needs permission\n` +
                `> Project: \`${projectName}\`\n` +
                `> Please approve the pending action`;
    }
  }

  if (message) {
    try {
      await sendSlack(config.webhookUrl, message);
      console.log('[Slack] Notification sent');
    } catch (err) {
      console.error('[Slack] Failed to send:', err.message);
    }
  }

  process.exit(0);
}

main();
```

### 2. 설정 파일 구조

**파일**: `~/.claude/slack-config.json`

```json
{
  "webhookUrl": "<YOUR_SLACK_WEBHOOK_URL>",
  "enabled": true,
  "notifyOn": {
    "stop": true,
    "permission": true,
    "error": true
  },
  "quietHours": {
    "enabled": false,
    "start": "22:00",
    "end": "08:00"
  },
  "cooldown": 60
}
```

### 3. settings.json Hook 등록

```json
{
  "hooks": {
    "Stop": [
      {
        "hooks": [
          {
            "type": "command",
            "command": "node \"$CLAUDE_PROJECT_DIR/.claude/hooks/slack-notify.js\"",
            "timeout": 10
          }
        ]
      }
    ],
    "Notification": [
      {
        "hooks": [
          {
            "type": "command",
            "command": "node \"$CLAUDE_PROJECT_DIR/.claude/hooks/slack-notify.js\"",
            "timeout": 10
          }
        ]
      }
    ]
  }
}
```

---

## Slack Webhook 설정 가이드

### 1. Slack App 생성

1. https://api.slack.com/apps 접속
2. "Create New App" > "From scratch"
3. App 이름: `Claude Code Notifier`
4. Workspace 선택

### 2. Incoming Webhook 활성화

1. "Incoming Webhooks" 메뉴 선택
2. "Activate Incoming Webhooks" 토글 ON
3. "Add New Webhook to Workspace" 클릭
4. 알림 받을 채널 선택
5. Webhook URL 복사

### 3. 설정 파일 생성

```bash
# Windows (PowerShell)
@"
{
  "webhookUrl": "YOUR_WEBHOOK_URL_HERE",
  "enabled": true
}
"@ | Out-File -FilePath "$env:USERPROFILE\.claude\slack-config.json" -Encoding UTF8

# macOS/Linux
cat > ~/.claude/slack-config.json << 'EOF'
{
  "webhookUrl": "YOUR_WEBHOOK_URL_HERE",
  "enabled": true
}
EOF
```

---

## 구현 단계

### Phase 1: 기본 구현

1. [ ] `slack-notify.js` 스크립트 생성
2. [ ] `Stop` 이벤트 Hook 등록
3. [ ] Slack Webhook 연동 테스트

### Phase 2: 기능 확장

1. [ ] `Notification` 이벤트 지원
2. [ ] Quiet Hours (야간 알림 비활성화)
3. [ ] Cooldown (연속 알림 방지)
4. [ ] 프로젝트별 설정 지원

### Phase 3: 고급 기능

1. [ ] 메시지 포맷 커스터마이징
2. [ ] 다중 채널 지원
3. [ ] 작업 컨텍스트 포함 (현재 작업 요약)
4. [ ] 대시보드 연동 (선택)

---

## 고려사항

### 보안

- Webhook URL은 `~/.claude/slack-config.json`에 저장 (gitignore)
- 프로젝트 디렉토리에 민감 정보 저장 금지
- HTTPS 통신만 사용

### 성능

- 비동기 HTTP 요청으로 Claude 응답 지연 최소화
- `timeout: 10` 설정으로 실패 시 빠른 종료
- 에러 발생 시 정상 종료 (Claude 워크플로우 영향 없음)

### 사용자 경험

- 알림 빈도 조절 (Cooldown)
- 야간/업무 외 시간 알림 비활성화
- 프로젝트별 on/off 설정

---

## 예상 결과

### Slack 메시지 예시

```
🤖 Claude Code is waiting for your input
> Project: `docst-mng`
> Reason: end_turn
```

```
⚠️ Claude Code needs permission
> Project: `docst-mng`
> Please approve the pending action
```

---

## 파일 구조

```
.claude/
├── hooks/
│   ├── slack-notify.js      # Slack 알림 스크립트
│   └── ...
├── settings.json             # Hook 등록
└── ...

~/.claude/
└── slack-config.json         # Slack Webhook 설정 (글로벌)
```

---

## 참고 자료

- [Claude Code Hooks 문서](https://docs.anthropic.com/claude-code/hooks)
- [Slack Incoming Webhooks](https://api.slack.com/messaging/webhooks)
- [기존 Hook 로깅 시스템](../hook-logging-system.md)
