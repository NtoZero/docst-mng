'use client';

import { useTranslations } from 'next-intl';
import { Badge } from '@/components/ui/badge';
import { cn } from '@/lib/utils';

interface SearchModeSelectProps {
  value: 'keyword' | 'semantic' | 'hybrid';
  onChange: (mode: 'keyword' | 'semantic' | 'hybrid') => void;
}

export function SearchModeSelect({ value, onChange }: SearchModeSelectProps) {
  const t = useTranslations('search');

  return (
    <div className="flex gap-1 p-1 bg-muted rounded-lg">
      <button
        onClick={() => onChange('keyword')}
        className={cn(
          "px-3 py-1.5 text-sm rounded-md transition-colors",
          value === 'keyword'
            ? "bg-background shadow text-foreground"
            : "text-muted-foreground hover:text-foreground"
        )}
      >
        {t('modeKeyword')}
      </button>
      <button
        onClick={() => onChange('semantic')}
        className={cn(
          "px-3 py-1.5 text-sm rounded-md transition-colors",
          value === 'semantic'
            ? "bg-background shadow text-foreground"
            : "text-muted-foreground hover:text-foreground"
        )}
      >
        {t('modeSemantic')}
      </button>
      <button
        onClick={() => onChange('hybrid')}
        className={cn(
          "px-3 py-1.5 text-sm rounded-md transition-colors flex items-center gap-1",
          value === 'hybrid'
            ? "bg-background shadow text-foreground"
            : "text-muted-foreground hover:text-foreground"
        )}
      >
        {t('modeHybrid')}
        <Badge variant="secondary" className="text-xs">
          {t('recommended')}
        </Badge>
      </button>
    </div>
  );
}
