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

  // Get content from i18n
  let title: string;
  let summary: string;
  let quickSteps: string[];
  let detailButtonText: string;

  try {
    title = t(`${guideKey}.title`);
    summary = t(`${guideKey}.summary`);
    quickSteps = t.raw(`${guideKey}.quickSteps`) as string[];
    detailButtonText = t(`${guideKey}.detailButtonText`);
  } catch {
    // Fallback if translation not found
    return null;
  }

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
