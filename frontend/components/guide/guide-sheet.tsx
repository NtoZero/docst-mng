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

  const handleCopyScope = async (scope: string) => {
    try {
      await navigator.clipboard.writeText(scope);
      setCopiedScope(scope);
      setTimeout(() => setCopiedScope(null), 2000);
    } catch (err) {
      console.error('Failed to copy:', err);
    }
  };

  if (loading) {
    return (
      <Sheet open={open} onOpenChange={onOpenChange}>
        <SheetContent className="w-full sm:max-w-lg">
          <SheetHeader>
            <SheetTitle className="sr-only">Loading guide...</SheetTitle>
          </SheetHeader>
          <div className="flex items-center justify-center h-full">
            <div className="animate-spin h-8 w-8 border-2 border-primary border-t-transparent rounded-full" />
          </div>
        </SheetContent>
      </Sheet>
    );
  }

  if (!guide) {
    return (
      <Sheet open={open} onOpenChange={onOpenChange}>
        <SheetContent className="w-full sm:max-w-lg">
          <SheetHeader>
            <SheetTitle className="sr-only">Guide</SheetTitle>
          </SheetHeader>
          <div className="flex items-center justify-center h-full text-muted-foreground">
            Guide not available
          </div>
        </SheetContent>
      </Sheet>
    );
  }

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
