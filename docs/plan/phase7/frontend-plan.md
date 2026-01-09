# Phase 7: Frontend Implementation Plan

> **작성일**: 2026-01-09
> **대상**: Document Rendering UI Enhancement

---

## 1. 의존성 설치

```bash
cd frontend
npm install gray-matter remark-math rehype-katex katex mermaid rehype-slug
npm install -D @types/katex
```

---

## 2. FrontmatterCard Component

### 파일: `frontend/components/markdown/frontmatter-card.tsx`

```typescript
'use client';

import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Calendar, User, Tag, FileText } from 'lucide-react';
import { cn } from '@/lib/utils';

interface FrontmatterData {
  title?: string;
  author?: string;
  date?: string;
  tags?: string[];
  description?: string;
  [key: string]: unknown;
}

interface FrontmatterCardProps {
  data: FrontmatterData;
  className?: string;
}

export function FrontmatterCard({ data, className }: FrontmatterCardProps) {
  if (!data || Object.keys(data).length === 0) return null;

  const { title, author, date, tags, description, ...rest } = data;

  return (
    <Card className={cn('mb-6 border-l-4 border-l-primary', className)}>
      <CardHeader className="pb-3">
        {title && (
          <CardTitle className="flex items-center gap-2">
            <FileText className="h-5 w-5" />
            {title}
          </CardTitle>
        )}
        {description && (
          <p className="text-sm text-muted-foreground">{description}</p>
        )}
      </CardHeader>
      <CardContent className="pt-0">
        <div className="flex flex-wrap gap-4 text-sm">
          {author && (
            <div className="flex items-center gap-1.5">
              <User className="h-4 w-4 text-muted-foreground" />
              <span>{author}</span>
            </div>
          )}
          {date && (
            <div className="flex items-center gap-1.5">
              <Calendar className="h-4 w-4 text-muted-foreground" />
              <span>{new Date(date).toLocaleDateString()}</span>
            </div>
          )}
        </div>
        {tags && tags.length > 0 && (
          <div className="mt-3 flex flex-wrap items-center gap-2">
            <Tag className="h-4 w-4 text-muted-foreground" />
            {tags.map((tag) => (
              <Badge key={tag} variant="secondary">
                {tag}
              </Badge>
            ))}
          </div>
        )}
        {Object.keys(rest).length > 0 && (
          <div className="mt-3 grid grid-cols-2 gap-2 text-sm">
            {Object.entries(rest).map(([key, value]) => (
              <div key={key}>
                <span className="font-medium text-muted-foreground">{key}: </span>
                <span>{String(value)}</span>
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
```

---

## 3. CodeBlock Component

### 파일: `frontend/components/markdown/code-block.tsx`

```typescript
'use client';

import { useState, useRef } from 'react';
import { Check, Copy, Code2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';

interface CodeBlockProps {
  children: React.ReactNode;
  className?: string;
  inline?: boolean;
}

const languageLabels: Record<string, string> = {
  javascript: 'JavaScript',
  typescript: 'TypeScript',
  js: 'JavaScript',
  ts: 'TypeScript',
  tsx: 'TypeScript',
  jsx: 'JavaScript',
  java: 'Java',
  python: 'Python',
  py: 'Python',
  dart: 'Dart',
  json: 'JSON',
  yaml: 'YAML',
  yml: 'YAML',
  bash: 'Bash',
  shell: 'Shell',
  sh: 'Shell',
  sql: 'SQL',
  html: 'HTML',
  css: 'CSS',
  markdown: 'Markdown',
  md: 'Markdown',
  xml: 'XML',
  go: 'Go',
  rust: 'Rust',
  kotlin: 'Kotlin',
  swift: 'Swift',
  c: 'C',
  cpp: 'C++',
  csharp: 'C#',
  cs: 'C#',
  php: 'PHP',
  ruby: 'Ruby',
  r: 'R',
  scala: 'Scala',
  latex: 'LaTeX',
  tex: 'LaTeX',
};

export function CodeBlock({ children, className, inline }: CodeBlockProps) {
  const [copied, setCopied] = useState(false);
  const codeRef = useRef<HTMLElement>(null);

  // Extract language from className (e.g., "language-javascript" or "hljs language-javascript")
  const match = /language-(\w+)/.exec(className || '');
  const language = match ? match[1] : '';
  const languageLabel = languageLabels[language] || language.toUpperCase();

  // Handle inline code
  if (inline) {
    return (
      <code className="rounded bg-muted px-1.5 py-0.5 font-mono text-sm">
        {children}
      </code>
    );
  }

  const handleCopy = async () => {
    const text = codeRef.current?.textContent || '';
    await navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="group relative my-4">
      {/* Header: Language badge and copy button */}
      <div className="absolute right-2 top-2 z-10 flex items-center gap-2 opacity-0 transition-opacity group-hover:opacity-100">
        {language && (
          <span className="flex items-center gap-1 rounded bg-background/80 px-2 py-1 text-xs font-medium backdrop-blur-sm">
            <Code2 className="h-3 w-3" />
            {languageLabel}
          </span>
        )}
        <Button
          variant="ghost"
          size="icon"
          className="h-7 w-7 bg-background/80 backdrop-blur-sm"
          onClick={handleCopy}
        >
          {copied ? (
            <Check className="h-3.5 w-3.5 text-green-500" />
          ) : (
            <Copy className="h-3.5 w-3.5" />
          )}
        </Button>
      </div>

      {/* Code block */}
      <code
        ref={codeRef}
        className={cn(
          'block overflow-x-auto rounded-lg bg-muted p-4 pr-24 font-mono text-sm',
          className
        )}
      >
        {children}
      </code>
    </div>
  );
}
```

---

## 4. MermaidDiagram Component

### 파일: `frontend/components/markdown/mermaid-diagram.tsx`

```typescript
'use client';

import { useEffect, useRef, useState, useId } from 'react';
import { cn } from '@/lib/utils';
import { Loader2, AlertCircle } from 'lucide-react';

interface MermaidDiagramProps {
  chart: string;
  className?: string;
}

export function MermaidDiagram({ chart, className }: MermaidDiagramProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [svg, setSvg] = useState<string>('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const uniqueId = useId().replace(/:/g, '-');

  useEffect(() => {
    let mounted = true;

    const renderDiagram = async () => {
      try {
        setLoading(true);
        setError(null);

        // Dynamic import for code splitting
        const mermaid = (await import('mermaid')).default;

        // Initialize with theme based on dark mode
        const isDark = document.documentElement.classList.contains('dark');
        mermaid.initialize({
          startOnLoad: false,
          theme: isDark ? 'dark' : 'default',
          securityLevel: 'strict',
          fontFamily: 'inherit',
        });

        const { svg: renderedSvg } = await mermaid.render(
          `mermaid-${uniqueId}`,
          chart.trim()
        );

        if (mounted) {
          setSvg(renderedSvg);
          setLoading(false);
        }
      } catch (err) {
        if (mounted) {
          setError(err instanceof Error ? err.message : 'Failed to render diagram');
          setLoading(false);
        }
      }
    };

    renderDiagram();

    return () => {
      mounted = false;
    };
  }, [chart, uniqueId]);

  // Re-render on theme change
  useEffect(() => {
    const observer = new MutationObserver((mutations) => {
      mutations.forEach((mutation) => {
        if (mutation.attributeName === 'class') {
          setLoading(true);
          setSvg('');
        }
      });
    });

    observer.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ['class'],
    });

    return () => observer.disconnect();
  }, []);

  if (loading) {
    return (
      <div className={cn('flex items-center justify-center py-8', className)}>
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
        <span className="ml-2 text-sm text-muted-foreground">Loading diagram...</span>
      </div>
    );
  }

  if (error) {
    return (
      <div className={cn('rounded-lg border border-destructive/50 bg-destructive/10 p-4', className)}>
        <div className="flex items-center gap-2 text-destructive">
          <AlertCircle className="h-4 w-4" />
          <span className="font-medium">Diagram Error</span>
        </div>
        <pre className="mt-2 text-xs text-muted-foreground whitespace-pre-wrap">{error}</pre>
        <details className="mt-2">
          <summary className="cursor-pointer text-xs text-muted-foreground hover:underline">
            View Source
          </summary>
          <pre className="mt-1 overflow-x-auto rounded bg-muted p-2 text-xs">{chart}</pre>
        </details>
      </div>
    );
  }

  return (
    <div
      ref={containerRef}
      className={cn(
        'my-4 flex justify-center overflow-x-auto rounded-lg bg-muted/50 p-4',
        '[&_svg]:max-w-full',
        className
      )}
      dangerouslySetInnerHTML={{ __html: svg }}
    />
  );
}
```

---

## 5. TableOfContents Component (TOC Sidebar)

### 파일: `frontend/components/markdown/table-of-contents.tsx`

```typescript
'use client';

import { useEffect, useState, useCallback } from 'react';
import { cn } from '@/lib/utils';
import { List } from 'lucide-react';

export interface TocItem {
  id: string;
  text: string;
  level: number;
}

interface TableOfContentsProps {
  content: string;
  className?: string;
}

// Extract headings from markdown content
function extractHeadings(content: string): TocItem[] {
  const headings: TocItem[] = [];
  const lines = content.split('\n');

  for (const line of lines) {
    const match = line.match(/^(#{1,6})\s+(.+)$/);
    if (match) {
      const level = match[1].length;
      const text = match[2].trim();
      const id = text
        .toLowerCase()
        .replace(/[^a-z0-9가-힣\s-]/g, '')
        .replace(/\s+/g, '-')
        .replace(/-+/g, '-')
        .replace(/^-|-$/g, '');

      if (id) {
        headings.push({ id, text, level });
      }
    }
  }
  return headings;
}

export function TableOfContents({ content, className }: TableOfContentsProps) {
  const [headings, setHeadings] = useState<TocItem[]>([]);
  const [activeId, setActiveId] = useState<string>('');

  useEffect(() => {
    setHeadings(extractHeadings(content));
  }, [content]);

  // Scroll spy with Intersection Observer
  useEffect(() => {
    if (headings.length === 0) return;

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            setActiveId(entry.target.id);
          }
        });
      },
      { rootMargin: '-80px 0px -80% 0px', threshold: 0 }
    );

    headings.forEach(({ id }) => {
      const element = document.getElementById(id);
      if (element) observer.observe(element);
    });

    return () => observer.disconnect();
  }, [headings]);

  const handleClick = useCallback((id: string) => {
    const element = document.getElementById(id);
    if (element) {
      element.scrollIntoView({ behavior: 'smooth', block: 'start' });
      setActiveId(id);
    }
  }, []);

  if (headings.length === 0) return null;

  const minLevel = Math.min(...headings.map((h) => h.level));

  return (
    <nav className={cn('space-y-1', className)}>
      <div className="flex items-center gap-2 pb-2 text-sm font-medium text-muted-foreground">
        <List className="h-4 w-4" />
        <span>목차</span>
      </div>
      <ul className="space-y-1 text-sm">
        {headings.map((heading) => {
          const indent = (heading.level - minLevel) * 12;
          const isActive = activeId === heading.id;

          return (
            <li key={heading.id}>
              <button
                onClick={() => handleClick(heading.id)}
                className={cn(
                  'block w-full truncate rounded-md px-2 py-1.5 text-left transition-colors',
                  'hover:bg-muted hover:text-foreground',
                  isActive ? 'bg-primary/10 font-medium text-primary' : 'text-muted-foreground'
                )}
                style={{ paddingLeft: `${indent + 8}px` }}
                title={heading.text}
              >
                {heading.text}
              </button>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
```

---

## 6. Barrel Export

### 파일: `frontend/components/markdown/index.ts`

```typescript
export { FrontmatterCard } from './frontmatter-card';
export { CodeBlock } from './code-block';
export { MermaidDiagram } from './mermaid-diagram';
export { TableOfContents } from './table-of-contents';
export type { TocItem } from './table-of-contents';
```

---

## 7. MarkdownViewer Refactoring

### 파일: `frontend/components/markdown-viewer.tsx`

```typescript
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
```

---

## 7. CSS Styles

### 파일: `frontend/app/globals.css` (추가)

```css
/* ============================================
   KaTeX Styles Import
   ============================================ */
@import 'katex/dist/katex.min.css';

/* ============================================
   Syntax Highlighting (highlight.js - GitHub Theme)
   ============================================ */
.hljs {
  color: #24292e;
  background: hsl(var(--muted));
  border-radius: 0.5rem;
  padding: 1rem;
}

.hljs-comment,
.hljs-quote {
  color: #6a737d;
  font-style: italic;
}

.hljs-keyword,
.hljs-selector-tag,
.hljs-deletion {
  color: #d73a49;
}

.hljs-string,
.hljs-attr,
.hljs-addition {
  color: #032f62;
}

.hljs-number,
.hljs-literal,
.hljs-variable,
.hljs-template-variable,
.hljs-tag .hljs-attr {
  color: #005cc5;
}

.hljs-function .hljs-title,
.hljs-title.function_,
.hljs-section {
  color: #6f42c1;
}

.hljs-built_in,
.hljs-builtin-name {
  color: #005cc5;
}

.hljs-type,
.hljs-class .hljs-title {
  color: #e36209;
}

.hljs-name,
.hljs-selector-id,
.hljs-selector-class {
  color: #22863a;
}

/* ============================================
   Dark Mode Syntax Highlighting
   ============================================ */
.dark .hljs {
  color: #c9d1d9;
  background: hsl(var(--muted));
}

.dark .hljs-comment,
.dark .hljs-quote {
  color: #8b949e;
}

.dark .hljs-keyword,
.dark .hljs-selector-tag,
.dark .hljs-deletion {
  color: #ff7b72;
}

.dark .hljs-string,
.dark .hljs-attr,
.dark .hljs-addition {
  color: #a5d6ff;
}

.dark .hljs-number,
.dark .hljs-literal,
.dark .hljs-variable,
.dark .hljs-template-variable {
  color: #79c0ff;
}

.dark .hljs-function .hljs-title,
.dark .hljs-title.function_,
.dark .hljs-section {
  color: #d2a8ff;
}

.dark .hljs-built_in,
.dark .hljs-builtin-name {
  color: #79c0ff;
}

.dark .hljs-type,
.dark .hljs-class .hljs-title {
  color: #ffa657;
}

.dark .hljs-name,
.dark .hljs-selector-id,
.dark .hljs-selector-class {
  color: #7ee787;
}

/* ============================================
   KaTeX Dark Mode Adjustments
   ============================================ */
.dark .katex {
  color: hsl(var(--foreground));
}

.katex-display {
  overflow-x: auto;
  padding: 0.5rem 0;
}

.katex-display > .katex {
  white-space: normal;
}

/* ============================================
   Mermaid Diagram Enhancements
   ============================================ */
.mermaid {
  text-align: center;
}

/* ============================================
   Markdown Viewer Typography
   ============================================ */
.markdown-viewer {
  line-height: 1.8;
}

.markdown-viewer p {
  margin-bottom: 1.25em;
}

.markdown-viewer h1,
.markdown-viewer h2,
.markdown-viewer h3,
.markdown-viewer h4 {
  margin-top: 1.5em;
  margin-bottom: 0.75em;
}

.markdown-viewer ul,
.markdown-viewer ol {
  margin-bottom: 1.25em;
}

.markdown-viewer li {
  margin-bottom: 0.5em;
}

.markdown-viewer blockquote {
  margin-top: 1.25em;
  margin-bottom: 1.25em;
}

/* ============================================
   Code Block Enhancements
   ============================================ */
.markdown-viewer pre {
  position: relative;
  margin-top: 1.25em;
  margin-bottom: 1.25em;
}

.markdown-viewer pre code {
  display: block;
  padding: 1rem;
  overflow-x: auto;
  font-size: 0.875rem;
  line-height: 1.6;
}

/* Scrollbar styling for code blocks */
.markdown-viewer pre code::-webkit-scrollbar {
  height: 6px;
}

.markdown-viewer pre code::-webkit-scrollbar-track {
  background: hsl(var(--muted));
  border-radius: 3px;
}

.markdown-viewer pre code::-webkit-scrollbar-thumb {
  background: hsl(var(--muted-foreground) / 0.3);
  border-radius: 3px;
}

.markdown-viewer pre code::-webkit-scrollbar-thumb:hover {
  background: hsl(var(--muted-foreground) / 0.5);
}
```

---

## 8. 구현 체크리스트

### Phase 7-1: 의존성 및 기초
- [x] 의존성 설치 (`gray-matter`, `remark-math`, `rehype-katex`, `katex`, `mermaid`, `rehype-slug`)
- [x] `@types/katex` 설치
- [x] `frontend/components/markdown/` 디렉토리 생성

### Phase 7-2: 컴포넌트 구현
- [x] `frontmatter-card.tsx` 구현
- [x] `code-block.tsx` 구현
- [x] `mermaid-diagram.tsx` 구현
- [x] `table-of-contents.tsx` 구현 (TOC Sidebar + Scroll Spy)
- [x] `index.ts` barrel export

### Phase 7-3: MarkdownViewer 통합
- [x] `markdown-viewer.tsx` 리팩토링
- [x] 플러그인 통합 (remark-math, rehype-slug, rehype-highlight, rehype-katex)
- [x] 컴포넌트 연동 (FrontmatterCard, CodeBlock, MermaidDiagram)
- [x] TOC 앵커 링크 (헤딩에 id 자동 생성, hover 시 링크 아이콘)

### Phase 7-4: 스타일링
- [x] `globals.css`에 highlight.js 테마 추가 (배경색 포함)
- [x] KaTeX CSS import 및 다크 모드 조정
- [x] Mermaid 다이어그램 스타일링
- [x] Typography 개선 (line-height, margin 조정)

### Phase 7-5: 검증
- [x] YAML Frontmatter 파싱 테스트
- [x] 코드 블록 하이라이팅 테스트 (Java, Python, JS, Dart)
- [x] Copy 버튼 동작 테스트
- [x] LaTeX 수식 렌더링 테스트
- [x] Mermaid 다이어그램 렌더링 테스트
- [x] 다크 모드 전환 테스트
- [x] TOC 앵커 링크 테스트
- [x] TOC 사이드바 테스트 (Scroll Spy, 현재 섹션 하이라이팅)
- [x] Typography/개행 간격 테스트

---

## 9. 잠재적 이슈 및 해결책

### Issue 1: gray-matter SSR 호환성

**문제**: gray-matter가 브라우저에서 Buffer를 사용할 수 있음

**해결책**:
```typescript
// 필요시 dynamic import 사용
const matter = typeof window === 'undefined'
  ? require('gray-matter')
  : (await import('gray-matter')).default;
```

### Issue 2: Mermaid SSR 에러

**문제**: Mermaid는 브라우저 API(DOM)에 의존

**해결책**:
- `'use client'` 지시자 사용
- Dynamic import로 클라이언트에서만 로드
- useEffect 내에서만 렌더링

### Issue 3: KaTeX 폰트 로딩

**문제**: KaTeX 폰트가 로드되지 않을 수 있음

**해결책**:
- CSS import로 폰트 자동 로드
- 또는 next.config.js에서 폰트 최적화 설정

### Issue 4: 코드 블록 내 HTML Escape

**문제**: `<`, `>` 등이 escape될 수 있음

**해결책**: rehype-highlight가 자동 처리, 추가 설정 불필요

---

## 10. 성능 최적화 팁

1. **Mermaid Lazy Loading**: Dynamic import 유지
2. **React.memo**: MermaidDiagram, CodeBlock에 적용 고려
3. **Debounce**: 테마 변경 시 다이어그램 재렌더링 debounce
4. **Virtual Scrolling**: 매우 긴 문서의 경우 고려 (Phase 7에서는 미구현)
