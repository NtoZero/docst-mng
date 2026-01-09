# Phase 7: Document Rendering UI Enhancement

> **작성일**: 2026-01-09
> **전제 조건**: Phase 6 완료 (LLM 통합, Playground)
> **목표**: 문서 렌더링 페이지 고도화 - Frontmatter, Syntax Highlighting, LaTeX, Mermaid, TOC Sidebar 지원

---

## 개요

문서 상세 페이지(`/documents/[id]`)의 Markdown 렌더링을 고도화하여, 기술 문서 작성에 필요한 다양한 포맷을 지원합니다.

### 현재 상태 (Phase 6)
- **MarkdownViewer**: `react-markdown` + `remark-gfm` 기본 렌더링
- **Syntax Highlighting**: 미지원 (`rehype-highlight` 설치되어 있으나 미사용)
- **YAML Frontmatter**: 미지원
- **LaTeX 수식**: 미지원
- **Mermaid 다이어그램**: 미지원

### Phase 7 목표
- **YAML Frontmatter**: 문서 메타데이터 파싱 및 카드 UI 표시
- **Syntax Highlighting**: Java, Python, JS, TypeScript, Dart 등 코드 하이라이팅
- **Copy Button**: 코드 블록 복사 기능
- **LaTeX Rendering**: 인라인/블록 수식 렌더링
- **Mermaid Diagrams**: 플로우차트, 시퀀스 다이어그램 등 시각화
- **TOC Anchor Links**: 헤딩에 앵커 링크 자동 생성, 목차 클릭 시 해당 섹션으로 이동
- **TOC Sidebar**: 노션 스타일의 사이드바 목차, 스크롤 스파이로 현재 섹션 하이라이팅
- **Typography Enhancement**: 개행 간격 개선으로 가독성 향상

---

## 기능 상세

### 1. YAML Frontmatter Support

문서 상단의 YAML 메타데이터를 파싱하여 카드 UI로 표시합니다.

```yaml
---
title: API Documentation
author: John Doe
date: 2024-01-15
tags: [api, rest, guide]
description: REST API 가이드 문서
---
```

**표시 항목**:
- Title, Author, Date
- Description
- Tags (Badge 컴포넌트)
- 커스텀 필드 지원

### 2. Code Block Syntax Highlighting

지원 언어:

| Language | Extension |
|----------|-----------|
| Java | `.java` |
| Python | `.py` |
| JavaScript | `.js` |
| TypeScript | `.ts`, `.tsx` |
| Dart | `.dart` |
| JSON | `.json` |
| YAML | `.yaml`, `.yml` |
| Bash/Shell | `.sh` |
| SQL | `.sql` |
| HTML/CSS | `.html`, `.css` |

**기능**:
- 언어 자동 감지 및 라벨 표시
- Copy-to-clipboard 버튼
- Dark/Light 테마 지원

### 3. LaTeX/Math Rendering

KaTeX 기반 수식 렌더링:

- **인라인 수식**: `$E = mc^2$`
- **블록 수식**:
  ```latex
  $$
  \int_0^\infty e^{-x^2} dx = \frac{\sqrt{\pi}}{2}
  $$
  ```

### 4. Mermaid Diagram Rendering

지원 다이어그램:
- Flowchart
- Sequence Diagram
- Class Diagram
- State Diagram
- ER Diagram
- Gantt Chart

```mermaid
flowchart TD
    A[Start] --> B{Decision}
    B -->|Yes| C[OK]
    B -->|No| D[Retry]
    D --> B
```

### 5. TOC Anchor Links

헤딩(h1~h4)에 자동으로 앵커 링크를 생성하여 목차 네비게이션을 지원합니다.

**기능**:
- `rehype-slug`로 헤딩에 자동 id 생성
- 헤딩 hover 시 링크 아이콘 표시
- 클릭 시 해당 섹션으로 스크롤
- `scroll-mt-4`로 스크롤 시 상단 여백 확보

**예시**:
```markdown
## 설치 방법
```
→ `<h2 id="설치-방법">설치 방법</h2>` + 앵커 링크 아이콘

### 6. TOC Sidebar (Notion Style)

노션 스타일의 사이드바 목차를 제공합니다.

**기능**:
- 마크다운 콘텐츠에서 헤딩 자동 추출
- 헤딩 레벨에 따른 들여쓰기
- Intersection Observer 기반 스크롤 스파이
- 현재 보고 있는 섹션 하이라이팅
- 클릭 시 해당 섹션으로 부드러운 스크롤
- 반응형: xl(1280px) 이상에서만 표시

**레이아웃**:
```
┌─────────────────────────────────┬──────────────┐
│                                 │   목차        │
│         문서 본문                │   ├ 개요     │
│                                 │   ├ 설치     │
│                                 │   │ └ 요구사항│
│                                 │   ├ 사용법   │ ← 현재 섹션
│                                 │   └ 참고     │
└─────────────────────────────────┴──────────────┘
```

### 7. Typography Enhancement

문서 가독성을 위한 타이포그래피 개선:

| 요소 | 설정 |
|------|------|
| 기본 줄 간격 | `line-height: 1.8` |
| 문단 간격 | `margin-bottom: 1.25em` |
| 헤딩 상단 간격 | `margin-top: 1.5em` |
| 헤딩 하단 간격 | `margin-bottom: 0.75em` |
| 리스트 간격 | `margin-bottom: 1.25em` |
| 리스트 아이템 간격 | `margin-bottom: 0.5em` |
| 코드 블록 간격 | `margin: 1.25em 0` |

---

## 아키텍처

### 컴포넌트 구조

```
frontend/components/
├── markdown-viewer.tsx              # 메인 컴포넌트 (리팩토링)
└── markdown/                         # 신규 서브 컴포넌트
    ├── index.ts                      # Barrel export
    ├── frontmatter-card.tsx          # YAML 메타데이터 카드
    ├── code-block.tsx                # Syntax Highlighting + Copy
    ├── mermaid-diagram.tsx           # Mermaid 다이어그램 (Lazy Load)
    └── table-of-contents.tsx         # TOC 사이드바 (Scroll Spy)
```

### 플러그인 체인

```
Markdown Content
    │
    ▼
gray-matter (YAML Frontmatter 분리)
    │
    ▼
react-markdown
    │
    ├── remarkPlugins
    │   ├── remark-gfm (GFM 지원)
    │   └── remark-math (수식 파싱)
    │
    └── rehypePlugins
        ├── rehype-slug (헤딩 id 자동 생성)
        ├── rehype-highlight (Syntax Highlighting)
        └── rehype-katex (수식 렌더링)
    │
    ▼
Custom Components
    ├── FrontmatterCard
    ├── CodeBlock (+ Copy Button)
    └── MermaidDiagram (Lazy Loaded)
```

---

## 의존성

### 신규 설치

```bash
npm install gray-matter remark-math rehype-katex katex mermaid rehype-slug
npm install -D @types/katex
```

| Package | Version | Purpose |
|---------|---------|---------|
| `gray-matter` | ^4.0.3 | YAML frontmatter 파싱 |
| `remark-math` | ^6.0.0 | 수식 구문 파싱 |
| `rehype-katex` | ^7.0.1 | KaTeX 렌더링 |
| `rehype-slug` | ^6.0.0 | 헤딩에 id 자동 생성 (TOC) |
| `katex` | ^0.16.x | 수식 렌더링 엔진 |
| `mermaid` | ^11.x | 다이어그램 렌더링 |

### 기존 활용 (미사용 → 활성화)

| Package | Status |
|---------|--------|
| `rehype-highlight@7.0.2` | 설치됨, 활성화 필요 |

---

## 파일 변경 목록

### 수정 파일

| File | Changes |
|------|---------|
| `frontend/package.json` | 의존성 추가 |
| `frontend/components/markdown-viewer.tsx` | 플러그인 통합, 컴포넌트 연동 |
| `frontend/app/globals.css` | highlight.js, KaTeX, Typography 스타일 추가 |
| `frontend/app/[locale]/documents/[docId]/page.tsx` | TOC 사이드바 레이아웃 적용 |

### 신규 파일

| File | Description |
|------|-------------|
| `frontend/components/markdown/index.ts` | Barrel export |
| `frontend/components/markdown/frontmatter-card.tsx` | YAML 메타데이터 카드 |
| `frontend/components/markdown/code-block.tsx` | 코드 블록 + Copy 버튼 |
| `frontend/components/markdown/mermaid-diagram.tsx` | Mermaid 다이어그램 |
| `frontend/components/markdown/table-of-contents.tsx` | TOC 사이드바 (Scroll Spy) |

---

## 기술적 고려사항

### SSR Compatibility
- **Mermaid**: `'use client'` + dynamic import 필수
- **KaTeX**: Server-safe, CSS import만 필요
- **gray-matter**: Node.js 호환, SSR 가능

### Performance
- **Mermaid Lazy Loading**: Dynamic import로 초기 번들에서 제외 (~300KB)
- **컴포넌트 분리**: Tree-shaking 지원

### Dark Mode
- **Syntax Highlighting**: CSS 변수 기반 테마
- **KaTeX**: `dark:` 클래스로 색상 조정
- **Mermaid**: `MutationObserver`로 테마 변경 감지, 다이어그램 재렌더링

### Mobile Responsiveness
- 코드 블록: `overflow-x-auto` 수평 스크롤
- 수식: 긴 수식 `overflow-x-auto` 처리
- 다이어그램: 중앙 정렬, 최대 너비 제한

---

## 세부 계획 문서

- [Frontend 구현 계획](./frontend-plan.md) - 컴포넌트 상세 구현

---

## 검증 계획

### 테스트 케이스

1. **Frontmatter**: YAML 헤더 문서에서 카드 표시 확인
2. **Code Blocks**: Java, Python, JS, Dart 하이라이팅 확인
3. **Copy Button**: 코드 복사 기능 동작 확인
4. **LaTeX**: 인라인/블록 수식 렌더링 확인
5. **Mermaid**: 다양한 다이어그램 타입 렌더링 확인
6. **Dark Mode**: 모든 기능 다크 모드 전환 확인
7. **TOC Anchor Links**: 헤딩 hover 시 링크 아이콘 표시, 클릭 시 스크롤 확인
8. **TOC Sidebar**: 사이드바 목차 표시, 현재 섹션 하이라이팅 확인
9. **Typography**: 개행 간격, 문단 간격이 적절히 표시되는지 확인

### 테스트 문서

```markdown
---
title: Test Document
author: Tester
tags: [test, markdown]
---

# Code Block Test
\`\`\`java
public class Hello {
    public static void main(String[] args) {
        System.out.println("Hello!");
    }
}
\`\`\`

# Math Test
Inline: $E = mc^2$

Block:
$$\int_0^\infty e^{-x^2} dx = \frac{\sqrt{\pi}}{2}$$

# Diagram Test
\`\`\`mermaid
flowchart TD
    A[Start] --> B{Decision}
    B -->|Yes| C[OK]
    B -->|No| D[Retry]
\`\`\`
```

### 실행 방법

```bash
cd frontend
npm install
npm run dev
# http://localhost:3000/ko/documents/{docId} 접속
```

---

## 다음 단계

Phase 7 완료 후:
- **Phase 8**: Multi-tenant 지원, 팀 협업
- **Phase 9**: Advanced RAG (Hybrid Search 고도화, Re-ranking)
- **Phase 10**: 모니터링 & 분석 (사용 패턴, 비용 분석)
