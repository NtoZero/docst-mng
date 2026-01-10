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
  const idCounts = new Map<string, number>();

  for (const line of lines) {
    // Match markdown headings (## Heading)
    const match = line.match(/^(#{1,6})\s+(.+)$/);
    if (match) {
      const level = match[1].length;
      const text = match[2].trim();
      // Generate slug similar to rehype-slug
      let baseId = text
        .toLowerCase()
        .replace(/[^a-z0-9가-힣\s-]/g, '')
        .replace(/\s+/g, '-')
        .replace(/-+/g, '-')
        .replace(/^-|-$/g, '');

      if (baseId) {
        // Handle duplicate IDs by appending a counter (like rehype-slug)
        const count = idCounts.get(baseId) || 0;
        const id = count === 0 ? baseId : `${baseId}-${count}`;
        idCounts.set(baseId, count + 1);

        headings.push({ id, text, level });
      }
    }
  }

  return headings;
}

export function TableOfContents({ content, className }: TableOfContentsProps) {
  const [headings, setHeadings] = useState<TocItem[]>([]);
  const [activeId, setActiveId] = useState<string>('');

  // Extract headings from content
  useEffect(() => {
    setHeadings(extractHeadings(content));
  }, [content]);

  // Scroll spy: track which heading is currently visible
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
      {
        rootMargin: '-80px 0px -80% 0px',
        threshold: 0,
      }
    );

    // Observe all heading elements
    headings.forEach(({ id }) => {
      const element = document.getElementById(id);
      if (element) {
        observer.observe(element);
      }
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

  if (headings.length === 0) {
    return null;
  }

  // Find minimum level for proper indentation
  const minLevel = Math.min(...headings.map((h) => h.level));

  return (
    <nav className={cn('flex flex-col', className)}>
      <div className="flex items-center gap-2 pb-2 text-sm font-medium text-muted-foreground shrink-0">
        <List className="h-4 w-4" />
        <span>목차</span>
      </div>
      <ul className="space-y-1 text-sm overflow-y-auto max-h-[calc(100vh-12rem)] pr-1 scrollbar-thin">
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
                  isActive
                    ? 'bg-primary/10 font-medium text-primary'
                    : 'text-muted-foreground'
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
