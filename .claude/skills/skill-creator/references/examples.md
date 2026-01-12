# 스킬 예제 모음

실제 사용 가능한 스킬 예제입니다.

## 1. 코드 리뷰 스킬

```markdown
---
name: code-review
description: |
  코드 리뷰를 수행합니다.
  Use when user mentions: 코드 리뷰, 리뷰해줘, PR 리뷰, 코드 검토.
  Examples: "이 코드 리뷰해줘", "PR 리뷰 부탁", "코드 품질 검토"
allowed-tools: Read, Grep, Glob, Bash
user-invocable: true
---

# Code Review

## 워크플로우

1. `git diff` 또는 지정된 파일 확인
2. 코드 품질 분석
3. 문제점 우선순위별 보고

## 검토 항목

- 코드 가독성
- 보안 취약점
- 에러 핸들링
- 성능 이슈
- Best practices 준수

## 출력 형식

### Critical
- [파일:라인] 문제 설명

### Warning
- [파일:라인] 개선 제안

### Info
- [파일:라인] 참고 사항
```

## 2. 테스트 생성 스킬

```markdown
---
name: test-generator
description: |
  단위 테스트를 생성합니다.
  Use when user mentions: 테스트 생성, 테스트 작성, 유닛 테스트, 테스트 코드.
  Examples: "이 함수 테스트 만들어", "테스트 코드 생성해줘"
allowed-tools: Read, Write, Edit, Glob, Grep
user-invocable: true
---

# Test Generator

## 워크플로우

1. 대상 코드 분석
2. 테스트 케이스 식별
3. 테스트 파일 생성

## 규칙

- 기존 테스트 프레임워크 사용 (Jest, Vitest 등)
- 경계값 테스트 포함
- 에러 케이스 테스트 포함
- 명확한 테스트 이름 사용
```

## 3. 문서 생성 스킬

```markdown
---
name: doc-generator
description: |
  코드 문서를 생성합니다.
  Use when user mentions: 문서 생성, README 작성, API 문서, JSDoc.
  Examples: "README 만들어줘", "API 문서 생성"
allowed-tools: Read, Write, Edit, Glob, Grep
user-invocable: true
---

# Documentation Generator

## 워크플로우

1. 코드베이스 스캔
2. 구조 분석
3. 문서 생성

## 출력 형식

- README.md: 프로젝트 개요
- API.md: API 레퍼런스
- JSDoc: 인라인 문서
```

## 4. Git 커밋 스킬

```markdown
---
name: git-commit
description: |
  Git 커밋을 생성합니다.
  Use when user mentions: 커밋, commit, 변경사항 저장.
  Examples: "커밋해줘", "변경사항 커밋"
allowed-tools: Bash(git:*)
user-invocable: true
---

# Git Commit

## 워크플로우

1. `git status` 확인
2. `git diff` 분석
3. 커밋 메시지 생성
4. `git commit` 실행

## 커밋 메시지 규칙

```
<type>(<scope>): <subject>

<body>

Co-Authored-By: Claude <noreply@anthropic.com>
```

Types: feat, fix, docs, style, refactor, test, chore
```

## 5. 프론트엔드 컴포넌트 생성 스킬

```markdown
---
name: component-creator
description: |
  React 컴포넌트를 생성합니다.
  Use when user mentions: 컴포넌트 생성, React 컴포넌트, UI 만들기.
  Examples: "버튼 컴포넌트 만들어", "폼 컴포넌트 생성"
allowed-tools: Read, Write, Edit, Glob, Grep
user-invocable: true
---

# Component Creator

## 포매터 규칙 (필수)

| 규칙 | 값 |
|------|-----|
| semi | true |
| singleQuote | true |
| tabWidth | 2 |
| trailingComma | es5 |
| printWidth | 100 |
| arrowParens | always |

## 워크플로우

1. 컴포넌트 요구사항 수집
2. 기존 컴포넌트 패턴 확인
3. 컴포넌트 파일 생성
4. 필요시 스토리북/테스트 생성

## 파일 구조

```
components/
└── ComponentName/
    ├── index.tsx
    ├── ComponentName.tsx
    ├── ComponentName.test.tsx
    └── ComponentName.stories.tsx
```
```

## 6. API 엔드포인트 생성 스킬

```markdown
---
name: api-creator
description: |
  REST API 엔드포인트를 생성합니다.
  Use when user mentions: API 생성, 엔드포인트 추가, REST API.
  Examples: "사용자 API 만들어", "CRUD 엔드포인트 생성"
allowed-tools: Read, Write, Edit, Glob, Grep, Bash
user-invocable: true
---

# API Creator

## 워크플로우

1. 요구사항 분석
2. 기존 API 패턴 확인
3. Controller/Service 생성
4. DTO 정의
5. 테스트 생성

## 규칙

- RESTful 규칙 준수
- 적절한 HTTP 상태 코드 사용
- 에러 핸들링 포함
- 입력 검증 포함
```

## 7. 데이터베이스 마이그레이션 스킬

```markdown
---
name: db-migration
description: |
  데이터베이스 마이그레이션을 생성합니다.
  Use when user mentions: 마이그레이션, 스키마 변경, 테이블 추가.
  Examples: "마이그레이션 생성", "테이블 추가 마이그레이션"
allowed-tools: Read, Write, Edit, Glob, Grep, Bash
user-invocable: true
---

# Database Migration

## 워크플로우

1. 현재 스키마 확인
2. 변경사항 분석
3. 마이그레이션 파일 생성
4. 롤백 스크립트 포함

## 파일명 규칙

```
V{version}__{description}.sql
예: V3__add_user_roles_table.sql
```
```