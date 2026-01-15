'use client';

import { useState } from 'react';
import { Slider } from '@/components/ui/slider';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Settings2 } from 'lucide-react';
import type { SearchParams, SearchMode, FusionStrategy, SearchPreset } from '@/lib/types';

interface SearchParamsPanelProps {
  params: SearchParams;
  onChange: (params: SearchParams) => void;
}

const PRESETS: Record<Exclude<SearchPreset, 'custom'>, Partial<SearchParams>> = {
  balanced: { similarityThreshold: 0.3, topK: 10, fusionStrategy: 'rrf' },
  precision: { similarityThreshold: 0.5, topK: 5, fusionStrategy: 'rrf' },
  recall: { similarityThreshold: 0.2, topK: 20, fusionStrategy: 'rrf' },
};

export function SearchParamsPanel({ params, onChange }: SearchParamsPanelProps) {
  const [preset, setPreset] = useState<SearchPreset>('balanced');

  const handlePresetChange = (value: string) => {
    if (value === 'custom') {
      setPreset('custom');
      return;
    }
    const presetValue = PRESETS[value as keyof typeof PRESETS];
    setPreset(value as SearchPreset);
    onChange({
      ...params,
      similarityThreshold: presetValue.similarityThreshold ?? params.similarityThreshold,
      topK: presetValue.topK ?? params.topK,
      fusionStrategy: presetValue.fusionStrategy ?? params.fusionStrategy,
    });
  };

  const handleParamChange = <K extends keyof SearchParams>(key: K, value: SearchParams[K]) => {
    setPreset('custom');
    onChange({ ...params, [key]: value });
  };

  return (
    <Card className="w-full">
      <CardHeader className="pb-3">
        <CardTitle className="text-sm flex items-center gap-2">
          <Settings2 className="h-4 w-4" />
          Search Parameters
          <Badge variant="outline" className="text-xs ml-auto">Adjusts</Badge>
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {/* Preset Selection */}
        <div className="space-y-2">
          <Label className="text-xs text-muted-foreground">Preset</Label>
          <Select value={preset} onValueChange={handlePresetChange}>
            <SelectTrigger className="h-8">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="balanced">Balanced</SelectItem>
              <SelectItem value="precision">Precision</SelectItem>
              <SelectItem value="recall">Recall</SelectItem>
              <SelectItem value="custom">Custom</SelectItem>
            </SelectContent>
          </Select>
        </div>

        {/* Search Mode */}
        <div className="space-y-2">
          <Label className="text-xs text-muted-foreground">Search Mode</Label>
          <Select
            value={params.mode}
            onValueChange={(v) => handleParamChange('mode', v as SearchMode)}
          >
            <SelectTrigger className="h-8">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="semantic">Semantic</SelectItem>
              <SelectItem value="keyword">Keyword</SelectItem>
              <SelectItem value="graph">Graph</SelectItem>
              <SelectItem value="hybrid">Hybrid</SelectItem>
            </SelectContent>
          </Select>
        </div>

        {/* Similarity Threshold (for semantic/hybrid modes) */}
        {(params.mode === 'semantic' || params.mode === 'hybrid') && (
          <div className="space-y-2">
            <div className="flex justify-between">
              <Label className="text-xs text-muted-foreground">Similarity Threshold</Label>
              <span className="text-xs text-muted-foreground">
                {params.similarityThreshold.toFixed(2)}
              </span>
            </div>
            <Slider
              value={[params.similarityThreshold]}
              min={0}
              max={1}
              step={0.05}
              onValueChange={([v]) => handleParamChange('similarityThreshold', v)}
            />
            <p className="text-xs text-muted-foreground">
              Lower = more results
            </p>
          </div>
        )}

        {/* TopK */}
        <div className="space-y-2">
          <Label className="text-xs text-muted-foreground">Results (topK)</Label>
          <Input
            type="number"
            min={1}
            max={50}
            value={params.topK}
            onChange={(e) => handleParamChange('topK', parseInt(e.target.value) || 10)}
            className="h-8"
          />
        </div>

        {/* Hybrid Settings */}
        {params.mode === 'hybrid' && (
          <>
            <div className="border-t pt-4 space-y-2">
              <Label className="text-xs font-medium">Hybrid Settings</Label>
            </div>

            {/* Fusion Strategy */}
            <div className="space-y-2">
              <Label className="text-xs text-muted-foreground">Fusion Strategy</Label>
              <Select
                value={params.fusionStrategy}
                onValueChange={(v) => handleParamChange('fusionStrategy', v as FusionStrategy)}
              >
                <SelectTrigger className="h-8">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="rrf">RRF</SelectItem>
                  <SelectItem value="weighted_sum">Weighted Sum</SelectItem>
                </SelectContent>
              </Select>
            </div>

            {/* RRF K (for rrf strategy) */}
            {params.fusionStrategy === 'rrf' && (
              <div className="space-y-2">
                <div className="flex justify-between">
                  <Label className="text-xs text-muted-foreground">RRF K</Label>
                  <span className="text-xs text-muted-foreground">{params.rrfK}</span>
                </div>
                <Slider
                  value={[params.rrfK]}
                  min={1}
                  max={100}
                  step={1}
                  onValueChange={([v]) => handleParamChange('rrfK', v)}
                />
              </div>
            )}

            {/* Vector Weight (for weighted_sum strategy) */}
            {params.fusionStrategy === 'weighted_sum' && (
              <div className="space-y-2">
                <div className="flex justify-between">
                  <Label className="text-xs text-muted-foreground">Vector Weight</Label>
                  <span className="text-xs text-muted-foreground">
                    {params.vectorWeight.toFixed(1)} / {(1 - params.vectorWeight).toFixed(1)}
                  </span>
                </div>
                <Slider
                  value={[params.vectorWeight]}
                  min={0}
                  max={1}
                  step={0.1}
                  onValueChange={([v]) => handleParamChange('vectorWeight', v)}
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

export const DEFAULT_SEARCH_PARAMS: SearchParams = {
  mode: 'semantic',
  similarityThreshold: 0.3,
  topK: 10,
  fusionStrategy: 'rrf',
  rrfK: 60,
  vectorWeight: 0.6,
};
