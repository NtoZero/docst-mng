# Phase 4-D: RAG 설정 UI 구현 계획

> **작성일**: 2025-12-30
> **기반**: Phase 4-D 백엔드 API 완료
> **목표**: 프로젝트별 RAG 설정 관리 UI 구현

---

## 개요

Phase 4-D에서 구현한 RAG 설정 API를 기반으로 프론트엔드 UI를 구현한다.
사용자가 프로젝트별로 임베딩 모델, Neo4j, Hybrid 검색 설정을 관리할 수 있도록 한다.

---

## 백엔드 API 명세 (구현 완료)

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/projects/{id}/rag-config` | 프로젝트 RAG 설정 조회 |
| PUT | `/api/projects/{id}/rag-config` | 설정 업데이트 |
| POST | `/api/projects/{id}/rag-config/validate` | 설정 검증 |
| GET | `/api/projects/{id}/rag-config/defaults` | 전역 기본값 조회 |
| POST | `/api/projects/{id}/rag-config/re-embed` | 재임베딩 트리거 |
| GET | `/api/projects/{id}/rag-config/re-embed/status` | 재임베딩 상태 조회 |

---

## UI 구조

```
app/[locale]/projects/[projectId]/settings/
├── page.tsx                    # 설정 메인 (기존 또는 신규)
└── rag/
    └── page.tsx                # RAG 설정 페이지 (신규)

components/
├── rag-config/
│   ├── rag-config-form.tsx     # 전체 설정 폼
│   ├── embedding-config.tsx    # 임베딩 설정 섹션
│   ├── pgvector-config.tsx     # PgVector 설정 섹션
│   ├── neo4j-config.tsx        # Neo4j 설정 섹션
│   ├── hybrid-config.tsx       # Hybrid 설정 섹션
│   └── re-embed-dialog.tsx     # 재임베딩 다이얼로그
```

---

## 1. RAG 설정 페이지

**파일**: `frontend/app/[locale]/projects/[projectId]/settings/rag/page.tsx`

```tsx
'use client';

import { useParams } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { useRagConfig, useUpdateRagConfig, useRagDefaults } from '@/hooks/use-rag-config';
import { RagConfigForm } from '@/components/rag-config/rag-config-form';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Loader2, Settings2, AlertTriangle } from 'lucide-react';
import { Alert, AlertDescription } from '@/components/ui/alert';

export default function RagConfigPage() {
  const { projectId } = useParams<{ projectId: string }>();
  const t = useTranslations('ragConfig');

  const { data: config, isLoading, error } = useRagConfig(projectId);
  const { data: defaults } = useRagDefaults(projectId);
  const updateConfig = useUpdateRagConfig(projectId);

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <Loader2 className="w-8 h-8 animate-spin" />
      </div>
    );
  }

  if (error) {
    return (
      <Alert variant="destructive">
        <AlertTriangle className="h-4 w-4" />
        <AlertDescription>{t('loadError')}</AlertDescription>
      </Alert>
    );
  }

  return (
    <div className="container py-6 max-w-4xl">
      <div className="flex items-center gap-3 mb-6">
        <Settings2 className="w-8 h-8" />
        <div>
          <h1 className="text-2xl font-bold">{t('title')}</h1>
          <p className="text-muted-foreground">{t('description')}</p>
        </div>
      </div>

      <RagConfigForm
        config={config}
        defaults={defaults}
        onSubmit={updateConfig.mutateAsync}
        isSubmitting={updateConfig.isPending}
      />
    </div>
  );
}
```

---

## 2. RAG 설정 폼 컴포넌트

**파일**: `frontend/components/rag-config/rag-config-form.tsx`

```tsx
'use client';

import { useState, useEffect } from 'react';
import { useTranslations } from 'next-intl';
import { Button } from '@/components/ui/button';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Save, RotateCcw, AlertCircle } from 'lucide-react';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { EmbeddingConfig } from './embedding-config';
import { PgVectorConfig } from './pgvector-config';
import { Neo4jConfig } from './neo4j-config';
import { HybridConfig } from './hybrid-config';
import { ReEmbedDialog } from './re-embed-dialog';
import type { RagConfigResponse, UpdateRagConfigRequest, RagDefaults } from '@/lib/types';

interface RagConfigFormProps {
  config: RagConfigResponse;
  defaults?: RagDefaults;
  onSubmit: (data: UpdateRagConfigRequest) => Promise<void>;
  isSubmitting: boolean;
}

export function RagConfigForm({ config, defaults, onSubmit, isSubmitting }: RagConfigFormProps) {
  const t = useTranslations('ragConfig');
  const [formData, setFormData] = useState<UpdateRagConfigRequest>({
    embedding: config.embedding,
    pgvector: config.pgvector,
    neo4j: config.neo4j,
    hybrid: config.hybrid,
  });
  const [embeddingChanged, setEmbeddingChanged] = useState(false);
  const [showReEmbedDialog, setShowReEmbedDialog] = useState(false);

  // 임베딩 모델 변경 감지
  useEffect(() => {
    const providerChanged = formData.embedding?.provider !== config.embedding?.provider;
    const modelChanged = formData.embedding?.model !== config.embedding?.model;
    setEmbeddingChanged(providerChanged || modelChanged);
  }, [formData.embedding, config.embedding]);

  const handleSubmit = async () => {
    await onSubmit(formData);
    if (embeddingChanged) {
      setShowReEmbedDialog(true);
    }
  };

  const handleReset = () => {
    setFormData({
      embedding: config.embedding,
      pgvector: config.pgvector,
      neo4j: config.neo4j,
      hybrid: config.hybrid,
    });
  };

  const updateSection = <K extends keyof UpdateRagConfigRequest>(
    section: K,
    value: UpdateRagConfigRequest[K]
  ) => {
    setFormData(prev => ({ ...prev, [section]: value }));
  };

  return (
    <div className="space-y-6">
      {embeddingChanged && (
        <Alert variant="warning">
          <AlertCircle className="h-4 w-4" />
          <AlertDescription>
            {t('embeddingChangeWarning')}
          </AlertDescription>
        </Alert>
      )}

      <Tabs defaultValue="embedding" className="w-full">
        <TabsList className="grid w-full grid-cols-4">
          <TabsTrigger value="embedding">{t('tabs.embedding')}</TabsTrigger>
          <TabsTrigger value="pgvector">{t('tabs.pgvector')}</TabsTrigger>
          <TabsTrigger value="neo4j">{t('tabs.neo4j')}</TabsTrigger>
          <TabsTrigger value="hybrid">{t('tabs.hybrid')}</TabsTrigger>
        </TabsList>

        <TabsContent value="embedding" className="mt-6">
          <EmbeddingConfig
            value={formData.embedding}
            defaults={defaults?.embedding}
            onChange={(v) => updateSection('embedding', v)}
          />
        </TabsContent>

        <TabsContent value="pgvector" className="mt-6">
          <PgVectorConfig
            value={formData.pgvector}
            defaults={defaults?.pgvector}
            onChange={(v) => updateSection('pgvector', v)}
          />
        </TabsContent>

        <TabsContent value="neo4j" className="mt-6">
          <Neo4jConfig
            value={formData.neo4j}
            defaults={defaults?.neo4j}
            onChange={(v) => updateSection('neo4j', v)}
          />
        </TabsContent>

        <TabsContent value="hybrid" className="mt-6">
          <HybridConfig
            value={formData.hybrid}
            defaults={defaults?.hybrid}
            onChange={(v) => updateSection('hybrid', v)}
          />
        </TabsContent>
      </Tabs>

      <div className="flex justify-end gap-3 pt-4 border-t">
        <Button variant="outline" onClick={handleReset} disabled={isSubmitting}>
          <RotateCcw className="w-4 h-4 mr-2" />
          {t('reset')}
        </Button>
        <Button onClick={handleSubmit} disabled={isSubmitting}>
          {isSubmitting ? (
            <Loader2 className="w-4 h-4 mr-2 animate-spin" />
          ) : (
            <Save className="w-4 h-4 mr-2" />
          )}
          {t('save')}
        </Button>
      </div>

      <ReEmbedDialog
        open={showReEmbedDialog}
        onOpenChange={setShowReEmbedDialog}
        projectId={config.projectId}
      />
    </div>
  );
}
```

---

## 3. 임베딩 설정 섹션

**파일**: `frontend/components/rag-config/embedding-config.tsx`

```tsx
'use client';

import { useTranslations } from 'next-intl';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Label } from '@/components/ui/label';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Badge } from '@/components/ui/badge';
import { Cpu } from 'lucide-react';
import type { EmbeddingConfigRequest } from '@/lib/types';

interface EmbeddingConfigProps {
  value?: EmbeddingConfigRequest;
  defaults?: EmbeddingConfigRequest;
  onChange: (value: EmbeddingConfigRequest) => void;
}

const EMBEDDING_MODELS = {
  openai: [
    { value: 'text-embedding-3-small', label: 'text-embedding-3-small', dimensions: 1536 },
    { value: 'text-embedding-3-large', label: 'text-embedding-3-large', dimensions: 3072 },
    { value: 'text-embedding-ada-002', label: 'text-embedding-ada-002', dimensions: 1536 },
  ],
  ollama: [
    { value: 'nomic-embed-text', label: 'nomic-embed-text', dimensions: 768 },
    { value: 'mxbai-embed-large', label: 'mxbai-embed-large', dimensions: 1024 },
    { value: 'all-minilm', label: 'all-minilm', dimensions: 384 },
  ],
};

export function EmbeddingConfig({ value, defaults, onChange }: EmbeddingConfigProps) {
  const t = useTranslations('ragConfig.embedding');

  const currentProvider = value?.provider || defaults?.provider || 'openai';
  const currentModel = value?.model || defaults?.model;
  const currentDimensions = value?.dimensions || defaults?.dimensions;

  const availableModels = EMBEDDING_MODELS[currentProvider as keyof typeof EMBEDDING_MODELS] || [];

  const handleProviderChange = (provider: string) => {
    const defaultModel = EMBEDDING_MODELS[provider as keyof typeof EMBEDDING_MODELS]?.[0];
    onChange({
      provider,
      model: defaultModel?.value,
      dimensions: defaultModel?.dimensions,
    });
  };

  const handleModelChange = (model: string) => {
    const modelInfo = availableModels.find(m => m.value === model);
    onChange({
      ...value,
      provider: currentProvider,
      model,
      dimensions: modelInfo?.dimensions,
    });
  };

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center gap-2">
          <Cpu className="w-5 h-5" />
          <CardTitle>{t('title')}</CardTitle>
        </div>
        <CardDescription>{t('description')}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-6">
        {/* Provider 선택 */}
        <div className="space-y-2">
          <Label>{t('provider')}</Label>
          <Select value={currentProvider} onValueChange={handleProviderChange}>
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="openai">
                OpenAI
                <Badge variant="secondary" className="ml-2">API</Badge>
              </SelectItem>
              <SelectItem value="ollama">
                Ollama
                <Badge variant="outline" className="ml-2">Local</Badge>
              </SelectItem>
            </SelectContent>
          </Select>
          <p className="text-sm text-muted-foreground">
            {currentProvider === 'openai' ? t('providerOpenaiHint') : t('providerOllamaHint')}
          </p>
        </div>

        {/* Model 선택 */}
        <div className="space-y-2">
          <Label>{t('model')}</Label>
          <Select value={currentModel} onValueChange={handleModelChange}>
            <SelectTrigger>
              <SelectValue placeholder={t('selectModel')} />
            </SelectTrigger>
            <SelectContent>
              {availableModels.map((model) => (
                <SelectItem key={model.value} value={model.value}>
                  <div className="flex items-center justify-between w-full">
                    <span>{model.label}</span>
                    <Badge variant="outline" className="ml-2">
                      {model.dimensions}d
                    </Badge>
                  </div>
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        {/* Dimensions (읽기 전용) */}
        <div className="space-y-2">
          <Label>{t('dimensions')}</Label>
          <Input
            type="number"
            value={currentDimensions || ''}
            disabled
            className="bg-muted"
          />
          <p className="text-sm text-muted-foreground">{t('dimensionsHint')}</p>
        </div>

        {/* 기본값 표시 */}
        {defaults && (
          <div className="pt-4 border-t">
            <p className="text-sm text-muted-foreground">
              {t('defaultValue')}: {defaults.provider} / {defaults.model} ({defaults.dimensions}d)
            </p>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
```

---

## 4. Neo4j 설정 섹션

**파일**: `frontend/components/rag-config/neo4j-config.tsx`

```tsx
'use client';

import { useTranslations } from 'next-intl';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Label } from '@/components/ui/label';
import { Input } from '@/components/ui/input';
import { Switch } from '@/components/ui/switch';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Slider } from '@/components/ui/slider';
import { Network, Zap } from 'lucide-react';
import type { Neo4jConfigRequest } from '@/lib/types';

interface Neo4jConfigProps {
  value?: Neo4jConfigRequest;
  defaults?: Neo4jConfigRequest;
  onChange: (value: Neo4jConfigRequest) => void;
}

const EXTRACTION_MODELS = [
  { value: 'gpt-4o-mini', label: 'GPT-4o Mini', description: 'Fast & cost-effective' },
  { value: 'gpt-4o', label: 'GPT-4o', description: 'Higher quality extraction' },
  { value: 'gpt-4-turbo', label: 'GPT-4 Turbo', description: 'Best for complex documents' },
];

export function Neo4jConfig({ value, defaults, onChange }: Neo4jConfigProps) {
  const t = useTranslations('ragConfig.neo4j');

  const isEnabled = value?.enabled ?? defaults?.enabled ?? false;
  const maxHop = value?.maxHop ?? defaults?.maxHop ?? 2;
  const extractionModel = value?.entityExtractionModel ?? defaults?.entityExtractionModel ?? 'gpt-4o-mini';

  const handleToggle = (enabled: boolean) => {
    onChange({ ...value, enabled });
  };

  const handleMaxHopChange = (values: number[]) => {
    onChange({ ...value, enabled: isEnabled, maxHop: values[0] });
  };

  const handleModelChange = (model: string) => {
    onChange({ ...value, enabled: isEnabled, entityExtractionModel: model });
  };

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Network className="w-5 h-5" />
            <CardTitle>{t('title')}</CardTitle>
          </div>
          <Switch checked={isEnabled} onCheckedChange={handleToggle} />
        </div>
        <CardDescription>{t('description')}</CardDescription>
      </CardHeader>
      <CardContent className={`space-y-6 ${!isEnabled ? 'opacity-50 pointer-events-none' : ''}`}>
        {/* Max Hop */}
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <Label>{t('maxHop')}</Label>
            <span className="text-sm font-medium">{maxHop}</span>
          </div>
          <Slider
            value={[maxHop]}
            onValueChange={handleMaxHopChange}
            min={1}
            max={5}
            step={1}
            disabled={!isEnabled}
          />
          <p className="text-sm text-muted-foreground">{t('maxHopHint')}</p>
        </div>

        {/* Entity Extraction Model */}
        <div className="space-y-2">
          <Label>{t('extractionModel')}</Label>
          <Select value={extractionModel} onValueChange={handleModelChange} disabled={!isEnabled}>
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {EXTRACTION_MODELS.map((model) => (
                <SelectItem key={model.value} value={model.value}>
                  <div className="flex flex-col">
                    <span>{model.label}</span>
                    <span className="text-xs text-muted-foreground">{model.description}</span>
                  </div>
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <p className="text-sm text-muted-foreground">{t('extractionModelHint')}</p>
        </div>

        {/* Graph RAG 설명 */}
        <div className="p-4 bg-muted rounded-lg">
          <div className="flex items-center gap-2 mb-2">
            <Zap className="w-4 h-4 text-yellow-500" />
            <span className="font-medium">{t('graphRagInfo')}</span>
          </div>
          <p className="text-sm text-muted-foreground">{t('graphRagDescription')}</p>
        </div>
      </CardContent>
    </Card>
  );
}
```

---

## 5. Hybrid 설정 섹션

**파일**: `frontend/components/rag-config/hybrid-config.tsx`

```tsx
'use client';

import { useTranslations } from 'next-intl';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Label } from '@/components/ui/label';
import { Input } from '@/components/ui/input';
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';
import { Slider } from '@/components/ui/slider';
import { Blend } from 'lucide-react';
import type { HybridConfigRequest } from '@/lib/types';

interface HybridConfigProps {
  value?: HybridConfigRequest;
  defaults?: HybridConfigRequest;
  onChange: (value: HybridConfigRequest) => void;
}

export function HybridConfig({ value, defaults, onChange }: HybridConfigProps) {
  const t = useTranslations('ragConfig.hybrid');

  const fusionStrategy = value?.fusionStrategy ?? defaults?.fusionStrategy ?? 'rrf';
  const rrfK = value?.rrfK ?? defaults?.rrfK ?? 60;
  const vectorWeight = value?.vectorWeight ?? defaults?.vectorWeight ?? 0.6;
  const graphWeight = value?.graphWeight ?? defaults?.graphWeight ?? 0.4;

  const handleStrategyChange = (strategy: string) => {
    onChange({ ...value, fusionStrategy: strategy });
  };

  const handleRrfKChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    onChange({ ...value, rrfK: parseInt(e.target.value) || 60 });
  };

  const handleWeightChange = (type: 'vector' | 'graph', values: number[]) => {
    const newValue = values[0];
    if (type === 'vector') {
      onChange({ ...value, vectorWeight: newValue, graphWeight: 1 - newValue });
    } else {
      onChange({ ...value, graphWeight: newValue, vectorWeight: 1 - newValue });
    }
  };

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center gap-2">
          <Blend className="w-5 h-5" />
          <CardTitle>{t('title')}</CardTitle>
        </div>
        <CardDescription>{t('description')}</CardDescription>
      </CardHeader>
      <CardContent className="space-y-6">
        {/* Fusion Strategy */}
        <div className="space-y-4">
          <Label>{t('fusionStrategy')}</Label>
          <RadioGroup value={fusionStrategy} onValueChange={handleStrategyChange}>
            <div className="flex items-center space-x-2 p-3 border rounded-lg">
              <RadioGroupItem value="rrf" id="rrf" />
              <div className="flex-1">
                <Label htmlFor="rrf" className="font-medium">
                  RRF (Reciprocal Rank Fusion)
                </Label>
                <p className="text-sm text-muted-foreground">{t('rrfDescription')}</p>
              </div>
            </div>
            <div className="flex items-center space-x-2 p-3 border rounded-lg">
              <RadioGroupItem value="weighted_sum" id="weighted_sum" />
              <div className="flex-1">
                <Label htmlFor="weighted_sum" className="font-medium">
                  Weighted Sum
                </Label>
                <p className="text-sm text-muted-foreground">{t('weightedSumDescription')}</p>
              </div>
            </div>
          </RadioGroup>
        </div>

        {/* RRF K (RRF 선택 시) */}
        {fusionStrategy === 'rrf' && (
          <div className="space-y-2">
            <Label>{t('rrfK')}</Label>
            <Input
              type="number"
              value={rrfK}
              onChange={handleRrfKChange}
              min={1}
              max={100}
            />
            <p className="text-sm text-muted-foreground">{t('rrfKHint')}</p>
          </div>
        )}

        {/* Weights (Weighted Sum 선택 시) */}
        {fusionStrategy === 'weighted_sum' && (
          <div className="space-y-6">
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <Label>{t('vectorWeight')}</Label>
                <span className="text-sm font-medium">{(vectorWeight * 100).toFixed(0)}%</span>
              </div>
              <Slider
                value={[vectorWeight]}
                onValueChange={(v) => handleWeightChange('vector', v)}
                min={0}
                max={1}
                step={0.1}
              />
            </div>

            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <Label>{t('graphWeight')}</Label>
                <span className="text-sm font-medium">{(graphWeight * 100).toFixed(0)}%</span>
              </div>
              <Slider
                value={[graphWeight]}
                onValueChange={(v) => handleWeightChange('graph', v)}
                min={0}
                max={1}
                step={0.1}
              />
            </div>

            {/* Weight 시각화 */}
            <div className="flex h-4 rounded-full overflow-hidden">
              <div
                className="bg-blue-500 transition-all"
                style={{ width: `${vectorWeight * 100}%` }}
              />
              <div
                className="bg-green-500 transition-all"
                style={{ width: `${graphWeight * 100}%` }}
              />
            </div>
            <div className="flex justify-between text-xs text-muted-foreground">
              <span className="flex items-center gap-1">
                <div className="w-2 h-2 rounded-full bg-blue-500" />
                Vector
              </span>
              <span className="flex items-center gap-1">
                <div className="w-2 h-2 rounded-full bg-green-500" />
                Graph
              </span>
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
```

---

## 6. 재임베딩 다이얼로그

**파일**: `frontend/components/rag-config/re-embed-dialog.tsx`

```tsx
'use client';

import { useState, useEffect } from 'react';
import { useTranslations } from 'next-intl';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Progress } from '@/components/ui/progress';
import { useTriggerReEmbed, useReEmbedStatus } from '@/hooks/use-rag-config';
import { AlertTriangle, CheckCircle, Loader2, RefreshCw, XCircle } from 'lucide-react';

interface ReEmbedDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  projectId: string;
}

export function ReEmbedDialog({ open, onOpenChange, projectId }: ReEmbedDialogProps) {
  const t = useTranslations('ragConfig.reEmbed');
  const [isPolling, setIsPolling] = useState(false);

  const triggerReEmbed = useTriggerReEmbed(projectId);
  const { data: status, refetch } = useReEmbedStatus(projectId, {
    enabled: isPolling,
    refetchInterval: isPolling ? 2000 : false,
  });

  // 상태 폴링
  useEffect(() => {
    if (status && !status.inProgress && isPolling) {
      setIsPolling(false);
    }
  }, [status, isPolling]);

  const handleStartReEmbed = async () => {
    await triggerReEmbed.mutateAsync();
    setIsPolling(true);
  };

  const handleClose = () => {
    if (!status?.inProgress) {
      onOpenChange(false);
    }
  };

  const isCompleted = status && !status.inProgress && status.processedVersions > 0;
  const hasFailed = status && status.failedCount > 0;

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <RefreshCw className="w-5 h-5" />
            {t('title')}
          </DialogTitle>
          <DialogDescription>{t('description')}</DialogDescription>
        </DialogHeader>

        <div className="py-4">
          {!status && !triggerReEmbed.isPending && (
            <div className="text-center py-6">
              <AlertTriangle className="w-12 h-12 mx-auto text-yellow-500 mb-4" />
              <p className="text-sm text-muted-foreground mb-4">{t('warning')}</p>
              <p className="text-sm font-medium">{t('confirmMessage')}</p>
            </div>
          )}

          {(triggerReEmbed.isPending || status?.inProgress) && (
            <div className="space-y-4">
              <div className="flex items-center justify-center py-4">
                <Loader2 className="w-8 h-8 animate-spin text-primary" />
              </div>
              {status && (
                <>
                  <Progress value={status.progress} />
                  <div className="grid grid-cols-2 gap-4 text-sm">
                    <div>
                      <span className="text-muted-foreground">{t('processed')}: </span>
                      <span className="font-medium">
                        {status.processedVersions} / {status.totalVersions}
                      </span>
                    </div>
                    <div>
                      <span className="text-muted-foreground">{t('embedded')}: </span>
                      <span className="font-medium">{status.embeddedCount}</span>
                    </div>
                    <div>
                      <span className="text-muted-foreground">{t('deleted')}: </span>
                      <span className="font-medium">{status.deletedEmbeddings}</span>
                    </div>
                    {status.failedCount > 0 && (
                      <div className="text-red-500">
                        <span>{t('failed')}: </span>
                        <span className="font-medium">{status.failedCount}</span>
                      </div>
                    )}
                  </div>
                </>
              )}
            </div>
          )}

          {isCompleted && (
            <div className="text-center py-6">
              {hasFailed ? (
                <>
                  <XCircle className="w-12 h-12 mx-auto text-yellow-500 mb-4" />
                  <p className="font-medium">{t('completedWithErrors')}</p>
                  <p className="text-sm text-muted-foreground mt-2">
                    {t('completedStats', {
                      total: status.processedVersions,
                      failed: status.failedCount,
                    })}
                  </p>
                </>
              ) : (
                <>
                  <CheckCircle className="w-12 h-12 mx-auto text-green-500 mb-4" />
                  <p className="font-medium">{t('completed')}</p>
                  <p className="text-sm text-muted-foreground mt-2">
                    {t('completedMessage', { count: status.embeddedCount })}
                  </p>
                </>
              )}
            </div>
          )}
        </div>

        <DialogFooter>
          {!status && !triggerReEmbed.isPending && (
            <>
              <Button variant="outline" onClick={() => onOpenChange(false)}>
                {t('cancel')}
              </Button>
              <Button onClick={handleStartReEmbed} variant="destructive">
                {t('start')}
              </Button>
            </>
          )}
          {isCompleted && (
            <Button onClick={() => onOpenChange(false)}>{t('close')}</Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
```

---

## 7. API Hooks

**파일**: `frontend/hooks/use-rag-config.ts`

```typescript
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { ragConfigApi } from '@/lib/api';
import type { UpdateRagConfigRequest } from '@/lib/types';

export const ragConfigKeys = {
  all: ['rag-config'] as const,
  config: (projectId: string) => [...ragConfigKeys.all, projectId] as const,
  defaults: (projectId: string) => [...ragConfigKeys.all, projectId, 'defaults'] as const,
  reEmbedStatus: (projectId: string) => [...ragConfigKeys.all, projectId, 're-embed-status'] as const,
};

export function useRagConfig(projectId: string) {
  return useQuery({
    queryKey: ragConfigKeys.config(projectId),
    queryFn: () => ragConfigApi.getConfig(projectId),
    enabled: !!projectId,
  });
}

export function useRagDefaults(projectId: string) {
  return useQuery({
    queryKey: ragConfigKeys.defaults(projectId),
    queryFn: () => ragConfigApi.getDefaults(projectId),
    enabled: !!projectId,
  });
}

export function useUpdateRagConfig(projectId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: UpdateRagConfigRequest) => ragConfigApi.updateConfig(projectId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ragConfigKeys.config(projectId) });
    },
  });
}

export function useValidateRagConfig(projectId: string) {
  return useMutation({
    mutationFn: (data: UpdateRagConfigRequest) => ragConfigApi.validateConfig(projectId, data),
  });
}

export function useTriggerReEmbed(projectId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () => ragConfigApi.triggerReEmbed(projectId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ragConfigKeys.reEmbedStatus(projectId) });
    },
  });
}

export function useReEmbedStatus(projectId: string, options?: { enabled?: boolean; refetchInterval?: number | false }) {
  return useQuery({
    queryKey: ragConfigKeys.reEmbedStatus(projectId),
    queryFn: () => ragConfigApi.getReEmbedStatus(projectId),
    enabled: options?.enabled ?? false,
    refetchInterval: options?.refetchInterval,
  });
}
```

---

## 8. API Client 확장

**파일**: `frontend/lib/api.ts` (추가)

```typescript
export const ragConfigApi = {
  getConfig: (projectId: string) =>
    request<RagConfigResponse>(`/api/projects/${projectId}/rag-config`),

  updateConfig: (projectId: string, data: UpdateRagConfigRequest) =>
    request<RagConfigResponse>(`/api/projects/${projectId}/rag-config`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),

  validateConfig: (projectId: string, data: UpdateRagConfigRequest) =>
    request<RagConfigValidationResponse>(`/api/projects/${projectId}/rag-config/validate`, {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  getDefaults: (projectId: string) =>
    request<RagConfigDefaultsResponse>(`/api/projects/${projectId}/rag-config/defaults`),

  triggerReEmbed: (projectId: string) =>
    request<ReEmbeddingTriggerResponse>(`/api/projects/${projectId}/rag-config/re-embed`, {
      method: 'POST',
    }),

  getReEmbedStatus: (projectId: string) =>
    request<ReEmbeddingStatusResponse>(`/api/projects/${projectId}/rag-config/re-embed/status`),
};
```

---

## 9. 타입 정의

**파일**: `frontend/lib/types.ts` (추가)

```typescript
// ===== RAG Config Types =====

export interface RagConfigResponse {
  projectId: string;
  embedding: EmbeddingConfigResponse;
  pgvector: PgVectorConfigResponse;
  neo4j: Neo4jConfigResponse;
  hybrid: HybridConfigResponse;
  updatedAt: string;
}

export interface EmbeddingConfigResponse {
  provider: string;
  model: string;
  dimensions: number;
}

export interface PgVectorConfigResponse {
  enabled: boolean;
  similarityThreshold: number;
}

export interface Neo4jConfigResponse {
  enabled: boolean;
  maxHop: number;
  entityExtractionModel: string;
}

export interface HybridConfigResponse {
  fusionStrategy: string;
  rrfK: number;
  vectorWeight: number;
  graphWeight: number;
}

export interface UpdateRagConfigRequest {
  embedding?: EmbeddingConfigRequest;
  pgvector?: PgVectorConfigRequest;
  neo4j?: Neo4jConfigRequest;
  hybrid?: HybridConfigRequest;
}

export interface EmbeddingConfigRequest {
  provider?: string;
  model?: string;
  dimensions?: number;
}

export interface PgVectorConfigRequest {
  enabled?: boolean;
  similarityThreshold?: number;
}

export interface Neo4jConfigRequest {
  enabled?: boolean;
  maxHop?: number;
  entityExtractionModel?: string;
}

export interface HybridConfigRequest {
  fusionStrategy?: string;
  rrfK?: number;
  vectorWeight?: number;
  graphWeight?: number;
}

export interface RagConfigValidationResponse {
  valid: boolean;
  errors: string[];
  warnings: string[];
}

export interface RagConfigDefaultsResponse {
  embedding: EmbeddingConfigResponse;
  pgvector: PgVectorConfigResponse;
  neo4j: Neo4jConfigResponse;
  hybrid: HybridConfigResponse;
}

export interface ReEmbeddingTriggerResponse {
  projectId: string;
  message: string;
  inProgress: boolean;
}

export interface ReEmbeddingStatusResponse {
  projectId: string;
  inProgress: boolean;
  totalVersions: number;
  processedVersions: number;
  progress: number;
  deletedEmbeddings: number;
  embeddedCount: number;
  failedCount: number;
  errorMessage?: string;
}
```

---

## 10. 다국어 메시지

**파일**: `frontend/messages/en.json` (추가)

```json
{
  "ragConfig": {
    "title": "RAG Settings",
    "description": "Configure embedding, vector search, and hybrid search settings for this project",
    "loadError": "Failed to load RAG configuration",
    "save": "Save Changes",
    "reset": "Reset",
    "embeddingChangeWarning": "Changing the embedding model will require re-embedding all documents. This may take some time.",
    "tabs": {
      "embedding": "Embedding",
      "pgvector": "Vector Search",
      "neo4j": "Graph RAG",
      "hybrid": "Hybrid"
    },
    "embedding": {
      "title": "Embedding Model",
      "description": "Configure the embedding model for document vectorization",
      "provider": "Provider",
      "providerOpenaiHint": "Uses OpenAI API. Requires API key configuration.",
      "providerOllamaHint": "Uses local Ollama server. No API costs.",
      "model": "Model",
      "selectModel": "Select a model",
      "dimensions": "Dimensions",
      "dimensionsHint": "Determined by the selected model",
      "defaultValue": "Default"
    },
    "pgvector": {
      "title": "Vector Search (PgVector)",
      "description": "Configure vector similarity search settings",
      "enabled": "Enable vector search",
      "similarityThreshold": "Similarity Threshold",
      "similarityThresholdHint": "Minimum similarity score (0.0 - 1.0). Higher values return more relevant results."
    },
    "neo4j": {
      "title": "Graph RAG (Neo4j)",
      "description": "Configure knowledge graph-based search using Neo4j",
      "maxHop": "Max Hop Distance",
      "maxHopHint": "Maximum graph traversal depth. Higher values explore more relationships but may be slower.",
      "extractionModel": "Entity Extraction Model",
      "extractionModelHint": "LLM model used for extracting entities and relationships from documents",
      "graphRagInfo": "What is Graph RAG?",
      "graphRagDescription": "Graph RAG extracts entities and relationships from your documents to build a knowledge graph. This enables semantic search that understands connections between concepts."
    },
    "hybrid": {
      "title": "Hybrid Search",
      "description": "Configure how vector and graph search results are combined",
      "fusionStrategy": "Fusion Strategy",
      "rrfDescription": "Combines rankings using reciprocal rank fusion. Good for most use cases.",
      "weightedSumDescription": "Combines scores using weighted sum. Allows fine-tuning the balance.",
      "rrfK": "RRF K Parameter",
      "rrfKHint": "Ranking constant (default: 60). Lower values give more weight to top results.",
      "vectorWeight": "Vector Search Weight",
      "graphWeight": "Graph Search Weight"
    },
    "reEmbed": {
      "title": "Re-embed Documents",
      "description": "Regenerate embeddings for all documents in this project",
      "warning": "This will delete all existing embeddings and regenerate them with the new model. This process cannot be undone.",
      "confirmMessage": "Are you sure you want to re-embed all documents?",
      "start": "Start Re-embedding",
      "cancel": "Cancel",
      "close": "Close",
      "processed": "Processed",
      "embedded": "Embedded",
      "deleted": "Deleted",
      "failed": "Failed",
      "completed": "Re-embedding Complete",
      "completedWithErrors": "Completed with Errors",
      "completedMessage": "{count} embeddings created successfully",
      "completedStats": "{total} documents processed, {failed} failed"
    }
  }
}
```

**파일**: `frontend/messages/ko.json` (추가)

```json
{
  "ragConfig": {
    "title": "RAG 설정",
    "description": "프로젝트의 임베딩, 벡터 검색, 하이브리드 검색 설정을 구성합니다",
    "loadError": "RAG 설정을 불러오는데 실패했습니다",
    "save": "변경 저장",
    "reset": "초기화",
    "embeddingChangeWarning": "임베딩 모델을 변경하면 모든 문서를 다시 임베딩해야 합니다. 시간이 걸릴 수 있습니다.",
    "tabs": {
      "embedding": "임베딩",
      "pgvector": "벡터 검색",
      "neo4j": "그래프 RAG",
      "hybrid": "하이브리드"
    },
    "embedding": {
      "title": "임베딩 모델",
      "description": "문서 벡터화를 위한 임베딩 모델을 설정합니다",
      "provider": "제공자",
      "providerOpenaiHint": "OpenAI API를 사용합니다. API 키 설정이 필요합니다.",
      "providerOllamaHint": "로컬 Ollama 서버를 사용합니다. API 비용이 없습니다.",
      "model": "모델",
      "selectModel": "모델 선택",
      "dimensions": "차원",
      "dimensionsHint": "선택한 모델에 의해 결정됩니다",
      "defaultValue": "기본값"
    },
    "pgvector": {
      "title": "벡터 검색 (PgVector)",
      "description": "벡터 유사도 검색 설정을 구성합니다",
      "enabled": "벡터 검색 활성화",
      "similarityThreshold": "유사도 임계값",
      "similarityThresholdHint": "최소 유사도 점수 (0.0 - 1.0). 높을수록 더 관련성 높은 결과를 반환합니다."
    },
    "neo4j": {
      "title": "그래프 RAG (Neo4j)",
      "description": "Neo4j를 사용한 지식 그래프 기반 검색을 설정합니다",
      "maxHop": "최대 홉 거리",
      "maxHopHint": "그래프 탐색 최대 깊이. 높을수록 더 많은 관계를 탐색하지만 느릴 수 있습니다.",
      "extractionModel": "엔티티 추출 모델",
      "extractionModelHint": "문서에서 엔티티와 관계를 추출하는 데 사용되는 LLM 모델",
      "graphRagInfo": "Graph RAG란?",
      "graphRagDescription": "Graph RAG는 문서에서 엔티티와 관계를 추출하여 지식 그래프를 구축합니다. 이를 통해 개념 간의 연결을 이해하는 시맨틱 검색이 가능합니다."
    },
    "hybrid": {
      "title": "하이브리드 검색",
      "description": "벡터와 그래프 검색 결과를 결합하는 방식을 설정합니다",
      "fusionStrategy": "융합 전략",
      "rrfDescription": "역순위 융합을 사용하여 순위를 결합합니다. 대부분의 경우에 적합합니다.",
      "weightedSumDescription": "가중 합계를 사용하여 점수를 결합합니다. 균형을 세밀하게 조정할 수 있습니다.",
      "rrfK": "RRF K 파라미터",
      "rrfKHint": "순위 상수 (기본값: 60). 낮을수록 상위 결과에 더 가중치를 줍니다.",
      "vectorWeight": "벡터 검색 가중치",
      "graphWeight": "그래프 검색 가중치"
    },
    "reEmbed": {
      "title": "문서 재임베딩",
      "description": "프로젝트의 모든 문서에 대해 임베딩을 다시 생성합니다",
      "warning": "기존 모든 임베딩을 삭제하고 새 모델로 다시 생성합니다. 이 작업은 취소할 수 없습니다.",
      "confirmMessage": "모든 문서를 재임베딩하시겠습니까?",
      "start": "재임베딩 시작",
      "cancel": "취소",
      "close": "닫기",
      "processed": "처리됨",
      "embedded": "임베딩됨",
      "deleted": "삭제됨",
      "failed": "실패",
      "completed": "재임베딩 완료",
      "completedWithErrors": "오류와 함께 완료",
      "completedMessage": "{count}개의 임베딩이 성공적으로 생성되었습니다",
      "completedStats": "{total}개 문서 처리됨, {failed}개 실패"
    }
  }
}
```

---

## 11. 필요한 shadcn/ui 컴포넌트

```bash
# 설치 필요한 컴포넌트
npx shadcn-ui@latest add tabs
npx shadcn-ui@latest add slider
npx shadcn-ui@latest add radio-group
npx shadcn-ui@latest add progress
npx shadcn-ui@latest add alert
```

---

## 12. 사이드바 메뉴 추가

**수정 파일**: `frontend/components/sidebar.tsx`

```tsx
// 프로젝트별 메뉴에 추가
<Link href={`/projects/${selectedProject.id}/settings/rag`}>
  <Button variant="ghost" className="w-full justify-start">
    <Settings2 className="w-4 h-4 mr-2" />
    {t('ragSettings')}
  </Button>
</Link>
```

---

## 작업 목록

### 필수 작업
- [ ] shadcn/ui 컴포넌트 설치 (tabs, slider, radio-group, progress, alert)
- [ ] 타입 정의 추가 (`lib/types.ts`)
- [ ] API client 확장 (`lib/api.ts`)
- [ ] API hooks 생성 (`hooks/use-rag-config.ts`)
- [ ] RAG 설정 페이지 생성 (`app/.../settings/rag/page.tsx`)
- [ ] RagConfigForm 컴포넌트 생성
- [ ] EmbeddingConfig 컴포넌트 생성
- [ ] PgVectorConfig 컴포넌트 생성
- [ ] Neo4jConfig 컴포넌트 생성
- [ ] HybridConfig 컴포넌트 생성
- [ ] ReEmbedDialog 컴포넌트 생성
- [ ] 다국어 메시지 추가 (en.json, ko.json)
- [ ] 사이드바에 RAG 설정 메뉴 추가

### 선택 작업
- [ ] 설정 변경 시 실시간 검증 (validate API 연동)
- [ ] 설정 비교 뷰 (현재 vs 기본값)
- [ ] 설정 내보내기/가져오기 기능
- [ ] 검색 페이지에서 임시 오버라이드 UI

---

## UI 디자인 참고

### 탭 레이아웃
```
┌──────────────────────────────────────────────────────────────┐
│  RAG Settings                                                │
│  Configure embedding, vector search, and hybrid search...   │
├──────────────────────────────────────────────────────────────┤
│  [Embedding] [Vector Search] [Graph RAG] [Hybrid]            │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │  Embedding Model                                       │ │
│  │  Configure the embedding model for document...         │ │
│  │                                                        │ │
│  │  Provider:  [OpenAI          ▼]                        │ │
│  │                                                        │ │
│  │  Model:     [text-embedding-3-small  ▼]  1536d         │ │
│  │                                                        │ │
│  │  Dimensions: 1536 (auto)                               │ │
│  │                                                        │ │
│  │  Default: openai / text-embedding-3-small (1536d)      │ │
│  └────────────────────────────────────────────────────────┘ │
│                                                              │
│                              [Reset]  [Save Changes]         │
└──────────────────────────────────────────────────────────────┘
```

### 재임베딩 다이얼로그
```
┌──────────────────────────────────────────┐
│  ↻ Re-embed Documents                    │
│                                          │
│  ⚠️ This will delete all existing        │
│  embeddings and regenerate them with     │
│  the new model.                          │
│                                          │
│  Are you sure you want to re-embed       │
│  all documents?                          │
│                                          │
│           [Cancel]  [Start Re-embedding] │
└──────────────────────────────────────────┘

// 진행 중
┌──────────────────────────────────────────┐
│  ↻ Re-embed Documents                    │
│                                          │
│         ⟳ (spinning)                     │
│                                          │
│  ████████████████░░░░░░░░  67%           │
│                                          │
│  Processed: 67 / 100                     │
│  Embedded: 1,234                         │
│  Deleted: 1,100                          │
│                                          │
└──────────────────────────────────────────┘

// 완료
┌──────────────────────────────────────────┐
│  ↻ Re-embed Documents                    │
│                                          │
│         ✓ (green check)                  │
│                                          │
│  Re-embedding Complete                   │
│  1,500 embeddings created successfully   │
│                                          │
│                              [Close]     │
└──────────────────────────────────────────┘
```
