---
name: skill-creator
description: |
  Claude Code 스킬(Skill)을 생성합니다.
  Use when user mentions: 스킬 생성, 스킬 만들어, skill 생성, 커스텀 커맨드, 슬래시 커맨드 생성, /명령어 만들기, SKILL.md 작성, .claude/skills 추가.
  Examples: "PDF 처리 스킬 만들어", "테스트 실행 스킬 생성", "코드 리뷰 스킬", "새로운 슬래시 커맨드"
allowed-tools: Read, Write, Edit, Glob, Grep, Bash
user-invocable: true
---

# Skill Creator

Claude Code용 커스텀 스킬(Custom Skill)을 생성합니다.

## Skill 파일 구조

스킬은 `.claude/skills/` 디렉토리에 저장됩니다:

```
.claude/skills/
└── skill-name/
    ├── SKILL.md              # 필수: 스킬 정의
    ├── resources/            # 선택: 참고 자료
    │   └── templates.md
    ├── assets/               # 선택: 템플릿 자산
    │   └── template-file.md
    └── references/           # 선택: 상세 레퍼런스
        └── examples.md
```

## SKILL.md Frontmatter 필드

### 필수 필드

| 필드 | 타입 | 설명 |
|------|------|------|
| `name` | string | 스킬 이름 (소문자, 하이픈, 최대 64자) |
| `description` | string | 스킬 용도 및 사용 시점 (최대 1024자) |

### 선택 필드

| 필드 | 타입 | 설명 |
|------|------|------|
| `allowed-tools` | string/list | 권한 없이 사용 가능한 도구 목록 |
| `model` | string | 실행 모델 (`sonnet`, `opus`, `haiku`, `inherit`) |
| `context` | string | `fork` 설정 시 독립된 서브에이전트에서 실행 |
| `agent` | string | `context: fork`일 때 사용할 에이전트 타입 |
| `user-invocable` | boolean | 슬래시 메뉴 표시 여부 (기본: `true`) |
| `disable-model-invocation` | boolean | Skill 도구 호출 차단 |
| `hooks` | object | 스킬 라이프사이클 훅 |

## 생성 워크플로우

1. **요구사항 수집**: 스킬의 목적, 사용 시점, 필요 기능 파악
2. **이름 결정**: 소문자, 하이픈 사용 (예: `pdf-processor`)
3. **description 작성**: 자동 발견을 위한 명확한 트리거 키워드 포함
4. **도구 선택**: 필요한 도구만 `allowed-tools`에 지정
5. **본문 작성**: 워크플로우, 규칙, 예제 포함
6. **파일 생성**: `.claude/skills/skill-name/SKILL.md` 작성

## Description 작성 가이드

description은 Claude가 스킬을 자동으로 발견하는 핵심입니다:

```yaml
# 좋은 예 - 구체적 트리거 키워드
description: |
  PDF 파일을 처리합니다.
  Use when user mentions: PDF 추출, PDF 병합, 폼 작성, 문서 변환.
  Examples: "PDF에서 텍스트 추출", "여러 PDF 합치기"

# 나쁜 예 - 너무 모호함
description: 문서를 처리합니다
```

## 도구 선택 가이드

| 작업 유형 | 권장 도구 |
|----------|----------|
| 읽기 전용 | `Read, Grep, Glob, Bash` |
| 파일 수정 | `Read, Write, Edit, Grep, Glob, Bash` |
| 웹 조회 | `WebFetch, WebSearch` |
| 사용자 입력 | `AskUser` |
| 전체 접근 | 필드 생략 (모든 도구 상속) |

Bash 세분화:
```yaml
allowed-tools:
  - Bash(git:*)       # git 명령만
  - Bash(npm run:*)   # npm run만
```

상세 도구 목록: [references/allowed-tools.md](references/allowed-tools.md)

## Progressive Disclosure 패턴

SKILL.md는 500줄 이하로 유지하고, 상세 자료는 분리:

```markdown
## 빠른 시작
[핵심 정보만]

## 상세 가이드
자세한 내용은 [references/detailed-guide.md](references/detailed-guide.md) 참조
```

## 프론트엔드 코드 생성 시 포매터 규칙

스킬이 프론트엔드 코드를 생성할 경우 반드시 준수:

```json
{
  "semi": true,
  "singleQuote": true,
  "tabWidth": 2,
  "trailingComma": "es5",
  "printWidth": 100,
  "bracketSpacing": true,
  "arrowParens": "always",
  "endOfLine": "lf"
}
```

### 적용 예시

```typescript
// 올바른 형식
const handleClick = (event: MouseEvent) => {
  console.log('clicked');
};

const items = [
  'item1',
  'item2',
  'item3',  // trailing comma (es5)
];

// 잘못된 형식
const handleClick = event => {  // arrowParens 누락
  console.log("clicked")  // double quotes, semi 누락
}
```

## 예제 스킬

템플릿: [assets/skill-template.md](assets/skill-template.md)
예제 모음: [references/examples.md](references/examples.md)

## Quick Start

```bash
mkdir -p .claude/skills/my-skill
```

`.claude/skills/my-skill/SKILL.md` 작성:

```markdown
---
name: my-skill
description: |
  내 커스텀 스킬입니다.
  Use when user mentions: 키워드1, 키워드2.
  Examples: "예제 요청1", "예제 요청2"
allowed-tools: Read, Write, Edit, Glob, Grep
user-invocable: true
---

# My Skill

## 워크플로우

1. 첫 번째 단계
2. 두 번째 단계
3. 결과 출력

## 규칙

- 규칙 1
- 규칙 2
```