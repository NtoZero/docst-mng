'use client';

import { Code, Columns } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import type { EditorViewMode } from '@/lib/types';

interface EditorViewModeToggleProps {
  mode: EditorViewMode;
  onModeChange: (mode: EditorViewMode) => void;
}

export function EditorViewModeToggle({ mode, onModeChange }: EditorViewModeToggleProps) {
  return (
    <div className="flex items-center gap-1 rounded-md border p-1">
      <Button
        variant="ghost"
        size="sm"
        className={cn('h-8 px-3', mode === 'source' && 'bg-accent')}
        onClick={() => onModeChange('source')}
        title="Source only"
      >
        <Code className="h-4 w-4 mr-2" />
        Source
      </Button>
      <Button
        variant="ghost"
        size="sm"
        className={cn('h-8 px-3', mode === 'split' && 'bg-accent')}
        onClick={() => onModeChange('split')}
        title="Source + Preview"
      >
        <Columns className="h-4 w-4 mr-2" />
        Split
      </Button>
    </div>
  );
}
