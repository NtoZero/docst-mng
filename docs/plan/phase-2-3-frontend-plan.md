# Phase 2-3: 프론트엔드 구현 계획

> **작성일**: 2025-12-28
> **현재 상태**: Phase 1 프론트엔드 완료 (프로젝트, 저장소, 문서, 키워드 검색, 동기화, 다국어)
> **목표**: 의미 검색 UI, OAuth, 문서 그래프, 멤버 관리 프론트엔드 구현

---

## 현재 프론트엔드 구현 상황

### 완료된 기능

| 항목 | 상태 | 위치 |
|------|------|------|
| 로컬 로그인 | ✅ | `app/[locale]/login/page.tsx` |
| 프로젝트 CRUD | ✅ | `app/[locale]/projects/` |
| 저장소 관리 | ✅ | `app/[locale]/projects/[projectId]/` |
| 문서 목록 | ✅ | `app/[locale]/projects/.../documents/page.tsx` |
| 문서 상세 | ✅ | `app/[locale]/documents/[docId]/page.tsx` |
| 버전 비교(Diff) | ✅ | `app/[locale]/documents/[docId]/versions/page.tsx` |
| 키워드 검색 | ✅ | `app/[locale]/projects/[projectId]/search/page.tsx` |
| SSE 동기화 | ✅ | `hooks/use-sync.ts` |
| 자격증명 관리 | ✅ | `app/[locale]/credentials/page.tsx` |
| 다국어 (en/ko) | ✅ | `messages/*.json` |

### 미구현 기능

| 항목 | Phase | 비고 |
|------|-------|------|
| 검색 모드 선택 | 2-C | 현재 `mode: 'keyword'` 하드코딩 |
| 청킹 결과 표시 | 2-C | headingPath, chunkId 미표시 |
| GitHub OAuth 로그인 | 3-B | 로컬 로그인만 존재 |
| OAuth 콜백 | 3-B | 미구현 |
| 문서 관계 그래프 | 3-D | 미구현 |
| 프로젝트 설정 | 3 | 미구현 |
| 멤버 관리 | 3 | 미구현 |

---

## 구현 우선순위

```
1. [우선] Phase 2-C: 의미/하이브리드 검색 UI
2. [중요] Phase 3-B: GitHub OAuth 프론트엔드
3. [보통] Phase 3-D: 문서 관계 그래프
4. [보통] Phase 3: 프로젝트 설정 & 멤버 관리
```

---

## Phase 2-C: 의미/하이브리드 검색 UI

### 1. 검색 모드 선택 컴포넌트

**신규 파일**: `frontend/components/search-mode-select.tsx`

```tsx
'use client';

import { useTranslations } from 'next-intl';

interface SearchModeSelectProps {
  value: 'keyword' | 'semantic' | 'hybrid';
  onChange: (mode: 'keyword' | 'semantic' | 'hybrid') => void;
}

export function SearchModeSelect({ value, onChange }: SearchModeSelectProps) {
  const t = useTranslations('search');

  return (
    <div className="flex gap-1 p-1 bg-muted rounded-lg">
      <button
        onClick={() => onChange('keyword')}
        className={cn(
          "px-3 py-1.5 text-sm rounded-md transition-colors",
          value === 'keyword'
            ? "bg-background shadow text-foreground"
            : "text-muted-foreground hover:text-foreground"
        )}
      >
        {t('modeKeyword')}
      </button>
      <button
        onClick={() => onChange('semantic')}
        className={cn(
          "px-3 py-1.5 text-sm rounded-md transition-colors",
          value === 'semantic'
            ? "bg-background shadow text-foreground"
            : "text-muted-foreground hover:text-foreground"
        )}
      >
        {t('modeSemantic')}
      </button>
      <button
        onClick={() => onChange('hybrid')}
        className={cn(
          "px-3 py-1.5 text-sm rounded-md transition-colors flex items-center gap-1",
          value === 'hybrid'
            ? "bg-background shadow text-foreground"
            : "text-muted-foreground hover:text-foreground"
        )}
      >
        {t('modeHybrid')}
        <Badge variant="secondary" className="text-xs">
          {t('recommended')}
        </Badge>
      </button>
    </div>
  );
}
```

### 2. 검색 페이지 수정

**수정 파일**: `frontend/app/[locale]/projects/[projectId]/search/page.tsx`

현재 코드:
```tsx
const { data: results, isLoading } = useSearch(projectId, {
  q: searchQuery,
  mode: 'keyword',  // 하드코딩
  topK: 20,
});
```

변경 후:
```tsx
const [mode, setMode] = useState<'keyword' | 'semantic' | 'hybrid'>('hybrid');

const { data: results, isLoading } = useSearch(projectId, {
  q: searchQuery,
  mode: mode,
  topK: 20,
});

// JSX
<div className="flex flex-col gap-4">
  <div className="flex items-center gap-4">
    <Input
      value={searchQuery}
      onChange={(e) => setSearchQuery(e.target.value)}
      placeholder={t('searchPlaceholder')}
      className="flex-1"
    />
    <SearchModeSelect value={mode} onChange={setMode} />
    <Button onClick={handleSearch}>
      <Search className="w-4 h-4 mr-2" />
      {t('search')}
    </Button>
  </div>

  {/* 검색 결과 */}
  {results?.map((result) => (
    <SearchResultCard key={result.chunkId} result={result} />
  ))}
</div>
```

### 3. 검색 결과 카드 개선

**신규 파일**: `frontend/components/search-result-card.tsx`

```tsx
'use client';

import { FileText, Hash, MapPin } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';

interface SearchResultCardProps {
  result: {
    chunkId: string;
    documentPath: string;
    headingPath?: string;
    content: string;
    score: number;
    highlights?: string[];
  };
}

export function SearchResultCard({ result }: SearchResultCardProps) {
  const scorePercent = Math.round(result.score * 100);

  return (
    <Card className="hover:shadow-md transition-shadow">
      <CardContent className="pt-4">
        {/* 문서 경로 */}
        <div className="flex items-center gap-2 text-sm text-muted-foreground mb-2">
          <FileText className="w-4 h-4" />
          <span>{result.documentPath}</span>
        </div>

        {/* 헤딩 경로 (의미 검색 시) */}
        {result.headingPath && (
          <div className="flex items-center gap-2 text-sm text-blue-600 mb-2">
            <MapPin className="w-4 h-4" />
            <span>{result.headingPath}</span>
          </div>
        )}

        {/* 스니펫 */}
        <div className="text-sm mb-3 line-clamp-3">
          {result.highlights ? (
            <span
              dangerouslySetInnerHTML={{
                __html: result.highlights.join('...')
              }}
            />
          ) : (
            result.content.slice(0, 200) + '...'
          )}
        </div>

        {/* 점수 및 청크 ID */}
        <div className="flex items-center justify-between">
          <Badge variant={scorePercent >= 80 ? 'default' : 'secondary'}>
            {scorePercent}% match
          </Badge>
          <span className="text-xs text-muted-foreground flex items-center gap-1">
            <Hash className="w-3 h-3" />
            {result.chunkId.slice(0, 8)}
          </span>
        </div>
      </CardContent>
    </Card>
  );
}
```

### 4. 타입 정의 확장

**수정 파일**: `frontend/lib/types.ts`

```typescript
// 기존 SearchResult 확장
export interface SearchResult {
  documentId: string;
  documentPath: string;
  title: string;
  snippet: string;
  highlights: string[];
  score: number;
  // 의미 검색 추가 필드
  chunkId?: string;
  headingPath?: string;
  tokenCount?: number;
}

// 검색 요청 타입 확장
export interface SearchRequest {
  q: string;
  mode: 'keyword' | 'semantic' | 'hybrid';
  topK?: number;
  repositoryId?: string;  // 저장소 필터
  docType?: DocType;      // 문서 타입 필터
}
```

### 5. 다국어 메시지 추가

**수정 파일**: `frontend/messages/en.json`

```json
{
  "search": {
    "title": "Search Documents",
    "searchPlaceholder": "Enter search query...",
    "search": "Search",
    "noResults": "No results found",
    "modeKeyword": "Keyword",
    "modeSemantic": "Semantic",
    "modeHybrid": "Hybrid",
    "recommended": "Recommended",
    "results": "{count} results",
    "matchScore": "{score}% match",
    "filterByType": "Filter by type",
    "filterByRepo": "Filter by repository"
  }
}
```

**수정 파일**: `frontend/messages/ko.json`

```json
{
  "search": {
    "title": "문서 검색",
    "searchPlaceholder": "검색어를 입력하세요...",
    "search": "검색",
    "noResults": "검색 결과가 없습니다",
    "modeKeyword": "키워드",
    "modeSemantic": "의미",
    "modeHybrid": "하이브리드",
    "recommended": "추천",
    "results": "{count}개 결과",
    "matchScore": "{score}% 일치",
    "filterByType": "타입별 필터",
    "filterByRepo": "저장소별 필터"
  }
}
```

### 6. 작업 목록 (Phase 2-C)

- [ ] `search-mode-select.tsx` 컴포넌트 생성
- [ ] `search-result-card.tsx` 컴포넌트 생성
- [ ] 검색 페이지 mode 상태 추가
- [ ] `types.ts`에 SearchResult 확장
- [ ] 다국어 메시지 추가 (en.json, ko.json)
- [ ] 검색 필터 UI 추가 (문서 타입, 저장소)
- [ ] 검색 결과 정렬 옵션 추가
- [ ] 검색 히스토리 저장 (localStorage)

---

## Phase 3-B: GitHub OAuth 프론트엔드

### 1. 로그인 페이지 수정

**수정 파일**: `frontend/app/[locale]/login/page.tsx`

```tsx
'use client';

import { Github } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Separator } from '@/components/ui/separator';

export default function LoginPage() {
  const t = useTranslations('auth');

  const handleGitHubLogin = () => {
    // GitHub OAuth 시작
    window.location.href = `${API_BASE}/api/auth/github/start`;
  };

  return (
    <div className="min-h-screen flex items-center justify-center">
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle>{t('loginTitle')}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          {/* GitHub OAuth 버튼 */}
          <Button
            onClick={handleGitHubLogin}
            className="w-full"
            variant="outline"
          >
            <Github className="w-5 h-5 mr-2" />
            {t('loginWithGitHub')}
          </Button>

          <div className="relative">
            <Separator />
            <span className="absolute left-1/2 -translate-x-1/2 -translate-y-1/2 bg-background px-2 text-sm text-muted-foreground">
              {t('orContinueWith')}
            </span>
          </div>

          {/* 기존 로컬 로그인 폼 */}
          <form onSubmit={handleLocalLogin}>
            {/* ... 기존 코드 유지 ... */}
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
```

### 2. OAuth 콜백 페이지

**신규 파일**: `frontend/app/auth/callback/page.tsx`

```tsx
'use client';

import { useEffect, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { Loader2, CheckCircle, XCircle } from 'lucide-react';
import { useAuthStore } from '@/lib/store';

export default function OAuthCallbackPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { setAuth } = useAuthStore();
  const [status, setStatus] = useState<'loading' | 'success' | 'error'>('loading');
  const [errorMessage, setErrorMessage] = useState<string>('');

  useEffect(() => {
    const token = searchParams.get('token');
    const error = searchParams.get('error');

    if (error) {
      setStatus('error');
      setErrorMessage(error);
      return;
    }

    if (token) {
      // 토큰 저장
      localStorage.setItem('docst-token', token);

      // 사용자 정보 조회
      fetch(`${API_BASE}/api/auth/me`, {
        headers: { Authorization: `Bearer ${token}` }
      })
        .then(res => res.json())
        .then(user => {
          setAuth(user, token);
          setStatus('success');
          // 대시보드로 리다이렉트
          setTimeout(() => router.push('/'), 1500);
        })
        .catch(err => {
          setStatus('error');
          setErrorMessage('Failed to fetch user info');
        });
    } else {
      setStatus('error');
      setErrorMessage('No token received');
    }
  }, [searchParams, router, setAuth]);

  return (
    <div className="min-h-screen flex items-center justify-center">
      <div className="text-center">
        {status === 'loading' && (
          <>
            <Loader2 className="w-12 h-12 mx-auto animate-spin text-primary" />
            <p className="mt-4 text-muted-foreground">Authenticating...</p>
          </>
        )}

        {status === 'success' && (
          <>
            <CheckCircle className="w-12 h-12 mx-auto text-green-500" />
            <p className="mt-4 text-green-600">Login successful! Redirecting...</p>
          </>
        )}

        {status === 'error' && (
          <>
            <XCircle className="w-12 h-12 mx-auto text-red-500" />
            <p className="mt-4 text-red-600">{errorMessage}</p>
            <Button
              className="mt-4"
              onClick={() => router.push('/login')}
            >
              Back to Login
            </Button>
          </>
        )}
      </div>
    </div>
  );
}
```

### 3. 다국어 메시지 추가

**수정 파일**: `frontend/messages/en.json`

```json
{
  "auth": {
    "loginTitle": "Welcome to Docst",
    "loginWithGitHub": "Continue with GitHub",
    "orContinueWith": "or continue with email",
    "email": "Email",
    "password": "Password",
    "loginButton": "Sign In",
    "oauthSuccess": "Login successful! Redirecting...",
    "oauthError": "Authentication failed",
    "oauthLoading": "Authenticating..."
  }
}
```

### 4. 작업 목록 (Phase 3-B)

- [ ] 로그인 페이지에 GitHub 버튼 추가
- [ ] OAuth 콜백 페이지 생성
- [ ] Separator 컴포넌트 추가 (shadcn/ui)
- [ ] 다국어 메시지 추가
- [ ] 에러 처리 UI 개선
- [ ] 로그인 후 원래 페이지로 리다이렉트 (returnUrl)

---

## Phase 3-D: 문서 관계 그래프

### 1. 의존성 추가

**수정 파일**: `frontend/package.json`

```json
{
  "dependencies": {
    "react-force-graph-2d": "^1.25.0",
    "@types/react-force-graph-2d": "^1.0.2"
  }
}
```

### 2. 그래프 페이지

**신규 파일**: `frontend/app/[locale]/projects/[projectId]/graph/page.tsx`

```tsx
'use client';

import { useParams } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { useDocumentGraph } from '@/hooks/use-api';
import { DocumentGraph } from '@/components/document-graph';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Loader2 } from 'lucide-react';

export default function GraphPage() {
  const { projectId } = useParams<{ projectId: string }>();
  const t = useTranslations('graph');

  const { data: graphData, isLoading, error } = useDocumentGraph(projectId);

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <Loader2 className="w-8 h-8 animate-spin" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="text-center text-red-500">
        {t('error')}
      </div>
    );
  }

  return (
    <div className="container py-6">
      <Card>
        <CardHeader>
          <CardTitle>{t('title')}</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="h-[600px] border rounded-lg overflow-hidden">
            <DocumentGraph data={graphData} />
          </div>
        </CardContent>
      </Card>

      {/* 범례 */}
      <div className="mt-4 flex gap-4 text-sm">
        <div className="flex items-center gap-2">
          <div className="w-3 h-3 rounded-full bg-blue-500" />
          <span>{t('legendDocument')}</span>
        </div>
        <div className="flex items-center gap-2">
          <div className="w-8 h-0.5 bg-gray-400" />
          <span>{t('legendLink')}</span>
        </div>
        <div className="flex items-center gap-2">
          <div className="w-8 h-0.5 bg-red-400 border-dashed" />
          <span>{t('legendBrokenLink')}</span>
        </div>
      </div>
    </div>
  );
}
```

### 3. 그래프 컴포넌트

**신규 파일**: `frontend/components/document-graph.tsx`

```tsx
'use client';

import { useCallback, useMemo, useRef } from 'react';
import ForceGraph2D from 'react-force-graph-2d';
import { useRouter } from 'next/navigation';

interface GraphNode {
  id: string;
  name: string;
  path: string;
  type: 'document' | 'external';
}

interface GraphLink {
  source: string;
  target: string;
  type: 'REFERENCES' | 'IMPORTS' | 'EXTENDS';
  broken: boolean;
}

interface DocumentGraphProps {
  data: {
    nodes: GraphNode[];
    links: GraphLink[];
  };
}

export function DocumentGraph({ data }: DocumentGraphProps) {
  const router = useRouter();
  const graphRef = useRef<any>();

  const graphData = useMemo(() => ({
    nodes: data.nodes.map(node => ({
      ...node,
      color: node.type === 'document' ? '#3b82f6' : '#9ca3af',
      size: node.type === 'document' ? 8 : 4,
    })),
    links: data.links.map(link => ({
      ...link,
      color: link.broken ? '#ef4444' : '#6b7280',
      dashed: link.broken,
    })),
  }), [data]);

  const handleNodeClick = useCallback((node: GraphNode) => {
    if (node.type === 'document') {
      router.push(`/documents/${node.id}`);
    }
  }, [router]);

  const paintNode = useCallback((node: any, ctx: CanvasRenderingContext2D) => {
    ctx.beginPath();
    ctx.arc(node.x, node.y, node.size, 0, 2 * Math.PI);
    ctx.fillStyle = node.color;
    ctx.fill();

    // 라벨
    ctx.font = '4px Sans-Serif';
    ctx.textAlign = 'center';
    ctx.fillStyle = '#374151';
    ctx.fillText(node.name, node.x, node.y + node.size + 4);
  }, []);

  return (
    <ForceGraph2D
      ref={graphRef}
      graphData={graphData}
      nodeCanvasObject={paintNode}
      onNodeClick={handleNodeClick}
      linkColor={(link: any) => link.color}
      linkLineDash={(link: any) => link.dashed ? [2, 2] : null}
      linkDirectionalArrowLength={3}
      linkDirectionalArrowRelPos={1}
      enableZoomInteraction={true}
      enablePanInteraction={true}
      cooldownTicks={100}
    />
  );
}
```

### 4. API Hook 추가

**수정 파일**: `frontend/hooks/use-api.ts`

```typescript
// 문서 그래프 조회
export function useDocumentGraph(projectId: string, options?: { depth?: number }) {
  return useQuery({
    queryKey: queryKeys.documentGraph(projectId, options?.depth),
    queryFn: () => graphApi.getGraph(projectId, options),
    enabled: !!projectId,
  });
}

// 영향 분석
export function useImpactAnalysis(documentId: string, maxDepth?: number) {
  return useQuery({
    queryKey: queryKeys.impactAnalysis(documentId, maxDepth),
    queryFn: () => graphApi.analyzeImpact(documentId, maxDepth),
    enabled: !!documentId,
  });
}
```

**수정 파일**: `frontend/lib/api.ts`

```typescript
export const graphApi = {
  getGraph: (projectId: string, options?: { depth?: number }) =>
    request<GraphData>(`/api/projects/${projectId}/graph?depth=${options?.depth || 2}`),

  getDocumentGraph: (documentId: string, depth?: number) =>
    request<GraphData>(`/api/documents/${documentId}/graph?depth=${depth || 2}`),

  analyzeImpact: (documentId: string, maxDepth?: number) =>
    request<ImpactAnalysis>(`/api/documents/${documentId}/impact?maxDepth=${maxDepth || 3}`),
};
```

### 5. 타입 정의 추가

**수정 파일**: `frontend/lib/types.ts`

```typescript
// 문서 관계 그래프
export interface GraphNode {
  id: string;
  name: string;
  path: string;
  type: 'document' | 'external';
  docType?: DocType;
}

export interface GraphLink {
  source: string;
  target: string;
  type: 'REFERENCES' | 'IMPORTS' | 'EXTENDS';
  linkText?: string;
  broken: boolean;
}

export interface GraphData {
  nodes: GraphNode[];
  links: GraphLink[];
}

// 영향 분석
export interface ImpactAnalysis {
  documentId: string;
  documentPath: string;
  affectedDocuments: {
    id: string;
    path: string;
    distance: number;
    relationType: string;
  }[];
  totalAffected: number;
}
```

### 6. 다국어 메시지 추가

```json
// en.json
{
  "graph": {
    "title": "Document Relationships",
    "description": "Visualize document links and dependencies",
    "legendDocument": "Document",
    "legendLink": "Link",
    "legendBrokenLink": "Broken Link",
    "noData": "No document relationships found",
    "error": "Failed to load graph data",
    "depth": "Depth",
    "zoomIn": "Zoom In",
    "zoomOut": "Zoom Out",
    "resetView": "Reset View"
  }
}

// ko.json
{
  "graph": {
    "title": "문서 관계도",
    "description": "문서 간 링크 및 의존성 시각화",
    "legendDocument": "문서",
    "legendLink": "링크",
    "legendBrokenLink": "끊어진 링크",
    "noData": "문서 관계가 없습니다",
    "error": "그래프 데이터를 불러오는데 실패했습니다",
    "depth": "깊이",
    "zoomIn": "확대",
    "zoomOut": "축소",
    "resetView": "뷰 초기화"
  }
}
```

### 7. 작업 목록 (Phase 3-D)

- [ ] react-force-graph-2d 의존성 추가
- [ ] 그래프 페이지 생성
- [ ] DocumentGraph 컴포넌트 생성
- [ ] API hooks 추가 (useDocumentGraph, useImpactAnalysis)
- [ ] 타입 정의 추가
- [ ] 다국어 메시지 추가
- [ ] 사이드바에 그래프 메뉴 추가
- [ ] 문서 상세 페이지에 "관계 보기" 버튼 추가
- [ ] 영향 분석 모달/패널 구현

---

## Phase 3: 프로젝트 설정 & 멤버 관리

### 1. 프로젝트 설정 페이지

**신규 파일**: `frontend/app/[locale]/projects/[projectId]/settings/page.tsx`

```tsx
'use client';

import { useParams } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { Link } from 'next/navigation';
import { Settings, Users, Shield, Bell } from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';

export default function SettingsPage() {
  const { projectId } = useParams<{ projectId: string }>();
  const t = useTranslations('settings');

  const settingsItems = [
    {
      icon: Settings,
      title: t('general'),
      description: t('generalDescription'),
      href: `/projects/${projectId}/settings/general`,
    },
    {
      icon: Users,
      title: t('members'),
      description: t('membersDescription'),
      href: `/projects/${projectId}/settings/members`,
    },
    {
      icon: Shield,
      title: t('permissions'),
      description: t('permissionsDescription'),
      href: `/projects/${projectId}/settings/permissions`,
    },
    {
      icon: Bell,
      title: t('notifications'),
      description: t('notificationsDescription'),
      href: `/projects/${projectId}/settings/notifications`,
    },
  ];

  return (
    <div className="container py-6">
      <h1 className="text-2xl font-bold mb-6">{t('title')}</h1>

      <div className="grid gap-4 md:grid-cols-2">
        {settingsItems.map((item) => (
          <Link key={item.href} href={item.href}>
            <Card className="hover:shadow-md transition-shadow cursor-pointer">
              <CardHeader className="flex flex-row items-center gap-4">
                <item.icon className="w-8 h-8 text-muted-foreground" />
                <div>
                  <CardTitle className="text-lg">{item.title}</CardTitle>
                  <CardDescription>{item.description}</CardDescription>
                </div>
              </CardHeader>
            </Card>
          </Link>
        ))}
      </div>
    </div>
  );
}
```

### 2. 멤버 관리 페이지

**신규 파일**: `frontend/app/[locale]/projects/[projectId]/settings/members/page.tsx`

```tsx
'use client';

import { useState } from 'react';
import { useParams } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { Plus, Trash2, Shield } from 'lucide-react';
import { useProjectMembers, useAddMember, useRemoveMember, useUpdateMemberRole } from '@/hooks/use-api';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Input } from '@/components/ui/input';

export default function MembersPage() {
  const { projectId } = useParams<{ projectId: string }>();
  const t = useTranslations('members');

  const { data: members, isLoading } = useProjectMembers(projectId);
  const addMember = useAddMember(projectId);
  const removeMember = useRemoveMember(projectId);
  const updateRole = useUpdateMemberRole(projectId);

  const [showInviteDialog, setShowInviteDialog] = useState(false);
  const [inviteEmail, setInviteEmail] = useState('');
  const [inviteRole, setInviteRole] = useState<'VIEWER' | 'EDITOR' | 'ADMIN'>('VIEWER');

  const handleInvite = async () => {
    await addMember.mutateAsync({ email: inviteEmail, role: inviteRole });
    setShowInviteDialog(false);
    setInviteEmail('');
  };

  const handleRoleChange = async (memberId: string, newRole: string) => {
    await updateRole.mutateAsync({ memberId, role: newRole });
  };

  const handleRemove = async (memberId: string) => {
    if (confirm(t('confirmRemove'))) {
      await removeMember.mutateAsync(memberId);
    }
  };

  const roleColors = {
    OWNER: 'bg-purple-100 text-purple-800',
    ADMIN: 'bg-red-100 text-red-800',
    EDITOR: 'bg-blue-100 text-blue-800',
    VIEWER: 'bg-gray-100 text-gray-800',
  };

  return (
    <div className="container py-6">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">{t('title')}</h1>

        <Dialog open={showInviteDialog} onOpenChange={setShowInviteDialog}>
          <DialogTrigger asChild>
            <Button>
              <Plus className="w-4 h-4 mr-2" />
              {t('inviteMember')}
            </Button>
          </DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>{t('inviteMember')}</DialogTitle>
            </DialogHeader>
            <div className="space-y-4">
              <Input
                placeholder={t('emailPlaceholder')}
                value={inviteEmail}
                onChange={(e) => setInviteEmail(e.target.value)}
              />
              <Select value={inviteRole} onValueChange={(v: any) => setInviteRole(v)}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="VIEWER">{t('roleViewer')}</SelectItem>
                  <SelectItem value="EDITOR">{t('roleEditor')}</SelectItem>
                  <SelectItem value="ADMIN">{t('roleAdmin')}</SelectItem>
                </SelectContent>
              </Select>
              <Button onClick={handleInvite} className="w-full">
                {t('sendInvite')}
              </Button>
            </div>
          </DialogContent>
        </Dialog>
      </div>

      <Card>
        <CardContent className="p-0">
          <table className="w-full">
            <thead>
              <tr className="border-b">
                <th className="text-left p-4">{t('member')}</th>
                <th className="text-left p-4">{t('role')}</th>
                <th className="text-left p-4">{t('joinedAt')}</th>
                <th className="p-4"></th>
              </tr>
            </thead>
            <tbody>
              {members?.map((member) => (
                <tr key={member.id} className="border-b last:border-0">
                  <td className="p-4">
                    <div className="flex items-center gap-3">
                      <div className="w-8 h-8 rounded-full bg-muted flex items-center justify-center">
                        {member.user.displayName?.[0] || member.user.email[0]}
                      </div>
                      <div>
                        <div className="font-medium">{member.user.displayName}</div>
                        <div className="text-sm text-muted-foreground">{member.user.email}</div>
                      </div>
                    </div>
                  </td>
                  <td className="p-4">
                    {member.role === 'OWNER' ? (
                      <Badge className={roleColors.OWNER}>
                        <Shield className="w-3 h-3 mr-1" />
                        {t('roleOwner')}
                      </Badge>
                    ) : (
                      <Select
                        value={member.role}
                        onValueChange={(v) => handleRoleChange(member.id, v)}
                      >
                        <SelectTrigger className="w-32">
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="VIEWER">{t('roleViewer')}</SelectItem>
                          <SelectItem value="EDITOR">{t('roleEditor')}</SelectItem>
                          <SelectItem value="ADMIN">{t('roleAdmin')}</SelectItem>
                        </SelectContent>
                      </Select>
                    )}
                  </td>
                  <td className="p-4 text-muted-foreground">
                    {new Date(member.createdAt).toLocaleDateString()}
                  </td>
                  <td className="p-4">
                    {member.role !== 'OWNER' && (
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => handleRemove(member.id)}
                      >
                        <Trash2 className="w-4 h-4 text-red-500" />
                      </Button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </CardContent>
      </Card>
    </div>
  );
}
```

### 3. 타입 정의 추가

**수정 파일**: `frontend/lib/types.ts`

```typescript
// 프로젝트 멤버
export interface ProjectMember {
  id: string;
  projectId: string;
  user: {
    id: string;
    email: string;
    displayName: string;
    avatarUrl?: string;
  };
  role: 'OWNER' | 'ADMIN' | 'EDITOR' | 'VIEWER';
  createdAt: string;
}

export interface AddMemberRequest {
  email: string;
  role: 'ADMIN' | 'EDITOR' | 'VIEWER';
}

export interface UpdateMemberRoleRequest {
  memberId: string;
  role: string;
}
```

### 4. 다국어 메시지 추가

```json
// en.json
{
  "settings": {
    "title": "Project Settings",
    "general": "General",
    "generalDescription": "Basic project settings",
    "members": "Members",
    "membersDescription": "Manage team members and permissions",
    "permissions": "Permissions",
    "permissionsDescription": "Configure access control",
    "notifications": "Notifications",
    "notificationsDescription": "Configure notification preferences"
  },
  "members": {
    "title": "Team Members",
    "inviteMember": "Invite Member",
    "emailPlaceholder": "Enter email address",
    "sendInvite": "Send Invitation",
    "member": "Member",
    "role": "Role",
    "joinedAt": "Joined",
    "roleOwner": "Owner",
    "roleAdmin": "Admin",
    "roleEditor": "Editor",
    "roleViewer": "Viewer",
    "confirmRemove": "Are you sure you want to remove this member?"
  }
}

// ko.json
{
  "settings": {
    "title": "프로젝트 설정",
    "general": "일반",
    "generalDescription": "기본 프로젝트 설정",
    "members": "멤버",
    "membersDescription": "팀 멤버 및 권한 관리",
    "permissions": "권한",
    "permissionsDescription": "접근 제어 설정",
    "notifications": "알림",
    "notificationsDescription": "알림 설정"
  },
  "members": {
    "title": "팀 멤버",
    "inviteMember": "멤버 초대",
    "emailPlaceholder": "이메일 주소 입력",
    "sendInvite": "초대 보내기",
    "member": "멤버",
    "role": "역할",
    "joinedAt": "참여일",
    "roleOwner": "소유자",
    "roleAdmin": "관리자",
    "roleEditor": "편집자",
    "roleViewer": "뷰어",
    "confirmRemove": "이 멤버를 제거하시겠습니까?"
  }
}
```

### 5. 작업 목록 (Phase 3)

- [ ] 프로젝트 설정 페이지 생성
- [ ] 멤버 관리 페이지 생성
- [ ] Select 컴포넌트 추가 (shadcn/ui)
- [ ] 멤버 관련 API hooks 추가
- [ ] 타입 정의 추가
- [ ] 다국어 메시지 추가
- [ ] 사이드바에 설정 링크 추가
- [ ] 권한 기반 UI 표시/숨김 처리

---

## 사이드바 메뉴 추가

**수정 파일**: `frontend/components/sidebar.tsx`

```tsx
// 프로젝트별 메뉴에 추가
{selectedProject && (
  <div className="mt-4 space-y-1">
    <Link href={`/projects/${selectedProject.id}/search`}>
      <Button variant="ghost" className="w-full justify-start">
        <Search className="w-4 h-4 mr-2" />
        {t('search')}
      </Button>
    </Link>
    <Link href={`/projects/${selectedProject.id}/graph`}>
      <Button variant="ghost" className="w-full justify-start">
        <Network className="w-4 h-4 mr-2" />
        {t('documentGraph')}
      </Button>
    </Link>
    <Link href={`/projects/${selectedProject.id}/settings`}>
      <Button variant="ghost" className="w-full justify-start">
        <Settings className="w-4 h-4 mr-2" />
        {t('settings')}
      </Button>
    </Link>
  </div>
)}
```

---

## 신규 컴포넌트 요약

| 컴포넌트 | Phase | 위치 |
|---------|-------|------|
| `SearchModeSelect` | 2-C | `components/search-mode-select.tsx` |
| `SearchResultCard` | 2-C | `components/search-result-card.tsx` |
| `DocumentGraph` | 3-D | `components/document-graph.tsx` |
| `MemberList` | 3 | `components/member-list.tsx` |
| `InviteMemberDialog` | 3 | `components/invite-member-dialog.tsx` |

---

## 신규 페이지 요약

| 페이지 | Phase | 위치 |
|-------|-------|------|
| OAuth Callback | 3-B | `app/auth/callback/page.tsx` |
| Document Graph | 3-D | `app/[locale]/projects/[projectId]/graph/page.tsx` |
| Project Settings | 3 | `app/[locale]/projects/[projectId]/settings/page.tsx` |
| Members | 3 | `app/[locale]/projects/[projectId]/settings/members/page.tsx` |

---

## 신규 의존성 요약

```json
{
  "dependencies": {
    "react-force-graph-2d": "^1.25.0"
  },
  "devDependencies": {
    "@types/react-force-graph-2d": "^1.0.2"
  }
}
```

---

## 전체 작업 목록

### Phase 2-C: 의미/하이브리드 검색 UI
- [ ] `search-mode-select.tsx` 컴포넌트 생성
- [ ] `search-result-card.tsx` 컴포넌트 생성
- [ ] 검색 페이지 mode 상태 추가
- [ ] `types.ts`에 SearchResult 확장
- [ ] 다국어 메시지 추가

### Phase 3-B: GitHub OAuth 프론트엔드
- [ ] 로그인 페이지에 GitHub 버튼 추가
- [ ] OAuth 콜백 페이지 생성
- [ ] Separator 컴포넌트 추가
- [ ] 다국어 메시지 추가

### Phase 3-D: 문서 관계 그래프
- [ ] react-force-graph-2d 의존성 추가
- [ ] 그래프 페이지 생성
- [ ] DocumentGraph 컴포넌트 생성
- [ ] API hooks 추가
- [ ] 다국어 메시지 추가

### Phase 3: 프로젝트 설정 & 멤버 관리
- [ ] 프로젝트 설정 페이지 생성
- [ ] 멤버 관리 페이지 생성
- [ ] Select 컴포넌트 추가
- [ ] 멤버 관련 API hooks 추가
- [ ] 다국어 메시지 추가

### 공통
- [ ] 사이드바 메뉴 업데이트
- [ ] 권한 기반 UI 처리
