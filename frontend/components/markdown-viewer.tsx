'use client';

import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { cn } from '@/lib/utils';

interface MarkdownViewerProps {
  content: string;
  className?: string;
}

export function MarkdownViewer({ content, className }: MarkdownViewerProps) {
  return (
    <div className={cn('prose prose-slate dark:prose-invert max-w-none', className)}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          h1: ({ children }) => <h1 className="border-b pb-2 text-3xl font-bold">{children}</h1>,
          h2: ({ children }) => (
            <h2 className="border-b pb-1 text-2xl font-semibold">{children}</h2>
          ),
          h3: ({ children }) => <h3 className="text-xl font-semibold">{children}</h3>,
          code: ({ className, children, ...props }) => {
            const isInline = !className;
            if (isInline) {
              return (
                <code className="rounded bg-muted px-1.5 py-0.5 font-mono text-sm" {...props}>
                  {children}
                </code>
              );
            }
            return (
              <code
                className={cn(
                  'block overflow-x-auto rounded-lg bg-muted p-4 font-mono text-sm',
                  className
                )}
                {...props}
              >
                {children}
              </code>
            );
          },
          pre: ({ children }) => <pre className="overflow-x-auto">{children}</pre>,
          a: ({ href, children }) => (
            <a
              href={href}
              className="text-primary underline hover:no-underline"
              target={href?.startsWith('http') ? '_blank' : undefined}
              rel={href?.startsWith('http') ? 'noopener noreferrer' : undefined}
            >
              {children}
            </a>
          ),
          table: ({ children }) => (
            <div className="overflow-x-auto">
              <table className="w-full border-collapse border">{children}</table>
            </div>
          ),
          th: ({ children }) => (
            <th className="border bg-muted px-4 py-2 text-left font-semibold">{children}</th>
          ),
          td: ({ children }) => <td className="border px-4 py-2">{children}</td>,
          blockquote: ({ children }) => (
            <blockquote className="border-l-4 border-primary pl-4 italic">{children}</blockquote>
          ),
          ul: ({ children }) => <ul className="list-disc pl-6">{children}</ul>,
          ol: ({ children }) => <ol className="list-decimal pl-6">{children}</ol>,
        }}
      >
        {content}
      </ReactMarkdown>
    </div>
  );
}
