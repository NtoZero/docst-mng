# 스킬에서 사용 가능한 도구

`allowed-tools` 필드에 지정할 수 있는 도구 목록입니다.

## Core Tools

| 도구 | 설명 |
|------|------|
| `Read` | 파일 내용 읽기 |
| `Write` | 파일 생성 또는 덮어쓰기 |
| `Edit` | 기존 파일 정밀 편집 |
| `Glob` | 패턴 기반 파일 검색 |
| `Grep` | 정규표현식으로 파일 내용 검색 |
| `Bash` | 셸 명령 실행 |
| `Task` | 서브에이전트 생성 (비권장) |

## Interaction Tools

| 도구 | 설명 |
|------|------|
| `AskUser` | 사용자에게 질문 |
| `TodoWrite` | 작업 목록 관리 |

## Web Tools

| 도구 | 설명 |
|------|------|
| `WebFetch` | 웹 콘텐츠 조회 및 처리 |
| `WebSearch` | 웹 검색 |

## IDE Tools (사용 가능 시)

| 도구 | 설명 |
|------|------|
| `mcp__ide__getDiagnostics` | VS Code 언어 진단 조회 |
| `mcp__ide__executeCode` | Jupyter 커널에서 코드 실행 |

## MCP Tools

MCP 서버에서 제공하는 도구도 사용 가능합니다.
도구명 형식: `mcp__<server>__<tool>`

## 작업별 권장 조합

### 읽기 전용 분석

```yaml
allowed-tools: Read, Grep, Glob, Bash
```

용도: 코드 분석, 문서 검토, 코드베이스 탐색

### 코드 수정

```yaml
allowed-tools: Read, Write, Edit, Grep, Glob, Bash
```

용도: 기능 구현, 버그 수정, 리팩토링

### 최소 권한 (보안 감사)

```yaml
allowed-tools: Read, Grep, Glob
```

용도: 보안 감사, 코드 리뷰 (보고서만)

### 웹 조회 포함

```yaml
allowed-tools: Read, Grep, Glob, WebFetch, WebSearch
```

용도: 문서 참조, API 조회, 최신 정보 확인

### 전체 접근

```yaml
# allowed-tools 필드 생략
```

모든 도구 상속 (주의해서 사용)

## Bash 세분화

특정 명령만 허용:

```yaml
allowed-tools:
  - Bash(git:*)        # git 명령만
  - Bash(npm:*)        # npm 명령만
  - Bash(python:*)     # python 명령만
  - Bash(git add:*)    # git add만
  - Bash(npm run:*)    # npm run만
```

## 도구 선언 형식

### 쉼표 구분

```yaml
allowed-tools: Read, Write, Edit, Glob, Grep
```

### YAML 리스트

```yaml
allowed-tools:
  - Read
  - Write
  - Edit
  - Bash(git:*)
  - Bash(npm:*)
```