'use client';

import { Loader2 } from 'lucide-react';

interface StreamingMessageProps {
  content: string;
  isStreaming: boolean;
}

export function StreamingMessage({
  content,
  isStreaming,
}: StreamingMessageProps) {
  return (
    <div className="whitespace-pre-wrap">
      {content}
      {isStreaming && (
        <span className="inline-flex ml-1">
          <Loader2 className="h-3 w-3 animate-spin" />
        </span>
      )}
    </div>
  );
}
