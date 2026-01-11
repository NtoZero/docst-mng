# .claude/skills/hook-creator/SKILL.md
---
name: hook-creator
description: |
Claude Code Hook을 생성합니다.
트리거: "hook 만들어", "훅 생성", "자동화 설정"
allowed-tools:
- Read
- Write
- Edit
---

# Hook Creator

## 생성 절차
1. 어떤 이벤트에 반응할지 확인 (PreToolUse, PostToolUse, Stop 등)
2. 어떤 도구/작업을 대상으로 할지 확인 (matcher)
3. Node.js 스크립트 생성 (.claude/hooks/*.js)
4. settings.json에 Hook 등록

## 규칙
- 항상 Node.js 스크립트 사용 (크로스 플랫폼)
- 경로는 path.join() 사용
- 홈 디렉토리: process.env.HOME || process.env.USERPROFILE

## 주의사항 (중요)

### 경로 설정 시 따옴표 필수
`$CLAUDE_PROJECT_DIR` 환경 변수를 사용할 때 **반드시 따옴표로 감싸야** 합니다:

```json
// ✅ 올바른 방법
"command": "node \"$CLAUDE_PROJECT_DIR\"/.claude/hooks/script.js"

// ❌ 잘못된 방법 (경로에 공백이 있으면 실패)
"command": "node $CLAUDE_PROJECT_DIR/.claude/hooks/script.js"
```

### 상대 경로 사용 금지
Claude Code의 작업 디렉토리(cwd)는 세션 중 변경될 수 있으므로 **상대 경로를 사용하면 안 됩니다**:

```json
// ✅ 올바른 방법
"command": "node \"$CLAUDE_PROJECT_DIR\"/.claude/hooks/script.js"

// ❌ 잘못된 방법 (cwd가 프로젝트 루트가 아닐 수 있음)
"command": "node .claude/hooks/script.js"
```

## 출력 위치
- 글로벌: ~/.claude/settings.json
- 프로젝트: .claude/settings.json
- 스크립트: .claude/hooks/

## 템플릿
@resources/templates.md 참조