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
                <div className="flex items-center gap-2">
                  <span>OpenAI</span>
                  <Badge variant="secondary">API</Badge>
                </div>
              </SelectItem>
              <SelectItem value="ollama">
                <div className="flex items-center gap-2">
                  <span>Ollama</span>
                  <Badge variant="outline">Local</Badge>
                </div>
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
                  <div className="flex items-center gap-2">
                    <span>{model.label}</span>
                    <Badge variant="outline">{model.dimensions}d</Badge>
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
