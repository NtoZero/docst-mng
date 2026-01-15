# Phase 15-C: 온보딩 모달 & 통합

> **목표**: 첫 로그인 시 표시되는 온보딩 모달 구현 및 레이아웃 통합
> **상태**: ✅ Completed

---

## 1. OnboardingModal 컴포넌트 (Enhanced)

### 주요 기능

| 기능 | 설명 |
|------|------|
| **실시간 상태 확인** | `useCredentials`, `useProjects`, `useStats` 훅으로 현재 설정 상태 조회 |
| **네비게이션 버튼** | 각 단계에서 해당 설정 페이지로 바로 이동 |
| **상태 배지** | 완료(녹색)/미완료(회색) 상태 시각적 표시 |
| **진행률 바** | 전체 완료 상태 (X/3) 시각화 |
| **로딩 상태** | API 조회 중 스피너 표시 |

### 네비게이션 경로

| Step | 페이지 | 용도 |
|------|--------|------|
| Step 1 | `/{locale}/settings/credentials` | 인증정보 등록 |
| Step 2 | `/{locale}/projects/new` | 프로젝트 생성 |
| Step 3 | `/{locale}/projects` | 문서 동기화 |

### 파일: `frontend/components/guide/onboarding-modal.tsx`

```typescript
'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import {
  ChevronRight,
  ChevronLeft,
  KeyRound,
  FolderGit2,
  RefreshCw,
  Sparkles,
  Check,
  ArrowRight,
  Loader2,
} from 'lucide-react';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import { useTranslations, useLocale } from 'next-intl';
import { useOnboardingStore } from '@/lib/store';
import { useCredentials, useProjects, useStats } from '@/hooks/use-api';

interface OnboardingModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

const STEP_ICONS = [Sparkles, KeyRound, FolderGit2, RefreshCw];
const TOTAL_STEPS = 4;

interface StepStatus {
  completed: boolean;
  count?: number;
  loading: boolean;
}

export function OnboardingModal({ open, onOpenChange }: OnboardingModalProps) {
  const t = useTranslations('guide.onboarding');
  const locale = useLocale();
  const router = useRouter();
  const { setOnboardingComplete } = useOnboardingStore();
  const [currentStep, setCurrentStep] = useState(0);
  const [dontShowAgain, setDontShowAgain] = useState(true);

  // Fetch current status for each step
  const { data: credentials, isLoading: credentialsLoading } = useCredentials();
  const { data: projects, isLoading: projectsLoading } = useProjects();
  const { data: stats, isLoading: statsLoading } = useStats();

  // Calculate step statuses
  const stepStatuses: Record<number, StepStatus> = {
    0: { completed: true, loading: false }, // Welcome step
    1: {
      completed: (credentials?.length ?? 0) > 0,
      count: credentials?.length ?? 0,
      loading: credentialsLoading,
    },
    2: {
      completed: (projects?.length ?? 0) > 0,
      count: projects?.length ?? 0,
      loading: projectsLoading,
    },
    3: {
      completed: (stats?.totalDocuments ?? 0) > 0,
      count: stats?.totalDocuments ?? 0,
      loading: statsLoading,
    },
  };

  // Navigation paths for each step
  const stepPaths: Record<number, string> = {
    1: `/${locale}/settings/credentials`,
    2: `/${locale}/projects/new`,
    3: `/${locale}/projects`,
  };

  const handleGoToStep = (stepIndex: number) => {
    const path = stepPaths[stepIndex];
    if (path) {
      onOpenChange(false);
      router.push(path);
    }
  };

  // ... handleComplete, handleSkip, getStatusText, getActionText 함수들 ...

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[520px]">
        {/* Header with icon */}
        <DialogHeader>...</DialogHeader>

        {/* Status indicator for current step */}
        {currentStep > 0 && (
          <div className="flex justify-center py-2">
            {currentStatus.loading ? (
              <div className="flex items-center gap-2">
                <Loader2 className="h-4 w-4 animate-spin" />
                <span>{t('checkingStatus')}</span>
              </div>
            ) : currentStatus.completed ? (
              <Badge variant="default" className="bg-green-600">
                <Check className="mr-1 h-3 w-3" />
                {getStatusText(currentStep, currentStatus)}
              </Badge>
            ) : (
              <Badge variant="secondary">
                {getStatusText(currentStep, currentStatus)}
              </Badge>
            )}
          </div>
        )}

        {/* Action button for current step */}
        {currentStep > 0 && (
          <div className="flex justify-center py-2">
            <Button
              variant={currentStatus.completed ? 'outline' : 'default'}
              onClick={() => handleGoToStep(currentStep)}
            >
              {getActionText(currentStep, currentStatus)}
              <ArrowRight className="ml-2 h-4 w-4" />
            </Button>
          </div>
        )}

        {/* Overall progress */}
        <div className="py-4">
          <div className="flex justify-between items-center mb-2">
            <span>{t('progressLabel')}</span>
            <span>{t('progressComplete', { completed: completedSteps, total: 3 })}</span>
          </div>
          <div className="flex gap-1">
            {[1, 2, 3].map((stepIdx) => (
              <div
                key={stepIdx}
                className={`h-2 flex-1 rounded-full ${
                  stepStatuses[stepIdx].completed
                    ? 'bg-green-500'
                    : stepIdx === currentStep
                    ? 'bg-primary'
                    : 'bg-muted'
                }`}
              />
            ))}
          </div>
        </div>

        {/* Step indicators & Footer */}
        ...
      </DialogContent>
    </Dialog>
  );
}
```

---

## 2. 온보딩 래퍼 컴포넌트

### 파일: `frontend/components/guide/onboarding-wrapper.tsx`

```typescript
'use client';

import { useEffect, useState } from 'react';
import { OnboardingModal } from './onboarding-modal';
import { useOnboardingStore } from '@/lib/store';
import { useAuthHydrated } from '@/lib/store';

export function OnboardingWrapper() {
  const { isHydrated, user } = useAuthHydrated();
  const hasSeenOnboarding = useOnboardingStore((s) => s.hasSeenOnboarding);
  const [showOnboarding, setShowOnboarding] = useState(false);

  useEffect(() => {
    // 조건:
    // 1. Zustand hydration 완료
    // 2. 로그인된 사용자
    // 3. 온보딩 미완료
    if (isHydrated && user && !hasSeenOnboarding) {
      // 페이지 로드 후 약간의 딜레이
      const timer = setTimeout(() => {
        setShowOnboarding(true);
      }, 1000);
      return () => clearTimeout(timer);
    }
  }, [isHydrated, user, hasSeenOnboarding]);

  // Hydration 전에는 렌더링하지 않음
  if (!isHydrated) return null;

  return (
    <OnboardingModal
      open={showOnboarding}
      onOpenChange={setShowOnboarding}
    />
  );
}
```

---

## 3. Layout 통합

### 파일: `frontend/app/[locale]/layout.tsx`

**변경 사항**: OnboardingWrapper 추가

```typescript
// 상단 import 추가
import { OnboardingWrapper } from '@/components/guide/onboarding-wrapper';

// return 문 내부 (Toaster 근처)에 추가
export default function LocaleLayout({ children, params }) {
  // ... 기존 코드

  return (
    <html lang={locale}>
      <body>
        <NextIntlClientProvider messages={messages}>
          <QueryProvider>
            <Header />
            <Sidebar />
            <main className="flex-1 lg:pl-72">
              {children}
            </main>
            <Toaster />
            <OnboardingWrapper />  {/* 추가 */}
          </QueryProvider>
        </NextIntlClientProvider>
      </body>
    </html>
  );
}
```

---

## 4. index.ts 업데이트

### 파일: `frontend/components/guide/index.ts`

```typescript
export { HelpPopover } from './help-popover';
export { GuideSheet } from './guide-sheet';
export { OnboardingModal } from './onboarding-modal';
export { OnboardingWrapper } from './onboarding-wrapper';
```

---

## 5. 빠른 시작 가이드 콘텐츠 (선택)

### 파일: `frontend/messages/guides/ko/quick_start.json`

```json
{
  "id": "quick_start",
  "title": "Docst 빠른 시작 가이드",
  "description": "Docst를 처음 사용하시는 분들을 위한 기본 설정 가이드입니다.",
  "steps": [
    {
      "id": "step1",
      "title": "인증 정보 등록",
      "description": "Settings > Credentials에서 GitHub PAT를 등록합니다. 프라이빗 레포지토리에 접근하기 위해 필요합니다.",
      "tip": "USER 스코프로 등록하면 개인용으로 사용됩니다."
    },
    {
      "id": "step2",
      "title": "프로젝트 생성",
      "description": "Projects > New Project에서 새 프로젝트를 만듭니다. 프로젝트는 문서를 그룹화하는 단위입니다."
    },
    {
      "id": "step3",
      "title": "레포지토리 연결",
      "description": "프로젝트에 Git 레포지토리를 연결합니다. Clone URL을 입력하면 자동으로 레포지토리 정보를 가져옵니다."
    },
    {
      "id": "step4",
      "title": "동기화 실행",
      "description": "Sync 버튼을 클릭하여 레포지토리의 문서를 동기화합니다. Markdown, AsciiDoc 등의 문서가 자동으로 인덱싱됩니다."
    },
    {
      "id": "step5",
      "title": "검색 시작",
      "description": "Search 페이지에서 키워드 검색 또는 의미 검색을 사용해 보세요.",
      "tip": "의미 검색을 사용하려면 OpenAI API Key가 필요합니다. Settings > Credentials에서 등록하세요."
    }
  ],
  "externalLinks": [
    {
      "text": "전체 문서 보기",
      "url": "/docs"
    }
  ]
}
```

---

## 구현 순서

1. `frontend/components/guide/onboarding-modal.tsx` 생성
2. `frontend/components/guide/onboarding-wrapper.tsx` 생성
3. `frontend/components/guide/index.ts` 업데이트
4. `frontend/app/[locale]/layout.tsx` 수정
5. `frontend/messages/guides/ko/quick_start.json` 생성 (선택)
6. `frontend/messages/guides/en/quick_start.json` 생성 (선택)

---

## 테스트 시나리오

### 시나리오 1: 첫 로그인 사용자

1. localStorage에서 `docst-onboarding` 키 삭제 (또는 시크릿 모드)
2. 로그인
3. 1초 후 온보딩 모달 표시 확인
4. **각 단계에서 현재 상태 배지 확인** (예: "0개 인증정보" 또는 "2개 인증정보 등록됨")
5. **네비게이션 버튼 클릭 → 해당 설정 페이지로 이동 확인**
6. Step 1→2→3→4 진행
7. "시작하기" 클릭 후 모달 닫힘
8. `localStorage.getItem('docst-onboarding')` → `hasSeenOnboarding: true`

### 시나리오 2: 상태 변경 후 재확인

1. 온보딩 모달에서 "인증정보 등록하기" 클릭
2. Credentials 페이지에서 GitHub PAT 등록
3. 온보딩 모달 다시 열기 (새로고침 후)
4. **Step 1 상태가 "1개 인증정보 등록됨"으로 변경 확인**
5. **진행률 바가 1/3으로 업데이트 확인**

### 시나리오 3: 로딩 상태 확인

1. 네트워크 속도 제한 (DevTools → Network → Slow 3G)
2. 온보딩 모달 열기
3. **각 단계에서 로딩 스피너 표시 확인**
4. API 응답 후 상태 배지로 전환 확인

### 시나리오 4: 재방문 사용자

1. 온보딩 완료 상태에서 로그아웃
2. 재로그인
3. 온보딩 모달 미표시 확인

### 시나리오 5: "다시 보지 않기" 해제

1. 온보딩 모달에서 "다시 보지 않기" 체크 해제
2. "나중에 하기" 클릭
3. 새로고침 또는 재로그인
4. 온보딩 모달 다시 표시 확인

---

## i18n 메시지 키

### 추가된 키 (`guide.onboarding` 섹션)

| 키 | 설명 | 예시 (ko) |
|----|------|-----------|
| `stepN.statusComplete` | 완료 상태 텍스트 | `"{count}개 인증정보 등록됨"` |
| `stepN.statusIncomplete` | 미완료 상태 텍스트 | `"인증정보 미등록"` |
| `stepN.actionComplete` | 완료 시 버튼 텍스트 | `"인증정보 관리"` |
| `stepN.actionIncomplete` | 미완료 시 버튼 텍스트 | `"인증정보 등록하기"` |
| `checkingStatus` | 로딩 중 텍스트 | `"상태 확인 중..."` |
| `progressLabel` | 진행률 라벨 | `"진행 상태"` |
| `progressComplete` | 진행률 텍스트 | `"{completed}/{total} 완료"` |

---

## 향후 확장 고려사항

1. ~~**CTA 버튼**: 각 스텝에서 관련 페이지로 직접 이동 버튼~~ ✅ 구현 완료
2. ~~**진행률 표시**: 전체 설정 완료 상태 시각화~~ ✅ 구현 완료
3. **진행률 저장**: 온보딩 중간에 닫아도 마지막 스텝부터 재개
4. **애니메이션**: Framer Motion으로 스텝 전환 애니메이션
5. **가이드 완료 배지**: 프로필에서 완료한 가이드 표시
6. **컨텍스트 가이드**: 특정 페이지 방문 시 해당 기능 가이드 제안
