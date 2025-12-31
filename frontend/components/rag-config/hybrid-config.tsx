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
                <Label htmlFor="rrf" className="font-medium cursor-pointer">
                  RRF (Reciprocal Rank Fusion)
                </Label>
                <p className="text-sm text-muted-foreground">{t('rrfDescription')}</p>
              </div>
            </div>
            <div className="flex items-center space-x-2 p-3 border rounded-lg">
              <RadioGroupItem value="weighted_sum" id="weighted_sum" />
              <div className="flex-1">
                <Label htmlFor="weighted_sum" className="font-medium cursor-pointer">
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
