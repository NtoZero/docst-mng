'use client';

import { useTranslations } from 'next-intl';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { Slider } from '@/components/ui/slider';
import { Database } from 'lucide-react';
import type { PgVectorConfigRequest } from '@/lib/types';

interface PgVectorConfigProps {
  value?: PgVectorConfigRequest;
  defaults?: PgVectorConfigRequest;
  onChange: (value: PgVectorConfigRequest) => void;
}

export function PgVectorConfig({ value, defaults, onChange }: PgVectorConfigProps) {
  const t = useTranslations('ragConfig.pgvector');

  const isEnabled = value?.enabled ?? defaults?.enabled ?? true;
  const similarityThreshold = value?.similarityThreshold ?? defaults?.similarityThreshold ?? 0.7;

  const handleToggle = (enabled: boolean) => {
    onChange({ ...value, enabled });
  };

  const handleThresholdChange = (values: number[]) => {
    onChange({ ...value, enabled: isEnabled, similarityThreshold: values[0] });
  };

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Database className="w-5 h-5" />
            <CardTitle>{t('title')}</CardTitle>
          </div>
          <Switch checked={isEnabled} onCheckedChange={handleToggle} />
        </div>
        <CardDescription>{t('description')}</CardDescription>
      </CardHeader>
      <CardContent className={`space-y-6 ${!isEnabled ? 'opacity-50 pointer-events-none' : ''}`}>
        {/* Similarity Threshold */}
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <Label>{t('similarityThreshold')}</Label>
            <span className="text-sm font-medium">{similarityThreshold.toFixed(2)}</span>
          </div>
          <Slider
            value={[similarityThreshold]}
            onValueChange={handleThresholdChange}
            min={0}
            max={1}
            step={0.05}
            disabled={!isEnabled}
          />
          <p className="text-sm text-muted-foreground">{t('similarityThresholdHint')}</p>
        </div>
      </CardContent>
    </Card>
  );
}
