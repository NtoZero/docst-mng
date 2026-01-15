# Phase 15-C: 온보딩 모달 & 통합

> **목표**: 첫 로그인 시 표시되는 온보딩 모달 구현 및 레이아웃 통합

---

## 1. OnboardingModal 컴포넌트

### 파일: `frontend/components/guide/onboarding-modal.tsx`

```typescript
'use client';

import { useState } from 'react';
import { ChevronRight, ChevronLeft, KeyRound, FolderGit2, RefreshCw, Sparkles } from 'lucide-react';
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
import { useTranslations } from 'next-intl';
import { useOnboardingStore } from '@/lib/store';

interface OnboardingModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

const STEP_ICONS = [Sparkles, KeyRound, FolderGit2, RefreshCw];
const TOTAL_STEPS = 4;

export function OnboardingModal({ open, onOpenChange }: OnboardingModalProps) {
  const t = useTranslations('guide.onboarding');
  const { setOnboardingComplete } = useOnboardingStore();
  const [currentStep, setCurrentStep] = useState(0);
  const [dontShowAgain, setDontShowAgain] = useState(true); // 기본 체크

  const handleComplete = () => {
    if (dontShowAgain) {
      setOnboardingComplete(true);
    }
    onOpenChange(false);
    setCurrentStep(0);
  };

  const handleSkip = () => {
    if (dontShowAgain) {
      setOnboardingComplete(true);
    }
    onOpenChange(false);
    setCurrentStep(0);
  };

  const stepKeys = ['welcome', 'step1', 'step2', 'step3'];
  const currentStepKey = stepKeys[currentStep];
  const StepIcon = STEP_ICONS[currentStep];

  const isLastStep = currentStep === TOTAL_STEPS - 1;
  const isFirstStep = currentStep === 0;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[480px]">
        <DialogHeader className="text-center pb-4">
          <div className="flex justify-center mb-4">
            <div className="flex h-16 w-16 items-center justify-center rounded-full bg-primary/10">
              <StepIcon className="h-8 w-8 text-primary" />
            </div>
          </div>
          <DialogTitle className="text-xl">
            {t(`${currentStepKey}.title`)}
          </DialogTitle>
          <DialogDescription className="text-base">
            {t(`${currentStepKey}.description`)}
          </DialogDescription>
        </DialogHeader>

        {/* Step indicators */}
        <div className="flex justify-center gap-2 py-4">
          {Array.from({ length: TOTAL_STEPS }).map((_, idx) => (
            <button
              key={idx}
              type="button"
              onClick={() => setCurrentStep(idx)}
              className={`h-2 rounded-full transition-all ${
                idx === currentStep
                  ? 'w-6 bg-primary'
                  : idx < currentStep
                  ? 'w-2 bg-primary/50'
                  : 'w-2 bg-muted'
              }`}
            />
          ))}
        </div>

        <DialogFooter className="flex-col gap-4 sm:flex-col">
          <div className="flex items-center justify-center space-x-2">
            <Checkbox
              id="dontShowAgain"
              checked={dontShowAgain}
              onCheckedChange={(checked) => setDontShowAgain(checked as boolean)}
            />
            <Label htmlFor="dontShowAgain" className="text-sm text-muted-foreground">
              {t('dontShowAgain')}
            </Label>
          </div>

          <div className="flex justify-between w-full">
            <Button variant="ghost" onClick={handleSkip}>
              {t('skipButton')}
            </Button>
            <div className="flex gap-2">
              {!isFirstStep && (
                <Button
                  variant="outline"
                  onClick={() => setCurrentStep((s) => s - 1)}
                >
                  <ChevronLeft className="mr-1 h-4 w-4" />
                  {t('prevButton')}
                </Button>
              )}
              {isLastStep ? (
                <Button onClick={handleComplete}>
                  {t('startButton')}
                </Button>
              ) : (
                <Button onClick={() => setCurrentStep((s) => s + 1)}>
                  {t('nextButton')}
                  <ChevronRight className="ml-1 h-4 w-4" />
                </Button>
              )}
            </div>
          </div>
        </DialogFooter>
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
4. Step 1→2→3→4 진행
5. "시작하기" 클릭 후 모달 닫힘
6. `localStorage.getItem('docst-onboarding')` → `hasSeenOnboarding: true`

### 시나리오 2: 재방문 사용자

1. 온보딩 완료 상태에서 로그아웃
2. 재로그인
3. 온보딩 모달 미표시 확인

### 시나리오 3: "다시 보지 않기" 해제

1. 온보딩 모달에서 "다시 보지 않기" 체크 해제
2. "나중에 하기" 클릭
3. 새로고침 또는 재로그인
4. 온보딩 모달 다시 표시 확인

---

## 향후 확장 고려사항

1. **진행률 저장**: 온보딩 중간에 닫아도 마지막 스텝부터 재개
2. **CTA 버튼**: 각 스텝에서 관련 페이지로 직접 이동 버튼
3. **애니메이션**: Framer Motion으로 스텝 전환 애니메이션
4. **가이드 완료 배지**: 프로필에서 완료한 가이드 표시
5. **컨텍스트 가이드**: 특정 페이지 방문 시 해당 기능 가이드 제안
