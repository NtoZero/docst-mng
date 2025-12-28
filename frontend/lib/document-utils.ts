import type { Document } from './types';

export interface TreeNode {
  name: string;
  type: 'folder' | 'file';
  path: string;
  document?: Document;
  children?: TreeNode[];
  level: number;
}

/**
 * 문서 배열을 트리 구조로 변환한다.
 */
export function buildDocumentTree(documents: Document[]): TreeNode[] {
  const root: TreeNode[] = [];
  const folderMap = new Map<string, TreeNode>();

  // 먼저 모든 폴더 노드 생성
  documents.forEach((doc) => {
    const parts = doc.path.split('/');
    let currentPath = '';

    // 파일명을 제외한 모든 경로 부분에 대해 폴더 생성
    for (let i = 0; i < parts.length - 1; i++) {
      const part = parts[i];
      const parentPath = currentPath;
      currentPath = currentPath ? `${currentPath}/${part}` : part;

      if (!folderMap.has(currentPath)) {
        const folderNode: TreeNode = {
          name: part,
          type: 'folder',
          path: currentPath,
          children: [],
          level: i,
        };
        folderMap.set(currentPath, folderNode);

        // 부모에 추가
        if (parentPath) {
          const parent = folderMap.get(parentPath);
          if (parent && parent.children) {
            parent.children.push(folderNode);
          }
        } else {
          root.push(folderNode);
        }
      }
    }
  });

  // 파일 노드 추가
  documents.forEach((doc) => {
    const parts = doc.path.split('/');
    const fileName = parts[parts.length - 1];
    const folderPath = parts.slice(0, -1).join('/');
    const level = parts.length - 1;

    const fileNode: TreeNode = {
      name: fileName,
      type: 'file',
      path: doc.path,
      document: doc,
      level,
    };

    if (folderPath) {
      const folder = folderMap.get(folderPath);
      if (folder && folder.children) {
        folder.children.push(fileNode);
      }
    } else {
      root.push(fileNode);
    }
  });

  // 각 폴더의 children 정렬 (폴더 먼저, 그 다음 파일, 알파벳 순)
  const sortNodes = (nodes: TreeNode[]) => {
    nodes.sort((a, b) => {
      if (a.type !== b.type) {
        return a.type === 'folder' ? -1 : 1;
      }
      return a.name.localeCompare(b.name);
    });
    nodes.forEach((node) => {
      if (node.children) {
        sortNodes(node.children);
      }
    });
  };

  sortNodes(root);
  return root;
}

/**
 * 문서를 경로별로 그룹화한다 (기존 Content View용).
 */
export function groupDocumentsByPath(documents: Document[]): [string, Document[]][] {
  const tree: Record<string, Document[]> = {};

  documents.forEach((doc) => {
    const parts = doc.path.split('/');
    const folder = parts.length > 1 ? parts.slice(0, -1).join('/') : '/';
    if (!tree[folder]) {
      tree[folder] = [];
    }
    tree[folder].push(doc);
  });

  return Object.entries(tree).sort(([a], [b]) => a.localeCompare(b));
}
