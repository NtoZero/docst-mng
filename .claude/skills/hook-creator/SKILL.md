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

## 출력 위치
- 글로벌: ~/.claude/settings.json
- 프로젝트: .claude/settings.json
- 스크립트: .claude/hooks/

## 템플릿
@resources/templates.md 참조