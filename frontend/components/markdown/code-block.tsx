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
  groovy: 'Groovy',
  gradle: 'Gradle',
  dockerfile: 'Dockerfile',
  nginx: 'Nginx',
  properties: 'Properties',
};

export function CodeBlock({ children, className, inline }: CodeBlockProps) {
  const [copied, setCopied] = useState(false);
  const codeRef = useRef<HTMLElement>(null);

  // Extract language from className (e.g., "language-javascript" or "hljs language-javascript")
  const match = /language-(\w+)/.exec(className || '');
  const language = match ? match[1] : '';
  const languageLabel = languageLabels[language] || (language ? language.toUpperCase() : '');

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
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch (err) {
      console.error('Failed to copy:', err);
    }
  };

  return (
    <div className="group relative my-4">
      {/* Header: Language badge and copy button */}
      <div className="absolute right-2 top-2 z-10 flex items-center gap-2 opacity-0 transition-opacity group-hover:opacity-100">
        {languageLabel && (
          <span className="flex items-center gap-1 rounded bg-background/80 px-2 py-1 text-xs font-medium backdrop-blur-sm">
            <Code2 className="h-3 w-3" />
            {languageLabel}
          </span>
        )}
        <Button
          variant="ghost"
          size="icon"
          className="h-7 w-7 bg-background/80 backdrop-blur-sm hover:bg-background"
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
          'block overflow-x-auto font-mono text-sm',
          className
        )}
      >
        {children}
      </code>
    </div>
  );
}
