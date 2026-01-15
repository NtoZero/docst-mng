# Phase 15-A: 기본 인프라

> **목표**: 가이드 시스템의 타입 정의, 상태 관리, i18n 메시지 구조 구축

---

## 1. 타입 정의

### 파일: `frontend/lib/types/guide.ts`

```typescript
// 가이드 키 (credential type과 매핑)
export type GuideKey =
  | 'github_pat'
  | 'openai_api_key'
  | 'anthropic_api_key'
  | 'neo4j_auth'
  | 'pgvector_auth'
  | 'quick_start';

// Popover에 표시할 간단한 가이드
export interface GuidePopoverContent {
  title: string;
  summary: string;
  quickSteps: string[];
  detailButtonText: string;
}

// 상세 가이드의 단계
export interface GuideStep {
  id: string;
  title: string;
  description: string;
  imageUrl?: string;
  imageAlt?: string;
  permissions?: Array<{ scope: string; description: string }>;
  tip?: string;
  warning?: string;
  code?: string;
}

// 상세 가이드 전체
export interface DetailedGuide {
  id: string;
  title: string;
  description: string;
  steps: GuideStep[];
  externalLinks?: Array<{ text: string; url: string }>;
}

// 온보딩 스텝
export interface OnboardingStep {
  title: string;
  description: string;
  icon?: string;
}
```

---

## 2. 상태 관리 (Zustand)

### 파일: `frontend/lib/store.ts` (추가)

```typescript
// ===== Onboarding State (Phase 15) =====

interface OnboardingState {
  hasSeenOnboarding: boolean;
  completedGuides: string[];
  setOnboardingComplete: (complete: boolean) => void;
  markGuideComplete: (guideKey: string) => void;
  resetOnboarding: () => void;
}

export const useOnboardingStore = create<OnboardingState>()(
  persist(
    (set) => ({
      hasSeenOnboarding: false,
      completedGuides: [],
      setOnboardingComplete: (complete) => set({ hasSeenOnboarding: complete }),
      markGuideComplete: (guideKey) =>
        set((state) => ({
          completedGuides: [...new Set([...state.completedGuides, guideKey])],
        })),
      resetOnboarding: () =>
        set({ hasSeenOnboarding: false, completedGuides: [] }),
    }),
    {
      name: 'docst-onboarding',
    }
  )
);
```

---

## 3. i18n 메시지 구조

### 파일: `frontend/messages/ko.json` (guide 섹션 추가)

```json
{
  "guide": {
    "popover": {
      "github_pat": {
        "title": "GitHub Personal Access Token",
        "summary": "프라이빗 레포지토리에 접근하기 위해 필요합니다.",
        "quickSteps": [
          "GitHub Settings로 이동",
          "Developer settings > Personal access tokens",
          "Generate new token (classic) 클릭"
        ],
        "detailButtonText": "상세 발급 가이드"
      },
      "openai_api_key": {
        "title": "OpenAI API Key",
        "summary": "의미 검색(Semantic Search)을 위한 임베딩 생성에 사용됩니다.",
        "quickSteps": [
          "OpenAI Platform에 로그인",
          "API Keys 메뉴로 이동",
          "Create new secret key 클릭"
        ],
        "detailButtonText": "상세 발급 가이드"
      },
      "anthropic_api_key": {
        "title": "Anthropic API Key",
        "summary": "Claude AI를 사용한 LLM Chat 기능에 필요합니다.",
        "quickSteps": [
          "Anthropic Console에 로그인",
          "Settings > API Keys 이동",
          "Create Key 클릭"
        ],
        "detailButtonText": "상세 발급 가이드"
      }
    },
    "onboarding": {
      "welcome": {
        "title": "Docst에 오신 것을 환영합니다!",
        "description": "분산된 문서를 한 곳에서 관리하고 AI 기반 검색을 경험하세요."
      },
      "step1": {
        "title": "1. 인증 정보 설정",
        "description": "프라이빗 레포지토리에 접근하려면 GitHub PAT를 등록하세요."
      },
      "step2": {
        "title": "2. 프로젝트 생성",
        "description": "문서를 관리할 프로젝트를 만들고 Git 레포지토리를 연결하세요."
      },
      "step3": {
        "title": "3. 문서 동기화",
        "description": "레포지토리를 동기화하면 문서가 자동으로 인덱싱됩니다."
      },
      "skipButton": "나중에 하기",
      "nextButton": "다음",
      "prevButton": "이전",
      "startButton": "시작하기",
      "dontShowAgain": "다시 보지 않기"
    },
    "sheet": {
      "externalResources": "외부 리소스",
      "requiredPermissions": "필요한 권한"
    }
  }
}
```

### 파일: `frontend/messages/en.json` (guide 섹션 추가)

```json
{
  "guide": {
    "popover": {
      "github_pat": {
        "title": "GitHub Personal Access Token",
        "summary": "Required for accessing private repositories.",
        "quickSteps": [
          "Go to GitHub Settings",
          "Developer settings > Personal access tokens",
          "Click Generate new token (classic)"
        ],
        "detailButtonText": "View detailed guide"
      },
      "openai_api_key": {
        "title": "OpenAI API Key",
        "summary": "Used for generating embeddings in semantic search.",
        "quickSteps": [
          "Log in to OpenAI Platform",
          "Navigate to API Keys menu",
          "Click Create new secret key"
        ],
        "detailButtonText": "View detailed guide"
      },
      "anthropic_api_key": {
        "title": "Anthropic API Key",
        "summary": "Required for LLM Chat functionality using Claude AI.",
        "quickSteps": [
          "Log in to Anthropic Console",
          "Go to Settings > API Keys",
          "Click Create Key"
        ],
        "detailButtonText": "View detailed guide"
      }
    },
    "onboarding": {
      "welcome": {
        "title": "Welcome to Docst!",
        "description": "Manage distributed documentation in one place and experience AI-powered search."
      },
      "step1": {
        "title": "1. Set up credentials",
        "description": "Register your GitHub PAT to access private repositories."
      },
      "step2": {
        "title": "2. Create a project",
        "description": "Create a project and connect your Git repositories."
      },
      "step3": {
        "title": "3. Sync documents",
        "description": "Sync repositories to automatically index your documents."
      },
      "skipButton": "Skip for now",
      "nextButton": "Next",
      "prevButton": "Back",
      "startButton": "Get Started",
      "dontShowAgain": "Don't show again"
    },
    "sheet": {
      "externalResources": "External Resources",
      "requiredPermissions": "Required Permissions"
    }
  }
}
```

---

## 4. credential-type-config 수정

### 파일: `frontend/components/credentials/credential-type-config.ts`

```typescript
import type { GuideKey } from '@/lib/types/guide';

export interface CredentialTypeConfig {
  label: string;
  scopes: CredentialScope[];
  secretLabel: string;
  placeholder: string;
  helpUrl?: string;
  guideKey?: GuideKey;  // 신규 추가
  isJsonAuth?: boolean;
  fields?: string[];
}

export const CREDENTIAL_TYPE_CONFIG: Record<CredentialType, CredentialTypeConfig> = {
  GITHUB_PAT: {
    label: 'GitHub PAT',
    scopes: ['USER', 'PROJECT'],
    secretLabel: 'Personal Access Token',
    placeholder: 'ghp_xxxxxxxxxxxxxxxxxxxx',
    helpUrl: 'https://github.com/settings/tokens',
    guideKey: 'github_pat',  // 추가
  },
  OPENAI_API_KEY: {
    label: 'OpenAI API Key',
    scopes: ['SYSTEM', 'PROJECT'],
    secretLabel: 'API Key',
    placeholder: 'sk-proj-...',
    helpUrl: 'https://platform.openai.com/api-keys',
    guideKey: 'openai_api_key',  // 추가
  },
  ANTHROPIC_API_KEY: {
    label: 'Anthropic API Key',
    scopes: ['SYSTEM', 'PROJECT'],
    secretLabel: 'API Key',
    placeholder: 'sk-ant-...',
    helpUrl: 'https://console.anthropic.com/settings/keys',
    guideKey: 'anthropic_api_key',  // 추가
  },
  // ... 기존 타입들 유지
};

// 유틸리티 함수 추가
export function getGuideKey(type: CredentialType): GuideKey | undefined {
  return CREDENTIAL_TYPE_CONFIG[type]?.guideKey;
}
```

---

## 구현 순서

1. `frontend/lib/types/guide.ts` 생성
2. `frontend/lib/store.ts`에 `useOnboardingStore` 추가
3. `frontend/messages/ko.json` guide 섹션 추가
4. `frontend/messages/en.json` guide 섹션 추가
5. `credential-type-config.ts`에 `guideKey` 필드 추가
