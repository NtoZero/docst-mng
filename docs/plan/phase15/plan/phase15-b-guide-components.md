# Phase 15-B: 가이드 컴포넌트

> **목표**: HelpPopover, GuideSheet 컴포넌트 구현 및 Credential 폼 통합

---

## 1. HelpPopover 컴포넌트

### 파일: `frontend/components/guide/help-popover.tsx`

```typescript
'use client';

import { useState } from 'react';
import { HelpCircle, ChevronRight, ExternalLink } from 'lucide-react';
import { Button } from '@/components/ui/button';
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover';
import { useTranslations } from 'next-intl';
import type { GuideKey } from '@/lib/types/guide';

interface HelpPopoverProps {
  guideKey: GuideKey;
  showDetailButton?: boolean;
  onDetailClick?: () => void;
  externalUrl?: string;
}

export function HelpPopover({
  guideKey,
  showDetailButton = true,
  onDetailClick,
  externalUrl,
}: HelpPopoverProps) {
  const t = useTranslations('guide.popover');
  const [open, setOpen] = useState(false);

  // i18n에서 콘텐츠 로드
  const title = t(`${guideKey}.title`);
  const summary = t(`${guideKey}.summary`);
  const quickSteps = t.raw(`${guideKey}.quickSteps`) as string[];
  const detailButtonText = t(`${guideKey}.detailButtonText`);

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          type="button"
          variant="ghost"
          size="icon"
          className="h-5 w-5 text-muted-foreground hover:text-foreground"
        >
          <HelpCircle className="h-4 w-4" />
          <span className="sr-only">Help</span>
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-80" align="start">
        <div className="space-y-3">
          <div>
            <h4 className="font-medium text-sm">{title}</h4>
            <p className="text-xs text-muted-foreground mt-1">{summary}</p>
          </div>

          <div className="space-y-1">
            <p className="text-xs font-medium text-muted-foreground">
              Quick Steps:
            </p>
            <ol className="text-xs space-y-0.5 list-decimal list-inside text-muted-foreground">
              {quickSteps.map((step, idx) => (
                <li key={idx}>{step}</li>
              ))}
            </ol>
          </div>

          <div className="flex items-center gap-2 pt-2 border-t">
            {showDetailButton && onDetailClick && (
              <Button
                type="button"
                variant="outline"
                size="sm"
                className="flex-1 text-xs"
                onClick={() => {
                  setOpen(false);
                  onDetailClick();
                }}
              >
                {detailButtonText}
                <ChevronRight className="ml-1 h-3 w-3" />
              </Button>
            )}
            {externalUrl && (
              <Button
                type="button"
                variant="ghost"
                size="icon"
                className="h-8 w-8"
                asChild
              >
                <a
                  href={externalUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  <ExternalLink className="h-3.5 w-3.5" />
                </a>
              </Button>
            )}
          </div>
        </div>
      </PopoverContent>
    </Popover>
  );
}
```

---

## 2. GuideSheet 컴포넌트

### 파일: `frontend/components/guide/guide-sheet.tsx`

```typescript
'use client';

import { useEffect, useState } from 'react';
import { ExternalLink, AlertTriangle, Lightbulb, Copy, Check } from 'lucide-react';
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { useLocale, useTranslations } from 'next-intl';
import type { DetailedGuide, GuideKey } from '@/lib/types/guide';

interface GuideSheetProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  guideKey: GuideKey;
}

export function GuideSheet({ open, onOpenChange, guideKey }: GuideSheetProps) {
  const locale = useLocale();
  const t = useTranslations('guide.sheet');
  const [guide, setGuide] = useState<DetailedGuide | null>(null);
  const [loading, setLoading] = useState(false);
  const [copiedScope, setCopiedScope] = useState<string | null>(null);

  useEffect(() => {
    if (open && guideKey) {
      setLoading(true);
      import(`@/messages/guides/${locale}/${guideKey}.json`)
        .then((module) => setGuide(module.default))
        .catch((err) => {
          console.error('Failed to load guide:', err);
          setGuide(null);
        })
        .finally(() => setLoading(false));
    }
  }, [open, guideKey, locale]);

  const handleCopyScope = (scope: string) => {
    navigator.clipboard.writeText(scope);
    setCopiedScope(scope);
    setTimeout(() => setCopiedScope(null), 2000);
  };

  if (loading) {
    return (
      <Sheet open={open} onOpenChange={onOpenChange}>
        <SheetContent className="w-full sm:max-w-lg">
          <div className="flex items-center justify-center h-full">
            <div className="animate-spin h-8 w-8 border-2 border-primary border-t-transparent rounded-full" />
          </div>
        </SheetContent>
      </Sheet>
    );
  }

  if (!guide) return null;

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent className="w-full sm:max-w-lg">
        <SheetHeader>
          <SheetTitle>{guide.title}</SheetTitle>
          <SheetDescription>{guide.description}</SheetDescription>
        </SheetHeader>

        <ScrollArea className="h-[calc(100vh-12rem)] mt-4">
          <div className="space-y-6 pr-4">
            {guide.steps.map((step, index) => (
              <div key={step.id} className="space-y-3">
                {/* Step header */}
                <div className="flex items-start gap-3">
                  <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-primary text-primary-foreground text-sm font-medium">
                    {index + 1}
                  </div>
                  <div className="space-y-1 flex-1">
                    <h4 className="font-medium leading-none">{step.title}</h4>
                    <p className="text-sm text-muted-foreground">
                      {step.description}
                    </p>
                  </div>
                </div>

                {/* Step image */}
                {step.imageUrl && (
                  <div className="ml-10 rounded-lg border overflow-hidden bg-muted">
                    <img
                      src={step.imageUrl}
                      alt={step.imageAlt || step.title}
                      className="w-full"
                      loading="lazy"
                    />
                  </div>
                )}

                {/* Permissions */}
                {step.permissions && step.permissions.length > 0 && (
                  <div className="ml-10 space-y-2 p-3 rounded-md bg-muted/50">
                    <p className="text-xs font-medium">
                      {t('requiredPermissions')}:
                    </p>
                    <div className="flex flex-wrap gap-2">
                      {step.permissions.map((perm) => (
                        <Badge
                          key={perm.scope}
                          variant="secondary"
                          className="cursor-pointer hover:bg-secondary/80"
                          onClick={() => handleCopyScope(perm.scope)}
                        >
                          <code className="text-xs">{perm.scope}</code>
                          {copiedScope === perm.scope ? (
                            <Check className="ml-1 h-3 w-3" />
                          ) : (
                            <Copy className="ml-1 h-3 w-3" />
                          )}
                        </Badge>
                      ))}
                    </div>
                    <ul className="text-xs text-muted-foreground space-y-1 mt-2">
                      {step.permissions.map((perm) => (
                        <li key={perm.scope}>
                          <code className="bg-muted px-1 rounded">{perm.scope}</code>
                          : {perm.description}
                        </li>
                      ))}
                    </ul>
                  </div>
                )}

                {/* Tip */}
                {step.tip && (
                  <Alert className="ml-10 bg-blue-50 dark:bg-blue-950 border-blue-200 dark:border-blue-800">
                    <Lightbulb className="h-4 w-4 text-blue-600 dark:text-blue-400" />
                    <AlertDescription className="text-blue-700 dark:text-blue-300 text-sm">
                      {step.tip}
                    </AlertDescription>
                  </Alert>
                )}

                {/* Warning */}
                {step.warning && (
                  <Alert className="ml-10 bg-amber-50 dark:bg-amber-950 border-amber-200 dark:border-amber-800">
                    <AlertTriangle className="h-4 w-4 text-amber-600 dark:text-amber-400" />
                    <AlertDescription className="text-amber-700 dark:text-amber-300 text-sm">
                      {step.warning}
                    </AlertDescription>
                  </Alert>
                )}

                {/* Code block */}
                {step.code && (
                  <div className="ml-10 rounded-md bg-zinc-900 p-3">
                    <code className="text-xs text-zinc-100 whitespace-pre-wrap">
                      {step.code}
                    </code>
                  </div>
                )}
              </div>
            ))}
          </div>
        </ScrollArea>

        {/* External links */}
        {guide.externalLinks && guide.externalLinks.length > 0 && (
          <div className="mt-4 pt-4 border-t space-y-2">
            <p className="text-xs font-medium text-muted-foreground">
              {t('externalResources')}:
            </p>
            <div className="flex flex-wrap gap-2">
              {guide.externalLinks.map((link) => (
                <Button key={link.url} variant="outline" size="sm" asChild>
                  <a href={link.url} target="_blank" rel="noopener noreferrer">
                    {link.text}
                    <ExternalLink className="ml-1 h-3 w-3" />
                  </a>
                </Button>
              ))}
            </div>
          </div>
        )}
      </SheetContent>
    </Sheet>
  );
}
```

---

## 3. 배럴 Export

### 파일: `frontend/components/guide/index.ts`

```typescript
export { HelpPopover } from './help-popover';
export { GuideSheet } from './guide-sheet';
export { OnboardingModal } from './onboarding-modal';
```

---

## 4. Credential Form Dialog 수정

### 파일: `frontend/components/credentials/credential-form-dialog.tsx`

**변경 사항**: Type 필드에 HelpPopover 추가

```typescript
// 상단 import 추가
import { HelpPopover, GuideSheet } from '@/components/guide';
import { getGuideKey } from './credential-type-config';

// 컴포넌트 내부에 상태 추가
const [guideSheetOpen, setGuideSheetOpen] = useState(false);
const guideKey = getGuideKey(type);

// Type 필드 수정 (라인 215-249 대체)
<div className="grid gap-2">
  <div className="flex items-center gap-1">
    <Label htmlFor="type">Type *</Label>
    {guideKey && (
      <HelpPopover
        guideKey={guideKey}
        showDetailButton={true}
        onDetailClick={() => setGuideSheetOpen(true)}
        externalUrl={helpUrl}
      />
    )}
  </div>
  <Select
    value={type}
    onValueChange={(value) => setType(value as CredentialType)}
    disabled={isEditMode}
  >
    <SelectTrigger id="type">
      <SelectValue />
    </SelectTrigger>
    <SelectContent>
      {availableTypes.map((t) => (
        <SelectItem key={t} value={t}>
          {getCredentialTypeLabel(t)}
        </SelectItem>
      ))}
    </SelectContent>
  </Select>
  {isEditMode && (
    <p className="text-xs text-muted-foreground">
      Type cannot be changed after creation
    </p>
  )}
</div>

// DialogContent 닫는 태그 직전에 GuideSheet 추가
{guideKey && (
  <GuideSheet
    open={guideSheetOpen}
    onOpenChange={setGuideSheetOpen}
    guideKey={guideKey}
  />
)}
```

---

## 5. 상세 가이드 콘텐츠 (예시)

### 파일: `frontend/messages/guides/ko/github_pat.json`

```json
{
  "id": "github_pat",
  "title": "GitHub Personal Access Token 발급 가이드",
  "description": "GitHub 프라이빗 레포지토리에 접근하기 위한 PAT를 발급받는 방법을 안내합니다.",
  "steps": [
    {
      "id": "step1",
      "title": "GitHub에 로그인",
      "description": "GitHub(github.com)에 로그인한 후, 우측 상단의 프로필 아이콘을 클릭합니다."
    },
    {
      "id": "step2",
      "title": "Settings 이동",
      "description": "드롭다운 메뉴에서 'Settings'를 클릭합니다."
    },
    {
      "id": "step3",
      "title": "Developer settings",
      "description": "좌측 사이드바 맨 아래의 'Developer settings'를 클릭합니다."
    },
    {
      "id": "step4",
      "title": "Personal Access Tokens",
      "description": "'Personal access tokens' > 'Tokens (classic)'을 선택합니다.",
      "tip": "Fine-grained tokens보다 classic tokens이 호환성이 좋습니다."
    },
    {
      "id": "step5",
      "title": "토큰 생성",
      "description": "'Generate new token' 버튼을 클릭하고, 'Generate new token (classic)'을 선택합니다."
    },
    {
      "id": "step6",
      "title": "권한 설정",
      "description": "아래 권한들을 선택하세요:",
      "permissions": [
        { "scope": "repo", "description": "프라이빗 레포지토리 전체 접근 권한" },
        { "scope": "read:user", "description": "사용자 프로필 읽기 권한" }
      ],
      "tip": "만료일은 90일 이상으로 설정하는 것을 권장합니다."
    },
    {
      "id": "step7",
      "title": "토큰 복사",
      "description": "생성된 토큰을 복사하여 안전한 곳에 보관하세요.",
      "warning": "이 페이지를 벗어나면 토큰을 다시 볼 수 없습니다. 반드시 복사해 두세요!"
    }
  ],
  "externalLinks": [
    {
      "text": "GitHub 공식 문서",
      "url": "https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/creating-a-personal-access-token"
    }
  ]
}
```

### 파일: `frontend/messages/guides/ko/openai_api_key.json`

```json
{
  "id": "openai_api_key",
  "title": "OpenAI API Key 발급 가이드",
  "description": "의미 검색(Semantic Search)을 위한 임베딩 생성에 필요한 OpenAI API Key를 발급받는 방법입니다.",
  "steps": [
    {
      "id": "step1",
      "title": "OpenAI Platform 로그인",
      "description": "platform.openai.com에 접속하여 로그인합니다. 계정이 없다면 회원가입을 진행하세요."
    },
    {
      "id": "step2",
      "title": "API Keys 메뉴",
      "description": "좌측 사이드바에서 'API keys' 메뉴를 클릭합니다."
    },
    {
      "id": "step3",
      "title": "새 키 생성",
      "description": "'Create new secret key' 버튼을 클릭합니다."
    },
    {
      "id": "step4",
      "title": "키 이름 설정",
      "description": "키의 용도를 구분할 수 있도록 이름을 입력합니다 (예: 'Docst-Embedding')."
    },
    {
      "id": "step5",
      "title": "키 복사",
      "description": "생성된 API Key를 복사합니다.",
      "warning": "이 키는 다시 볼 수 없습니다. 반드시 안전한 곳에 복사해 두세요!",
      "tip": "API Key는 'sk-proj-'로 시작합니다."
    }
  ],
  "externalLinks": [
    {
      "text": "OpenAI API Keys",
      "url": "https://platform.openai.com/api-keys"
    },
    {
      "text": "사용량 확인",
      "url": "https://platform.openai.com/usage"
    }
  ]
}
```

---

## 구현 순서

1. `frontend/components/guide/help-popover.tsx` 생성
2. `frontend/components/guide/guide-sheet.tsx` 생성
3. `frontend/components/guide/index.ts` 생성
4. `frontend/messages/guides/ko/github_pat.json` 생성
5. `frontend/messages/guides/ko/openai_api_key.json` 생성
6. `frontend/messages/guides/en/github_pat.json` 생성
7. `frontend/messages/guides/en/openai_api_key.json` 생성
8. `frontend/components/credentials/credential-form-dialog.tsx` 수정
