# Skill Template

아래 템플릿을 복사하여 새 스킬을 생성하세요.

## 기본 템플릿

```markdown
---
name: skill-name
description: |
  스킬 설명을 작성합니다.
  Use when user mentions: 키워드1, 키워드2, 키워드3.
  Examples: "예제 요청1", "예제 요청2"
allowed-tools: Read, Write, Edit, Glob, Grep
user-invocable: true
---

# Skill Name

스킬의 목적과 기능을 설명합니다.

## 워크플로우

이 스킬이 호출되면:

1. 첫 번째 단계 설명
2. 두 번째 단계 설명
3. 결과 출력

## 규칙

- 규칙 1: 설명
- 규칙 2: 설명
- 규칙 3: 설명

## 출력 형식

결과물의 형식을 정의합니다.

## 참고

추가 참고 자료가 있다면 링크합니다.
```

## 고급 템플릿 (Progressive Disclosure)

```markdown
---
name: advanced-skill
description: |
  고급 스킬 설명.
  Use when user mentions: 고급 키워드1, 고급 키워드2.
  Examples: "고급 예제1", "고급 예제2"
allowed-tools: Read, Write, Edit, Glob, Grep, Bash
model: inherit
user-invocable: true
---

# Advanced Skill

## Quick Start

핵심 기능만 간략히 설명합니다.

## 상세 가이드

자세한 내용은 별도 파일을 참조:

- 템플릿: [assets/template.md](assets/template.md)
- 예제: [references/examples.md](references/examples.md)
- API 참조: [references/api.md](references/api.md)

## 워크플로우

1. 요구사항 수집
2. 검증
3. 실행
4. 결과 확인

## 규칙

### 필수 규칙
- 규칙 A

### 권장 규칙
- 규칙 B
```

## Fork 컨텍스트 템플릿

독립된 서브에이전트에서 실행되는 스킬:

```markdown
---
name: forked-skill
description: 독립 컨텍스트에서 실행되는 스킬
allowed-tools: Read, Grep, Glob
context: fork
agent: Explore
user-invocable: true
---

# Forked Skill

이 스킬은 독립된 서브에이전트 컨텍스트에서 실행됩니다.

## 특징

- 메인 대화와 분리된 컨텍스트
- 지정된 에이전트 타입 사용
- 결과만 메인 대화로 반환
```

## 프론트엔드 코드 생성 스킬 템플릿

```markdown
---
name: frontend-generator
description: |
  React/Next.js 컴포넌트를 생성합니다.
  Use when user mentions: 컴포넌트 생성, React 컴포넌트, UI 생성.
allowed-tools: Read, Write, Edit, Glob, Grep
user-invocable: true
---

# Frontend Generator

## 포매터 규칙 (필수)

생성되는 모든 TypeScript/JavaScript 코드는 다음 규칙을 준수:

| 규칙 | 값 |
|------|-----|
| semi | true |
| singleQuote | true |
| tabWidth | 2 |
| trailingComma | es5 |
| printWidth | 100 |
| bracketSpacing | true |
| arrowParens | always |
| endOfLine | lf |

## 코드 예시

```typescript
import React from 'react';

interface ButtonProps {
  label: string;
  onClick: () => void;
}

export const Button = ({ label, onClick }: ButtonProps) => {
  return (
    <button
      className="px-4 py-2 bg-blue-500 text-white rounded"
      onClick={onClick}
    >
      {label}
    </button>
  );
};
```
```