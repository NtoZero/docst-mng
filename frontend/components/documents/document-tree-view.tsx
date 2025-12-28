'use client';

import { useState, useMemo } from 'react';
import { Link } from '@/i18n/routing';
import { FileText, Folder, FolderOpen, ChevronRight, ChevronDown, UnfoldVertical, FoldVertical } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { buildDocumentTree, type TreeNode } from '@/lib/document-utils';
import type { Document, DocType } from '@/lib/types';
import { cn } from '@/lib/utils';

function getDocTypeBadge(docType: DocType) {
  const variants: Record<DocType, { label: string; variant: 'default' | 'secondary' | 'outline' }> =
    {
      MD: { label: 'Markdown', variant: 'default' },
      ADOC: { label: 'AsciiDoc', variant: 'secondary' },
      OPENAPI: { label: 'OpenAPI', variant: 'outline' },
      ADR: { label: 'ADR', variant: 'secondary' },
      OTHER: { label: 'Other', variant: 'outline' },
    };
  const { label, variant } = variants[docType] || variants.OTHER;
  return <Badge variant={variant}>{label}</Badge>;
}

interface DocumentTreeViewProps {
  documents: Document[];
}

export function DocumentTreeView({ documents }: DocumentTreeViewProps) {
  const tree = buildDocumentTree(documents);
  const [expandedFolders, setExpandedFolders] = useState<Set<string>>(new Set(['/']));

  // 모든 폴더 경로를 수집
  const allFolderPaths = useMemo(() => {
    const paths: string[] = [];
    const collectFolders = (nodes: TreeNode[]) => {
      nodes.forEach((node) => {
        if (node.type === 'folder') {
          paths.push(node.path);
          if (node.children) {
            collectFolders(node.children);
          }
        }
      });
    };
    collectFolders(tree);
    return paths;
  }, [tree]);

  const toggleFolder = (path: string) => {
    setExpandedFolders((prev) => {
      const next = new Set(prev);
      if (next.has(path)) {
        next.delete(path);
      } else {
        next.add(path);
      }
      return next;
    });
  };

  const expandAll = () => {
    setExpandedFolders(new Set(allFolderPaths));
  };

  const collapseAll = () => {
    setExpandedFolders(new Set());
  };

  const renderNode = (node: TreeNode) => {
    const isExpanded = expandedFolders.has(node.path);
    const paddingLeft = node.level * 20;

    if (node.type === 'folder') {
      return (
        <div key={node.path}>
          <div
            className={cn(
              'flex items-center gap-2 rounded-md px-2 py-2 text-sm transition-colors hover:bg-accent cursor-pointer',
            )}
            style={{ paddingLeft: `${paddingLeft}px` }}
            onClick={() => toggleFolder(node.path)}
          >
            {isExpanded ? (
              <ChevronDown className="h-4 w-4 text-muted-foreground" />
            ) : (
              <ChevronRight className="h-4 w-4 text-muted-foreground" />
            )}
            {isExpanded ? (
              <FolderOpen className="h-4 w-4 text-muted-foreground" />
            ) : (
              <Folder className="h-4 w-4 text-muted-foreground" />
            )}
            <span className="font-medium">{node.name}</span>
            <span className="text-xs text-muted-foreground">
              ({node.children?.length || 0})
            </span>
          </div>
          {isExpanded && node.children && (
            <div>{node.children.map((child) => renderNode(child))}</div>
          )}
        </div>
      );
    }

    // File node
    return (
      <Link key={node.path} href={`/documents/${node.document?.id}`}>
        <div
          className={cn(
            'flex items-center justify-between gap-2 rounded-md px-2 py-2 text-sm transition-colors hover:bg-accent cursor-pointer',
          )}
          style={{ paddingLeft: `${paddingLeft + 24}px` }}
        >
          <div className="flex items-center gap-2 flex-1 min-w-0">
            <FileText className="h-4 w-4 text-muted-foreground flex-shrink-0" />
            <div className="min-w-0 flex-1">
              <p className="font-medium truncate">{node.name}</p>
              {node.document?.title && node.document.title !== node.name && (
                <p className="text-xs text-muted-foreground truncate">{node.document.title}</p>
              )}
            </div>
          </div>
          <div className="flex items-center gap-2 flex-shrink-0">
            {node.document && getDocTypeBadge(node.document.docType)}
            <span className="text-xs text-muted-foreground">
              {node.document?.latestCommitSha?.substring(0, 7)}
            </span>
          </div>
        </div>
      </Link>
    );
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2">
        <Button
          variant="outline"
          size="sm"
          onClick={expandAll}
          className="h-8"
        >
          <UnfoldVertical className="h-3.5 w-3.5 mr-2" />
          Expand All
        </Button>
        <Button
          variant="outline"
          size="sm"
          onClick={collapseAll}
          className="h-8"
        >
          <FoldVertical className="h-3.5 w-3.5 mr-2" />
          Collapse All
        </Button>
      </div>
      <div className="space-y-1">
        {tree.map((node) => renderNode(node))}
      </div>
    </div>
  );
}
