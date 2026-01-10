'use client';

import { useMemo } from 'react';
import { cn } from '@/lib/utils';

interface DiffLine {
  type: 'unchanged' | 'added' | 'removed' | 'header';
  content: string;
  lineNumber?: number;
}

/**
 * Simple line-based diff calculation
 * Compares two strings line by line and returns an array of diff lines
 */
export function calculateDiff(original: string, modified: string): DiffLine[] {
  const originalLines = original.split('\n');
  const modifiedLines = modified.split('\n');
  const result: DiffLine[] = [];

  // Add diff header
  result.push({ type: 'header', content: '--- Original' });
  result.push({ type: 'header', content: '+++ Modified' });

  // Simple LCS-based diff
  const lcs = computeLCS(originalLines, modifiedLines);

  let origIdx = 0;
  let modIdx = 0;
  let lcsIdx = 0;

  while (origIdx < originalLines.length || modIdx < modifiedLines.length) {
    if (lcsIdx < lcs.length && origIdx < originalLines.length && originalLines[origIdx] === lcs[lcsIdx]) {
      // Line exists in both - unchanged
      if (modIdx < modifiedLines.length && modifiedLines[modIdx] === lcs[lcsIdx]) {
        result.push({
          type: 'unchanged',
          content: originalLines[origIdx],
          lineNumber: origIdx + 1,
        });
        origIdx++;
        modIdx++;
        lcsIdx++;
      } else {
        // Modified line exists before the common line
        result.push({
          type: 'added',
          content: modifiedLines[modIdx],
          lineNumber: modIdx + 1,
        });
        modIdx++;
      }
    } else if (origIdx < originalLines.length && (lcsIdx >= lcs.length || originalLines[origIdx] !== lcs[lcsIdx])) {
      // Line removed from original
      result.push({
        type: 'removed',
        content: originalLines[origIdx],
        lineNumber: origIdx + 1,
      });
      origIdx++;
    } else if (modIdx < modifiedLines.length) {
      // Line added in modified
      result.push({
        type: 'added',
        content: modifiedLines[modIdx],
        lineNumber: modIdx + 1,
      });
      modIdx++;
    } else {
      break;
    }
  }

  return result;
}

/**
 * Compute Longest Common Subsequence of two string arrays
 */
function computeLCS(a: string[], b: string[]): string[] {
  const m = a.length;
  const n = b.length;

  // Create DP table
  const dp: number[][] = Array(m + 1).fill(null).map(() => Array(n + 1).fill(0));

  for (let i = 1; i <= m; i++) {
    for (let j = 1; j <= n; j++) {
      if (a[i - 1] === b[j - 1]) {
        dp[i][j] = dp[i - 1][j - 1] + 1;
      } else {
        dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
      }
    }
  }

  // Backtrack to find LCS
  const lcs: string[] = [];
  let i = m, j = n;
  while (i > 0 && j > 0) {
    if (a[i - 1] === b[j - 1]) {
      lcs.unshift(a[i - 1]);
      i--;
      j--;
    } else if (dp[i - 1][j] > dp[i][j - 1]) {
      i--;
    } else {
      j--;
    }
  }

  return lcs;
}

interface DiffViewerProps {
  original: string;
  modified: string;
  maxHeight?: string;
  className?: string;
}

export function DiffViewer({ original, modified, maxHeight = '300px', className }: DiffViewerProps) {
  const diffLines = useMemo(() => calculateDiff(original, modified), [original, modified]);

  // Count changes
  const stats = useMemo(() => {
    const added = diffLines.filter(l => l.type === 'added').length;
    const removed = diffLines.filter(l => l.type === 'removed').length;
    return { added, removed };
  }, [diffLines]);

  if (stats.added === 0 && stats.removed === 0) {
    return (
      <div className={cn('text-sm text-muted-foreground text-center py-4', className)}>
        No changes detected
      </div>
    );
  }

  return (
    <div className={cn('space-y-2', className)}>
      <div className="flex gap-4 text-xs">
        <span className="text-green-600 dark:text-green-400">+{stats.added} additions</span>
        <span className="text-red-600 dark:text-red-400">-{stats.removed} deletions</span>
      </div>
      <div
        className="overflow-auto rounded-lg border bg-muted/50 font-mono text-xs"
        style={{ maxHeight }}
      >
        {diffLines.map((line, idx) => {
          const lineStyles = {
            unchanged: '',
            added: 'bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-300',
            removed: 'bg-red-100 dark:bg-red-900/30 text-red-800 dark:text-red-300',
            header: 'bg-blue-100 dark:bg-blue-900/30 text-blue-800 dark:text-blue-300 font-semibold',
          };

          const prefix = {
            unchanged: ' ',
            added: '+',
            removed: '-',
            header: '',
          };

          return (
            <div
              key={idx}
              className={cn('px-2 py-0.5 whitespace-pre', lineStyles[line.type])}
            >
              <span className="inline-block w-4 text-muted-foreground select-none">
                {prefix[line.type]}
              </span>
              {line.content || ' '}
            </div>
          );
        })}
      </div>
    </div>
  );
}

/**
 * Simple unified diff format viewer (for server-generated diffs)
 */
interface UnifiedDiffViewerProps {
  diff: string;
  maxHeight?: string;
  className?: string;
}

export function UnifiedDiffViewer({ diff, maxHeight = '300px', className }: UnifiedDiffViewerProps) {
  const lines = diff.split('\n');

  return (
    <div
      className={cn('overflow-auto rounded-lg border bg-muted/50 font-mono text-xs', className)}
      style={{ maxHeight }}
    >
      {lines.map((line, idx) => {
        let bgColor = '';
        let textColor = '';

        if (line.startsWith('+') && !line.startsWith('+++')) {
          bgColor = 'bg-green-100 dark:bg-green-900/30';
          textColor = 'text-green-800 dark:text-green-300';
        } else if (line.startsWith('-') && !line.startsWith('---')) {
          bgColor = 'bg-red-100 dark:bg-red-900/30';
          textColor = 'text-red-800 dark:text-red-300';
        } else if (line.startsWith('@@')) {
          bgColor = 'bg-blue-100 dark:bg-blue-900/30';
          textColor = 'text-blue-800 dark:text-blue-300';
        }

        return (
          <div key={idx} className={cn('px-2 py-0.5 whitespace-pre', bgColor, textColor)}>
            {line || ' '}
          </div>
        );
      })}
    </div>
  );
}
