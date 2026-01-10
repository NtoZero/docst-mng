'use client';

import { useCallback, useRef, useImperativeHandle, forwardRef } from 'react';
import { Textarea } from '@/components/ui/textarea';
import { cn } from '@/lib/utils';

export interface MarkdownEditorHandle {
  scrollTo: (scrollTop: number) => void;
  getScrollInfo: () => { scrollTop: number; scrollHeight: number; clientHeight: number } | null;
}

interface MarkdownEditorProps {
  value: string;
  onChange: (value: string) => void;
  onScroll?: (scrollRatio: number) => void;
  className?: string;
  placeholder?: string;
}

export const MarkdownEditor = forwardRef<MarkdownEditorHandle, MarkdownEditorProps>(
  function MarkdownEditor(
    {
      value,
      onChange,
      onScroll,
      className,
      placeholder = 'Enter markdown content...',
    },
    ref
  ) {
    const textareaRef = useRef<HTMLTextAreaElement>(null);

    // Expose scroll methods via ref
    useImperativeHandle(ref, () => ({
      scrollTo: (scrollTop: number) => {
        if (textareaRef.current) {
          textareaRef.current.scrollTop = scrollTop;
        }
      },
      getScrollInfo: () => {
        if (!textareaRef.current) return null;
        return {
          scrollTop: textareaRef.current.scrollTop,
          scrollHeight: textareaRef.current.scrollHeight,
          clientHeight: textareaRef.current.clientHeight,
        };
      },
    }));

    const handleChange = useCallback(
      (e: React.ChangeEvent<HTMLTextAreaElement>) => {
        onChange(e.target.value);
      },
      [onChange]
    );

    // Handle scroll event
    const handleScroll = useCallback(
      (e: React.UIEvent<HTMLTextAreaElement>) => {
        if (!onScroll) return;
        const target = e.currentTarget;
        const maxScroll = target.scrollHeight - target.clientHeight;
        const scrollRatio = maxScroll > 0 ? target.scrollTop / maxScroll : 0;
        onScroll(scrollRatio);
      },
      [onScroll]
    );

    // Handle Tab key for indentation
    const handleKeyDown = useCallback(
      (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
        if (e.key === 'Tab') {
          e.preventDefault();
          const textarea = textareaRef.current;
          if (!textarea) return;

          const start = textarea.selectionStart;
          const end = textarea.selectionEnd;
          const newValue = value.substring(0, start) + '  ' + value.substring(end);
          onChange(newValue);

          // Set cursor position after the tab
          requestAnimationFrame(() => {
            textarea.selectionStart = textarea.selectionEnd = start + 2;
          });
        }
      },
      [value, onChange]
    );

    return (
      <Textarea
        ref={textareaRef}
        value={value}
        onChange={handleChange}
        onKeyDown={handleKeyDown}
        onScroll={handleScroll}
        placeholder={placeholder}
        className={cn(
          'min-h-[500px] font-mono text-sm resize-none',
          'focus-visible:ring-1',
          className
        )}
        spellCheck={false}
      />
    );
  }
);
