'use client';

import { useEffect, useState, useCallback, useMemo } from 'react';
import GithubSlugger from 'github-slugger';
import matter from 'gray-matter';
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

// Strip markdown formatting from text to get plain text (like rehype-slug does)
function stripMarkdownFormatting(text: string): string {
  return text
    // Remove inline code backticks but keep content
    .replace(/`([^`]+)`/g, '$1')
    // Remove bold/italic markers
    .replace(/\*\*([^*]+)\*\*/g, '$1')
    .replace(/\*([^*]+)\*/g, '$1')
    .replace(/__([^_]+)__/g, '$1')
    .replace(/_([^_]+)_/g, '$1')
    // Remove links but keep text [text](url) -> text
    .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1')
    // Remove images ![alt](url) -> alt
    .replace(/!\[([^\]]*)\]\([^)]+\)/g, '$1')
    // Remove strikethrough
    .replace(/~~([^~]+)~~/g, '$1');
}

// Remove code blocks from content (they might contain # that look like headings)
function removeCodeBlocks(content: string): string {
  const lines = content.split('\n');
  const result: string[] = [];
  let inCodeBlock = false;
  let codeFence = ''; // The actual fence string (e.g., '```', '````', '~~~')
  let fenceChar = ''; // '`' or '~'

  for (const line of lines) {
    const trimmedLine = line.trim();

    // Match fence: 3 or more backticks or tildes
    const fenceMatch = trimmedLine.match(/^(`{3,}|~{3,})/);

    if (!inCodeBlock && fenceMatch) {
      // Starting a code block
      inCodeBlock = true;
      codeFence = fenceMatch[1]; // e.g., '```' or '````'
      fenceChar = codeFence[0]; // '`' or '~'
      continue; // Skip fence line
    }

    if (inCodeBlock) {
      // Check if this line closes the code block
      // Closing fence must be the same char and at least as long as opening
      const closeFenceMatch = trimmedLine.match(new RegExp(`^${fenceChar}{${codeFence.length},}\\s*$`));
      if (closeFenceMatch) {
        // Ending the code block
        inCodeBlock = false;
        codeFence = '';
        fenceChar = '';
        continue; // Skip fence line
      }
    }

    // Only include lines that are not inside a code block
    if (!inCodeBlock) {
      result.push(line);
    }
  }

  return result.join('\n');
}

// Extract headings from markdown content using github-slugger (same as rehype-slug)
function extractHeadings(rawContent: string): TocItem[] {
  const headings: TocItem[] = [];
  const slugger = new GithubSlugger();

  // 1. Strip frontmatter (same as MarkdownViewer does)
  let content: string;
  try {
    content = matter(rawContent).content;
  } catch {
    content = rawContent;
  }

  // 2. Remove code blocks (headings inside code blocks are not rendered)
  const contentWithoutCodeBlocks = removeCodeBlocks(content);

  // DEBUG: Log in development
  if (process.env.NODE_ENV === 'development') {
    console.log('[TOC] Original lines:', content.split('\n').length);
    console.log('[TOC] After code block removal:', contentWithoutCodeBlocks.split('\n').length);
  }

  const lines = contentWithoutCodeBlocks.split('\n');

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    // Match markdown headings (## Heading)
    const match = line.match(/^(#{1,6})\s+(.+)$/);
    if (match) {
      const level = match[1].length;
      const rawText = match[2].trim();
      // Strip markdown formatting to match what rehype-slug sees (plain text)
      const plainText = stripMarkdownFormatting(rawText);
      // Use github-slugger for consistent ID generation with rehype-slug
      const id = slugger.slug(plainText);

      // DEBUG: Log each heading found
      if (process.env.NODE_ENV === 'development') {
        console.log(`[TOC] Found heading at line ${i}: "${rawText}" -> id="${id}"`);
      }

      if (id) {
        headings.push({ id, text: rawText, level });
      }
    }
  }

  // DEBUG: Log total headings
  if (process.env.NODE_ENV === 'development') {
    console.log('[TOC] Total headings extracted:', headings.length);
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
