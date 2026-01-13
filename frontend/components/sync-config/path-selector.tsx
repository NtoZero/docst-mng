'use client';

import { ChevronDown, ChevronRight, Folder, X } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { useFolderTree } from '@/hooks/use-api';
import { useState, useCallback } from 'react';
import type { FolderTreeItem } from '@/lib/types';
import { cn } from '@/lib/utils';

interface PathSelectorProps {
  includePaths: string[];
  excludePaths: string[];
  onIncludePathsChange: (paths: string[]) => void;
  onExcludePathsChange: (paths: string[]) => void;
  repositoryId: string;
  disabled?: boolean;
}

// 기본 제외 경로
const DEFAULT_EXCLUDE_PATHS = [
  '.git',
  'node_modules',
  'target',
  'build',
  '.gradle',
  'dist',
  'out',
  '__pycache__',
  '.venv',
  'vendor',
];

export function PathSelector({
  includePaths,
  excludePaths,
  onIncludePathsChange,
  onExcludePathsChange,
  repositoryId,
  disabled = false,
}: PathSelectorProps) {
  const { data: treeData, isLoading } = useFolderTree(repositoryId);
  const [expandedFolders, setExpandedFolders] = useState<Set<string>>(new Set());
  const [customPath, setCustomPath] = useState('');
  const [mode, setMode] = useState<'include' | 'exclude'>('include');

  const toggleFolder = useCallback((path: string) => {
    setExpandedFolders((prev) => {
      const next = new Set(prev);
      if (next.has(path)) {
        next.delete(path);
      } else {
        next.add(path);
      }
      return next;
    });
  }, []);

  const handleAddPath = (path: string) => {
    if (mode === 'include') {
      if (!includePaths.includes(path)) {
        onIncludePathsChange([...includePaths, path]);
      }
    } else {
      if (!excludePaths.includes(path)) {
        onExcludePathsChange([...excludePaths, path]);
      }
    }
  };

  const handleRemovePath = (path: string, type: 'include' | 'exclude') => {
    if (type === 'include') {
      onIncludePathsChange(includePaths.filter((p) => p !== path));
    } else {
      onExcludePathsChange(excludePaths.filter((p) => p !== path));
    }
  };

  const handleAddCustomPath = () => {
    if (customPath.trim()) {
      let normalizedPath = customPath.trim();
      if (!normalizedPath.endsWith('/')) {
        normalizedPath += '/';
      }
      handleAddPath(normalizedPath);
      setCustomPath('');
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      handleAddCustomPath();
    }
  };

  const isPathSelected = (path: string): 'include' | 'exclude' | null => {
    if (includePaths.includes(path)) return 'include';
    if (excludePaths.includes(path)) return 'exclude';
    return null;
  };

  const renderFolderTree = (items: FolderTreeItem[], depth: number = 0) => {
    return items.map((item) => {
      const isExpanded = expandedFolders.has(item.path);
      const hasChildren = item.children && item.children.length > 0;
      const selection = isPathSelected(item.path);

      return (
        <div key={item.path}>
          <div
            className={cn(
              'flex items-center gap-1 py-1 px-2 rounded-md hover:bg-muted/50 cursor-pointer',
              selection === 'include' && 'bg-green-100 dark:bg-green-900/30',
              selection === 'exclude' && 'bg-red-100 dark:bg-red-900/30'
            )}
            style={{ paddingLeft: `${depth * 16 + 8}px` }}
          >
            {hasChildren ? (
              <button
                type="button"
                onClick={() => toggleFolder(item.path)}
                className="p-0.5"
              >
                {isExpanded ? (
                  <ChevronDown className="h-4 w-4" />
                ) : (
                  <ChevronRight className="h-4 w-4" />
                )}
              </button>
            ) : (
              <span className="w-5" />
            )}
            <Folder className="h-4 w-4 text-muted-foreground" />
            <span
              className="text-sm flex-1"
              onClick={() => !disabled && handleAddPath(item.path)}
            >
              {item.name}
            </span>
            {selection && (
              <Badge
                variant={selection === 'include' ? 'default' : 'destructive'}
                className="text-xs"
              >
                {selection}
              </Badge>
            )}
          </div>
          {hasChildren && isExpanded && (
            <div>{renderFolderTree(item.children, depth + 1)}</div>
          )}
        </div>
      );
    });
  };

  return (
    <div className="space-y-4">
      <Tabs value={mode} onValueChange={(v) => setMode(v as 'include' | 'exclude')}>
        <TabsList className="grid w-full grid-cols-2">
          <TabsTrigger value="include">Include Paths</TabsTrigger>
          <TabsTrigger value="exclude">Exclude Paths</TabsTrigger>
        </TabsList>

        <TabsContent value="include" className="space-y-4">
          {/* 선택된 포함 경로 */}
          <div>
            <label className="text-sm font-medium mb-2 block">
              Included Paths {includePaths.length === 0 && '(All paths included)'}
            </label>
            <div className="flex flex-wrap gap-2 min-h-[40px] p-2 border rounded-md bg-muted/30">
              {includePaths.length === 0 ? (
                <span className="text-sm text-muted-foreground">
                  Click on folders to include specific paths
                </span>
              ) : (
                includePaths.map((path) => (
                  <Badge key={path} variant="default" className="gap-1">
                    {path}
                    <button
                      type="button"
                      onClick={() => handleRemovePath(path, 'include')}
                      disabled={disabled}
                    >
                      <X className="h-3 w-3" />
                    </button>
                  </Badge>
                ))
              )}
            </div>
          </div>
        </TabsContent>

        <TabsContent value="exclude" className="space-y-4">
          {/* 선택된 제외 경로 */}
          <div>
            <label className="text-sm font-medium mb-2 block">Excluded Paths</label>
            <div className="flex flex-wrap gap-2 min-h-[40px] p-2 border rounded-md bg-muted/30">
              {excludePaths.length === 0 ? (
                <span className="text-sm text-muted-foreground">
                  No paths excluded
                </span>
              ) : (
                excludePaths.map((path) => (
                  <Badge key={path} variant="destructive" className="gap-1">
                    {path}
                    <button
                      type="button"
                      onClick={() => handleRemovePath(path, 'exclude')}
                      disabled={disabled}
                    >
                      <X className="h-3 w-3" />
                    </button>
                  </Badge>
                ))
              )}
            </div>
          </div>

          {/* 기본 제외 경로 추천 */}
          <div>
            <label className="text-sm font-medium mb-2 block">Common Exclusions</label>
            <div className="flex flex-wrap gap-1">
              {DEFAULT_EXCLUDE_PATHS.filter((p) => !excludePaths.includes(p)).map(
                (path) => (
                  <Button
                    key={path}
                    variant="ghost"
                    size="sm"
                    className="h-6 text-xs px-2"
                    onClick={() => handleAddPath(path)}
                    disabled={disabled}
                  >
                    {path}
                  </Button>
                )
              )}
            </div>
          </div>
        </TabsContent>
      </Tabs>

      {/* 폴더 트리 */}
      <div>
        <label className="text-sm font-medium mb-2 block">Repository Folders</label>
        <div className="border rounded-md max-h-[250px] overflow-y-auto">
          {isLoading ? (
            <div className="p-4 text-center text-muted-foreground">Loading...</div>
          ) : treeData?.folders && treeData.folders.length > 0 ? (
            renderFolderTree(treeData.folders)
          ) : (
            <div className="p-4 text-center text-muted-foreground">
              No folders found
            </div>
          )}
        </div>
      </div>

      {/* 커스텀 경로 입력 */}
      <div>
        <label className="text-sm font-medium mb-2 block">Custom Path</label>
        <div className="flex gap-2">
          <Input
            value={customPath}
            onChange={(e) => setCustomPath(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="e.g., src/main/"
            className="flex-1"
            disabled={disabled}
          />
          <Button onClick={handleAddCustomPath} disabled={disabled || !customPath.trim()}>
            Add to {mode === 'include' ? 'Include' : 'Exclude'}
          </Button>
        </div>
      </div>
    </div>
  );
}
