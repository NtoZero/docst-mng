'use client';

import { useParams } from 'next/navigation';
import { useTranslations } from 'next-intl';
import { useRagConfig, useUpdateRagConfig, useRagDefaults } from '@/hooks/use-rag-config';
import { RagConfigForm } from '@/components/rag-config/rag-config-form';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Loader2, Settings2, AlertTriangle } from 'lucide-react';
import { Alert, AlertDescription } from '@/components/ui/alert';
import type { UpdateRagConfigRequest } from '@/lib/types';

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

  if (!config) {
    return null;
  }

  const handleSubmit = async (data: UpdateRagConfigRequest) => {
    await updateConfig.mutateAsync(data);
  };

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
        onSubmit={handleSubmit}
        isSubmitting={updateConfig.isPending}
      />
    </div>
  );
}
