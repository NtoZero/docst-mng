'use client';

import { Loader2 } from 'lucide-react';
import { MarkdownViewer } from '@/components/markdown-viewer';

interface StreamingMessageProps {
  content: string;
  isStreaming: boolean;
}

export function StreamingMessage({
  content,
  isStreaming,
}: StreamingMessageProps) {
  return (
    <div className="relative">
      <MarkdownViewer content={content} className="prose-sm" />
      {isStreaming && (
        <span className="inline-flex ml-1">
          <Loader2 className="h-3 w-3 animate-spin" />
        </span>
      )}
    </div>
  );
}
