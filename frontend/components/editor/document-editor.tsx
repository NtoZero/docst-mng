'use client';

import { useRef, useCallback, useState } from 'react';
import { MarkdownEditor } from './markdown-editor';
import { MarkdownViewer } from '@/components/markdown-viewer';
import { cn } from '@/lib/utils';
import type { EditorViewMode } from '@/lib/types';

interface DocumentEditorProps {
  content: string;
  onChange: (content: string) => void;
  viewMode: EditorViewMode;
  className?: string;
}

export function DocumentEditor({
  content,
  onChange,
  viewMode,
  className,
}: DocumentEditorProps) {
  const previewRef = useRef<HTMLDivElement>(null);
  const [isScrollingFromSource, setIsScrollingFromSource] = useState(false);

  // Handle source scroll -> sync to preview
  const handleSourceScroll = useCallback((scrollRatio: number) => {
    if (isScrollingFromSource) return;

    const preview = previewRef.current;
    if (!preview) return;

    setIsScrollingFromSource(true);
    const maxScroll = preview.scrollHeight - preview.clientHeight;
    preview.scrollTop = scrollRatio * maxScroll;

    // Reset flag after scroll animation
    requestAnimationFrame(() => {
      setIsScrollingFromSource(false);
    });
  }, [isScrollingFromSource]);

  if (viewMode === 'source') {
    return (
      <div className={cn('h-full', className)}>
        <MarkdownEditor
          value={content}
          onChange={onChange}
          className="h-full w-full border rounded-md"
        />
      </div>
    );
  }

  // Split view with synchronized scrolling
  return (
    <div className={cn('flex gap-4 h-full', className)}>
      {/* Source Panel */}
      <div className="flex-1 min-w-0 flex flex-col">
        <div className="text-xs font-medium text-muted-foreground mb-2">Source</div>
        <MarkdownEditor
          value={content}
          onChange={onChange}
          onScroll={handleSourceScroll}
          className="flex-1 border rounded-md"
        />
      </div>

      {/* Preview Panel */}
      <div className="flex-1 min-w-0 flex flex-col">
        <div className="text-xs font-medium text-muted-foreground mb-2">Preview</div>
        <div
          ref={previewRef}
          className="flex-1 border rounded-md overflow-auto p-4 bg-background"
        >
          <MarkdownViewer content={content} showFrontmatter={false} />
        </div>
      </div>
    </div>
  );
}
