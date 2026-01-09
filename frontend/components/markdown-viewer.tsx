'use client';

import React from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import remarkMath from 'remark-math';
import rehypeHighlight from 'rehype-highlight';
import rehypeKatex from 'rehype-katex';
import rehypeSlug from 'rehype-slug';
import matter from 'gray-matter';
import { Link } from 'lucide-react';
import { cn } from '@/lib/utils';
import { FrontmatterCard, CodeBlock, MermaidDiagram } from './markdown';

interface MarkdownViewerProps {
  content: string;
  className?: string;
  showFrontmatter?: boolean;
}

export function MarkdownViewer({
  content,
  className,
  showFrontmatter = true,
}: MarkdownViewerProps) {
  // Parse frontmatter
  const { data: frontmatter, content: markdownContent } = React.useMemo(() => {
    try {
      return matter(content);
    } catch {
      return { data: {}, content };
    }
  }, [content]);

  return (
    <div className={cn('markdown-viewer', className)}>
      {/* Frontmatter Card */}
      {showFrontmatter && Object.keys(frontmatter).length > 0 && (
        <FrontmatterCard data={frontmatter} />
      )}

      {/* Markdown Content */}
      <div className="prose prose-slate dark:prose-invert max-w-none">
        <ReactMarkdown
          remarkPlugins={[remarkGfm, remarkMath]}
          rehypePlugins={[
            rehypeSlug,
            rehypeHighlight,
            [rehypeKatex, { strict: false }],
          ]}
          components={{
            h1: ({ children, id }) => (
              <h1 id={id} className="group border-b pb-2 text-3xl font-bold scroll-mt-4">
                {id && (
                  <a href={`#${id}`} className="mr-2 opacity-0 group-hover:opacity-100 transition-opacity">
                    <Link className="inline h-5 w-5 text-muted-foreground" />
                  </a>
                )}
                {children}
              </h1>
            ),
            h2: ({ children, id }) => (
              <h2 id={id} className="group border-b pb-1 text-2xl font-semibold scroll-mt-4">
                {id && (
                  <a href={`#${id}`} className="mr-2 opacity-0 group-hover:opacity-100 transition-opacity">
                    <Link className="inline h-4 w-4 text-muted-foreground" />
                  </a>
                )}
                {children}
              </h2>
            ),
            h3: ({ children, id }) => (
              <h3 id={id} className="group text-xl font-semibold scroll-mt-4">
                {id && (
                  <a href={`#${id}`} className="mr-2 opacity-0 group-hover:opacity-100 transition-opacity">
                    <Link className="inline h-4 w-4 text-muted-foreground" />
                  </a>
                )}
                {children}
              </h3>
            ),
            h4: ({ children, id }) => (
              <h4 id={id} className="group text-lg font-semibold scroll-mt-4">
                {id && (
                  <a href={`#${id}`} className="mr-2 opacity-0 group-hover:opacity-100 transition-opacity">
                    <Link className="inline h-3.5 w-3.5 text-muted-foreground" />
                  </a>
                )}
                {children}
              </h4>
            ),
            code: ({ className, children, ...props }) => {
              const isInline = !className;
              return (
                <CodeBlock className={className} inline={isInline} {...props}>
                  {children}
                </CodeBlock>
              );
            },
            pre: ({ children, ...props }) => {
              // Check for mermaid diagrams
              const childElement = React.Children.toArray(children)[0];
              if (React.isValidElement(childElement)) {
                const childProps = childElement.props as {
                  className?: string;
                  children?: React.ReactNode;
                };
                const childClassName = childProps?.className || '';
                if (childClassName.includes('language-mermaid')) {
                  const code = childProps?.children;
                  return <MermaidDiagram chart={String(code)} />;
                }
              }
              return (
                <pre className="overflow-x-auto" {...props}>
                  {children}
                </pre>
              );
            },
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
              <th className="border bg-muted px-4 py-2 text-left font-semibold">
                {children}
              </th>
            ),
            td: ({ children }) => (
              <td className="border px-4 py-2">{children}</td>
            ),
            blockquote: ({ children }) => (
              <blockquote className="border-l-4 border-primary pl-4 italic">
                {children}
              </blockquote>
            ),
            ul: ({ children }) => <ul className="list-disc pl-6">{children}</ul>,
            ol: ({ children }) => <ol className="list-decimal pl-6">{children}</ol>,
          }}
        >
          {markdownContent}
        </ReactMarkdown>
      </div>
    </div>
  );
}
