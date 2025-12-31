'use client';

import { useState, useEffect } from 'react';
import { useTranslations } from 'next-intl';
import { Button } from '@/components/ui/button';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Save, RotateCcw, AlertCircle, Loader2 } from 'lucide-react';
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
