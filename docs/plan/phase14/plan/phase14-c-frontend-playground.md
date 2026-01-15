# Phase 14-C: Frontend Playground 고도화

## 목표

Playground에서 시맨틱 서치 파라미터를 실시간으로 조정하고 결과를 확인할 수 있는 UI를 제공한다.

## 변경 파일

| 파일 | 변경 내용 |
|-----|---------|
| `playground/page.tsx` | 레이아웃 변경, 사이드바 통합 |
| `search-params-panel.tsx` | 신규 - 검색 파라미터 패널 컴포넌트 |
| `search-preview.tsx` | 신규 - 실시간 검색 미리보기 컴포넌트 |
| `lib/types.ts` | SearchParams 인터페이스 추가 |
| `hooks/use-llm-chat.ts` | 검색 파라미터 전달 로직 |

## UI 레이아웃

### 변경 전
```
┌─────────────────────────────────────────────────────────┐
│  Header + Project Selector                              │
├─────────────────────────────────┬──────────────────────┤
│                                 │ Quick Tips           │
│      Chat Interface             │ - Example Questions  │
│                                 │ - Available Tools    │
└─────────────────────────────────┴──────────────────────┘
```

### 변경 후 (좌측 사이드바)
```
┌─────────────────────────────────────────────────────────┐
│  Header + Project Selector                              │
├───────────────┬─────────────────────┬──────────────────┤
│ Search Params │                     │ Quick Tips       │
│ Sidebar       │                     │                  │
│ ────────────  │   Chat Interface    │ - Example Qs     │
│ Mode: ○○○○    │                     │ - Available      │
│ Threshold:    │                     │   Tools          │
│ [====●===]    │                     │                  │
│ TopK: [10]    │                     ├──────────────────┤
│ ────────────  │                     │ Search Preview   │
│ Hybrid Config │                     │                  │
│ Strategy:     │                     │ - Live results   │
│ [RRF ▼]       │                     │ - Score display  │
│ ────────────  │                     │                  │
│ Presets:      │                     │                  │
│ [Balanced ▼]  │                     │                  │
└───────────────┴─────────────────────┴──────────────────┘
```

## 구현 내용

### 1. 검색 파라미터 패널 컴포넌트

**frontend/components/playground/search-params-panel.tsx**:

```typescript
'use client';

import { useState, useCallback } from 'react';
import { Slider } from '@/components/ui/slider';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';

export interface SearchParams {
  mode: 'keyword' | 'semantic' | 'graph' | 'hybrid';
  similarityThreshold: number;
  topK: number;
  fusionStrategy: 'rrf' | 'weighted_sum';
  rrfK: number;
  vectorWeight: number;
}

interface SearchParamsPanelProps {
  params: SearchParams;
  onChange: (params: SearchParams) => void;
  onPreview?: (query: string) => void;
}

const PRESETS = {
  balanced: { threshold: 0.3, topK: 10, fusionStrategy: 'rrf' as const },
  precision: { threshold: 0.5, topK: 5, fusionStrategy: 'rrf' as const },
  recall: { threshold: 0.2, topK: 20, fusionStrategy: 'rrf' as const },
};

export function SearchParamsPanel({ params, onChange, onPreview }: SearchParamsPanelProps) {
  const [preset, setPreset] = useState<'balanced' | 'precision' | 'recall' | 'custom'>('balanced');

  const handlePresetChange = (value: string) => {
    if (value === 'custom') {
      setPreset('custom');
      return;
    }
    const presetValue = PRESETS[value as keyof typeof PRESETS];
    setPreset(value as any);
    onChange({
      ...params,
      similarityThreshold: presetValue.threshold,
      topK: presetValue.topK,
      fusionStrategy: presetValue.fusionStrategy,
    });
  };

  return (
    <Card className="w-64">
      <CardHeader className="pb-3">
        <CardTitle className="text-sm flex items-center gap-2">
          검색 파라미터
          <Badge variant="outline" className="text-xs">Phase 14</Badge>
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {/* 프리셋 선택 */}
        <div className="space-y-2">
          <Label className="text-xs">프리셋</Label>
          <Select value={preset} onValueChange={handlePresetChange}>
            <SelectTrigger className="h-8">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="balanced">Balanced (균형)</SelectItem>
              <SelectItem value="precision">Precision (정확도)</SelectItem>
              <SelectItem value="recall">Recall (재현율)</SelectItem>
              <SelectItem value="custom">Custom (사용자 정의)</SelectItem>
            </SelectContent>
          </Select>
        </div>

        {/* 검색 모드 */}
        <div className="space-y-2">
          <Label className="text-xs">검색 모드</Label>
          <Select
            value={params.mode}
            onValueChange={(v) => onChange({ ...params, mode: v as any })}
          >
            <SelectTrigger className="h-8">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="semantic">Semantic (의미)</SelectItem>
              <SelectItem value="keyword">Keyword (키워드)</SelectItem>
              <SelectItem value="graph">Graph (그래프)</SelectItem>
              <SelectItem value="hybrid">Hybrid (하이브리드)</SelectItem>
            </SelectContent>
          </Select>
        </div>

        {/* Similarity Threshold */}
        {(params.mode === 'semantic' || params.mode === 'hybrid') && (
          <div className="space-y-2">
            <div className="flex justify-between">
              <Label className="text-xs">유사도 임계값</Label>
              <span className="text-xs text-muted-foreground">
                {params.similarityThreshold.toFixed(2)}
              </span>
            </div>
            <Slider
              value={[params.similarityThreshold]}
              min={0}
              max={1}
              step={0.05}
              onValueChange={([v]) => {
                setPreset('custom');
                onChange({ ...params, similarityThreshold: v });
              }}
            />
            <p className="text-xs text-muted-foreground">
              낮을수록 더 많은 결과 포함
            </p>
          </div>
        )}

        {/* TopK */}
        <div className="space-y-2">
          <Label className="text-xs">결과 개수 (topK)</Label>
          <Input
            type="number"
            min={1}
            max={50}
            value={params.topK}
            onChange={(e) => {
              setPreset('custom');
              onChange({ ...params, topK: parseInt(e.target.value) || 10 });
            }}
            className="h-8"
          />
        </div>

        {/* Hybrid 전용 설정 */}
        {params.mode === 'hybrid' && (
          <>
            <div className="border-t pt-4 space-y-2">
              <Label className="text-xs font-medium">하이브리드 설정</Label>
            </div>

            {/* Fusion Strategy */}
            <div className="space-y-2">
              <Label className="text-xs">융합 전략</Label>
              <Select
                value={params.fusionStrategy}
                onValueChange={(v) => onChange({ ...params, fusionStrategy: v as any })}
              >
                <SelectTrigger className="h-8">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="rrf">RRF (Reciprocal Rank Fusion)</SelectItem>
                  <SelectItem value="weighted_sum">Weighted Sum</SelectItem>
                </SelectContent>
              </Select>
            </div>

            {/* RRF K */}
            {params.fusionStrategy === 'rrf' && (
              <div className="space-y-2">
                <div className="flex justify-between">
                  <Label className="text-xs">RRF K</Label>
                  <span className="text-xs text-muted-foreground">{params.rrfK}</span>
                </div>
                <Slider
                  value={[params.rrfK]}
                  min={1}
                  max={100}
                  step={1}
                  onValueChange={([v]) => onChange({ ...params, rrfK: v })}
                />
              </div>
            )}

            {/* Vector Weight */}
            {params.fusionStrategy === 'weighted_sum' && (
              <div className="space-y-2">
                <div className="flex justify-between">
                  <Label className="text-xs">벡터 가중치</Label>
                  <span className="text-xs text-muted-foreground">
                    {params.vectorWeight.toFixed(1)} / {(1 - params.vectorWeight).toFixed(1)}
                  </span>
                </div>
                <Slider
                  value={[params.vectorWeight]}
                  min={0}
                  max={1}
                  step={0.1}
                  onValueChange={([v]) => onChange({ ...params, vectorWeight: v })}
                />
                <div className="flex text-xs text-muted-foreground justify-between">
                  <span>Keyword</span>
                  <span>Vector</span>
                </div>
              </div>
            )}
          </>
        )}
      </CardContent>
    </Card>
  );
}
```

### 2. 검색 프리셋

| 프리셋 | threshold | topK | fusionStrategy | 사용 시나리오 |
|--------|-----------|------|----------------|-------------|
| Balanced | 0.3 | 10 | rrf | 일반적인 검색 |
| Precision | 0.5 | 5 | rrf | 정확한 결과만 필요할 때 |
| Recall | 0.2 | 20 | rrf | 관련 문서를 최대한 찾을 때 |
| Custom | - | - | - | 사용자 정의 |

### 3. 실시간 검색 미리보기 컴포넌트

**frontend/components/playground/search-preview.tsx**:

```typescript
'use client';

import { useState, useEffect, useCallback } from 'react';
import { useDebounce } from '@/hooks/use-debounce';
import { SearchResult } from '@/lib/types';
import { SearchResultCard } from '@/components/search-result-card';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Loader2, Search } from 'lucide-react';
import type { SearchParams } from './search-params-panel';

interface SearchPreviewProps {
  projectId: string;
  params: SearchParams;
}

export function SearchPreview({ projectId, params }: SearchPreviewProps) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<SearchResult[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [queryTimeMs, setQueryTimeMs] = useState<number | null>(null);

  const debouncedQuery = useDebounce(query, 300);

  const executeSearch = useCallback(async () => {
    if (!debouncedQuery.trim() || !projectId) return;

    setIsLoading(true);
    try {
      const searchParams = new URLSearchParams({
        q: debouncedQuery,
        mode: params.mode,
        topK: params.topK.toString(),
        ...(params.similarityThreshold && { similarityThreshold: params.similarityThreshold.toString() }),
        ...(params.mode === 'hybrid' && { fusionStrategy: params.fusionStrategy }),
        ...(params.fusionStrategy === 'rrf' && { rrfK: params.rrfK.toString() }),
        ...(params.fusionStrategy === 'weighted_sum' && { vectorWeight: params.vectorWeight.toString() }),
      });

      const response = await fetch(
        `/api/projects/${projectId}/search?${searchParams}`
      );
      const data = await response.json();

      setResults(data.results || []);
      setQueryTimeMs(data.metadata?.queryTimeMs || null);
    } catch (error) {
      console.error('Search preview failed:', error);
      setResults([]);
    } finally {
      setIsLoading(false);
    }
  }, [debouncedQuery, projectId, params]);

  useEffect(() => {
    executeSearch();
  }, [executeSearch]);

  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle className="text-sm flex items-center gap-2">
          <Search className="h-4 w-4" />
          검색 미리보기
          {queryTimeMs !== null && (
            <span className="text-xs text-muted-foreground ml-auto">
              {queryTimeMs}ms
            </span>
          )}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        <Input
          placeholder="검색어 입력..."
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          className="h-8"
        />

        {isLoading ? (
          <div className="flex items-center justify-center py-4">
            <Loader2 className="h-4 w-4 animate-spin" />
          </div>
        ) : results.length > 0 ? (
          <div className="space-y-2 max-h-64 overflow-y-auto">
            {results.slice(0, 5).map((result, idx) => (
              <SearchResultCard key={result.documentId + idx} result={result} compact />
            ))}
            {results.length > 5 && (
              <p className="text-xs text-muted-foreground text-center">
                +{results.length - 5} more results
              </p>
            )}
          </div>
        ) : query ? (
          <p className="text-xs text-muted-foreground text-center py-4">
            검색 결과 없음
          </p>
        ) : (
          <p className="text-xs text-muted-foreground text-center py-4">
            검색어를 입력하세요
          </p>
        )}
      </CardContent>
    </Card>
  );
}
```

### 4. 타입 정의 추가

**frontend/lib/types.ts**:

```typescript
// 기존 SearchResult에 추가
export interface SearchMetadata {
  mode: string;
  totalResults: number;
  similarityThreshold: number;
  fusionStrategy: string | null;
  queryTimeMs: number;
}

export interface SearchResponse {
  results: SearchResult[];
  metadata: SearchMetadata;
}

// SearchParams (playground용)
export interface SearchParams {
  mode: 'keyword' | 'semantic' | 'graph' | 'hybrid';
  similarityThreshold: number;
  topK: number;
  fusionStrategy: 'rrf' | 'weighted_sum';
  rrfK: number;
  vectorWeight: number;
}
```

### 5. Playground 페이지 수정

**frontend/app/[locale]/playground/page.tsx**:

```typescript
// 상단 import 추가
import { SearchParamsPanel, SearchParams } from '@/components/playground/search-params-panel';
import { SearchPreview } from '@/components/playground/search-preview';

// 상태 추가
const [searchParams, setSearchParams] = useState<SearchParams>({
  mode: 'semantic',
  similarityThreshold: 0.3,
  topK: 10,
  fusionStrategy: 'rrf',
  rrfK: 60,
  vectorWeight: 0.6,
});

// 레이아웃 변경
<div className="flex h-[calc(100vh-4rem)]">
  {/* 좌측 사이드바 - 검색 파라미터 */}
  <aside className="w-64 border-r p-4 overflow-y-auto">
    <SearchParamsPanel
      params={searchParams}
      onChange={setSearchParams}
    />
  </aside>

  {/* 중앙 - 채팅 */}
  <main className="flex-1">
    <ChatInterface
      projectId={selectedProjectId}
      searchParams={searchParams}
    />
  </main>

  {/* 우측 사이드바 - Quick Tips + 검색 미리보기 */}
  <aside className="w-72 border-l p-4 space-y-4 overflow-y-auto">
    <QuickTips />
    {selectedProjectId && (
      <SearchPreview
        projectId={selectedProjectId}
        params={searchParams}
      />
    )}
  </aside>
</div>
```

## 검증 방법

1. **Playground 접속**: `http://localhost:3000/playground`
2. **프로젝트 선택**: 상단 드롭다운에서 프로젝트 선택
3. **파라미터 조정**:
   - 좌측 사이드바에서 검색 모드 변경
   - 유사도 임계값 슬라이더 조정
   - topK 값 변경
4. **실시간 미리보기**:
   - 우측 하단 검색 미리보기에서 검색어 입력
   - 파라미터 변경 시 결과 자동 갱신 확인
5. **프리셋 테스트**:
   - Balanced, Precision, Recall 프리셋 선택
   - 파라미터 자동 설정 확인

## 구현 순서

1. `lib/types.ts`에 SearchParams, SearchMetadata 타입 추가
2. `search-params-panel.tsx` 컴포넌트 생성
3. `search-preview.tsx` 컴포넌트 생성
4. `playground/page.tsx` 레이아웃 수정 및 컴포넌트 통합
5. `use-llm-chat.ts`에 searchParams 전달 로직 추가
6. i18n 번역 키 추가
7. E2E 테스트 작성
