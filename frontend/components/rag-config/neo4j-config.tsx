'use client';

import { useTranslations } from 'next-intl';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Label } from '@/components/ui/label';
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
