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

### 상대 경로 사용 권장 (크로스 플랫폼)

Claude Code는 hook 실행 시 **작업 디렉토리를 프로젝트 루트로 설정**합니다.
따라서 상대 경로가 가장 안정적입니다:

```json
// ✅ 권장 (크로스 플랫폼에서 안정적)
"command": "node .claude/hooks/script.js"
```

### `$CLAUDE_PROJECT_DIR` 사용 시 주의

`$CLAUDE_PROJECT_DIR` 환경 변수는 **Windows에서 불안정**합니다:

| 플랫폼 | `$CLAUDE_PROJECT_DIR` | 상대 경로 |
|--------|----------------------|-----------|
| macOS/Linux | ✅ 동작 | ✅ 동작 |
| Windows | ❌ 불안정 (셸에 따라 다름) | ✅ 동작 |

```json
// ❌ Windows에서 불안정
"command": "node \"$CLAUDE_PROJECT_DIR\"/.claude/hooks/script.js"

// ✅ 모든 플랫폼에서 안정적
"command": "node .claude/hooks/script.js"
```

> 참고: [GitHub Issue #5049](https://github.com/anthropics/claude-code/issues/5049) - Windows 셸 호환성 문제

## 출력 위치
- 글로벌: ~/.claude/settings.json
- 프로젝트: .claude/settings.json
- 스크립트: .claude/hooks/

## 템플릿
@resources/templates.md 참조