# Phase 15: User Guide & Onboarding UI

> **작성일**: 2026-01-15
> **상태**: ✅ Completed
> **전제조건**: Phase 14 완료 (Semantic Search 고도화)
> **목표**: 사용자 가이드 UI 제공 - 튜토리얼, 인라인 도움말, 온보딩 모달

---

## 개요

Docst 사용자가 처음 접했을 때 필요한 설정 정보(GitHub PAT 발급, OpenAI API Key 등)를 쉽게 이해할 수 있도록 인앱 가이드 시스템을 구축합니다.

### 핵심 목표

1. **인라인 도움말**: Credential 입력 필드 옆 `?` 아이콘으로 빠른 안내
2. **상세 가이드**: 단계별 스크린샷과 함께 발급 과정 안내
3. **온보딩 모달**: 첫 로그인 시 주요 기능 소개 및 설정 유도

---

## 제공할 가이드 콘텐츠

| 가이드 | 용도 | 우선순위 |
|--------|------|----------|
| GitHub PAT | 프라이빗 레포지토리 접근 | 높음 |
| OpenAI API Key | 의미 검색(Embedding) | 높음 |
| Anthropic API Key | LLM Chat | 중간 |
| Neo4j Auth | Graph RAG | 낮음 |
| PgVector Auth | Vector DB | 낮음 |

---

## UI 패턴

### 1. HelpPopover (인라인 도움말)

```
┌─────────────────────────────────────────┐
│ [Type: GitHub PAT ▼]  [?] ← 클릭       │
│ ┌─────────────────────────────────────┐ │
│ │ GitHub Personal Access Token        │ │
│ │ 프라이빗 레포지토리 접근에 필요합니다. │ │
│ │                                     │ │
│ │ Quick Steps:                        │ │
│ │ 1. GitHub Settings로 이동           │ │
│ │ 2. Developer settings > PAT         │ │
│ │ 3. Generate new token               │ │
│ │                                     │ │
│ │ [상세 가이드 보기]  [↗ GitHub]       │ │
│ └─────────────────────────────────────┘ │
└─────────────────────────────────────────┘
```

### 2. GuideSheet (상세 가이드)

우측에서 슬라이드로 열리는 단계별 가이드:
- Step 1~N 순차 진행
- 스크린샷/일러스트 포함
- 필요 권한(scope) 명시
- 팁/주의사항 강조

### 3. OnboardingModal (첫 로그인) - Enhanced

실시간 상태 확인 및 네비게이션 기능 포함:

```
┌─────────────────────────────────────────┐
│     1. 인증 정보 설정                    │
│   GitHub PAT를 등록하여 프라이빗          │
│   레포지토리에 접근하세요.                │
│                                         │
│   ┌─────────────────────────────────┐   │
│   │ ✓ 2개 인증정보 등록됨           │   │  ← 실시간 상태 배지
│   └─────────────────────────────────┘   │
│                                         │
│   [ 인증정보 관리 → ]                    │  ← 해당 페이지로 이동
│                                         │
│   Progress: ████░░░░░░ 2/3 완료         │  ← 전체 진행률 표시
│                                         │
│         ● ◉ ○ ○  (Step 2/4)             │  ← 완료된 단계는 녹색
│                                         │
│  [ 나중에 하기 ]    [ ← 이전 ] [ 다음 → ]│
│  [✓] 다시 보지 않기                      │
└─────────────────────────────────────────┘
```

**주요 기능**:
- **실시간 상태 확인**: API 호출로 현재 설정 상태 조회
- **네비게이션 버튼**: 각 단계에서 해당 설정 페이지로 바로 이동
- **상태 배지**: 완료(녹색)/미완료(회색) 상태 표시
- **진행률 바**: 전체 완료 상태 시각화

---

## Phase 구성

| Sub-Phase | 내용 | 파일 |
|-----------|------|------|
| **15-A** | 기본 인프라 (타입, 스토어, i18n) | `phase15-a-infrastructure.md` |
| **15-B** | 가이드 컴포넌트 (HelpPopover, GuideSheet) | `phase15-b-guide-components.md` |
| **15-C** | 온보딩 모달 & 통합 | `phase15-c-onboarding.md` |

---

## 파일 변경 요약

### 신규 생성

| 파일 | 설명 |
|------|------|
| `frontend/components/guide/index.ts` | 배럴 export |
| `frontend/components/guide/help-popover.tsx` | ? 아이콘 + Popover |
| `frontend/components/guide/guide-sheet.tsx` | 상세 가이드 Sheet |
| `frontend/components/guide/guide-step.tsx` | 단계별 스텝 |
| `frontend/components/guide/onboarding-modal.tsx` | 온보딩 모달 |
| `frontend/components/ui/tooltip.tsx` | shadcn/ui Tooltip |
| `frontend/lib/types/guide.ts` | 가이드 타입 정의 |
| `frontend/hooks/use-guide.ts` | 가이드 콘텐츠 로딩 훅 |
| `frontend/messages/guides/en/*.json` | 영문 가이드 콘텐츠 |
| `frontend/messages/guides/ko/*.json` | 한글 가이드 콘텐츠 |

### 수정

| 파일 | 변경 내용 |
|------|----------|
| `frontend/messages/en.json` | `guide` 섹션 추가 |
| `frontend/messages/ko.json` | `guide` 섹션 추가 |
| `frontend/lib/store.ts` | `useOnboardingStore` 추가 |
| `frontend/components/credentials/credential-type-config.ts` | `guideKey` 필드 추가 |
| `frontend/components/credentials/credential-form-dialog.tsx` | HelpPopover 통합 |
| `frontend/app/[locale]/layout.tsx` | OnboardingModal 통합 |

---

## 기술 결정사항

### 1. 콘텐츠 저장 방식

**결정**: JSON 파일 (기존 next-intl 활용)

- 간단한 도움말: `messages/[locale].json`의 `guide.popover` 섹션
- 상세 가이드: `messages/guides/[locale]/[guide-key].json`

**사유**: 기존 다국어 시스템과 일관성 유지, MDX보다 단순

### 2. 온보딩 상태 저장

**결정**: localStorage (Zustand persist)

- 서버 저장 불필요 (단일 디바이스 사용 가정)
- 추후 멀티 디바이스 동기화 필요시 백엔드 확장 가능

### 3. 가이드 이미지

**결정**: `/public/guides/` 정적 파일

- 빌드 시 포함, CDN 캐싱 가능
- 추후 외부 스토리지 이전 용이

---

## 검증 방법

### 1. HelpPopover 동작 확인

1. `/settings/credentials` 이동
2. "Add Credential" 클릭
3. Type 선택 필드 옆 `?` 아이콘 클릭
4. Popover에 Quick Steps 표시 확인
5. "상세 가이드 보기" 클릭 → GuideSheet 열림

### 2. GuideSheet 확인

1. GitHub PAT 가이드 → 단계별 내용 확인
2. 필요 권한(repo, read:user) 표시 확인
3. 외부 링크 동작 확인

### 3. OnboardingModal 확인

1. 새 사용자로 로그인
2. 1초 후 온보딩 모달 자동 표시
3. Step 1→2→3→4 진행
4. "다시 보지 않기" 체크 후 완료
5. 로그아웃 → 재로그인 시 모달 미표시 확인

### 4. 다국어 확인

1. 언어 전환 (ko ↔ en)
2. 모든 가이드 텍스트 번역 확인
