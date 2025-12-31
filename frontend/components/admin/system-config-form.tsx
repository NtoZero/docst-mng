'use client';

import { useState } from 'react';
import { useSystemConfigs, useUpdateSystemConfig } from '@/hooks/use-admin-config';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Loader2 } from 'lucide-react';
import { useTranslations } from 'next-intl';

interface ConfigGroup {
  title: string;
  description: string;
  configs: string[];
}

const CONFIG_GROUPS: ConfigGroup[] = [
  {
    title: 'PostgreSQL (PgVector)',
    description: 'PostgreSQL database connection for vector storage',
    configs: ['postgresql.host', 'postgresql.port', 'postgresql.database', 'postgresql.schema'],
  },
  {
    title: 'Neo4j',
    description: 'Graph database configuration',
    configs: ['neo4j.uri', 'neo4j.enabled', 'neo4j.max-hop', 'neo4j.entity-extraction-model'],
  },
  {
    title: 'Ollama',
    description: 'Local LLM server configuration',
    configs: ['ollama.base-url', 'ollama.enabled'],
  },
  {
    title: 'Embedding',
    description: 'Default embedding model settings',
    configs: ['embedding.default-provider', 'embedding.default-model', 'embedding.default-dimensions'],
  },
  {
    title: 'RAG - PgVector',
    description: 'Vector search configuration',
    configs: ['rag.pgvector.enabled', 'rag.pgvector.similarity-threshold'],
  },
  {
    title: 'RAG - Hybrid',
    description: 'Hybrid search configuration',
    configs: ['rag.hybrid.fusion-strategy', 'rag.hybrid.rrf-k', 'rag.hybrid.vector-weight', 'rag.hybrid.graph-weight'],
  },
];

export function SystemConfigForm() {
  const t = useTranslations('admin');
  const { data: configs, isLoading, error } = useSystemConfigs();
  const updateConfig = useUpdateSystemConfig();
  const [editingKey, setEditingKey] = useState<string | null>(null);
  const [editValue, setEditValue] = useState('');

  const handleEdit = (key: string, currentValue: string) => {
    setEditingKey(key);
    setEditValue(currentValue);
  };

  const handleSave = async (key: string) => {
    try {
      await updateConfig.mutateAsync({
        key,
        request: { configValue: editValue },
      });
      setEditingKey(null);
      setEditValue('');
    } catch (err) {
      console.error('Failed to update config:', err);
    }
  };

  const handleCancel = () => {
    setEditingKey(null);
    setEditValue('');
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center p-8">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (error) {
    return (
      <Alert variant="destructive">
        <AlertDescription>
          Failed to load system configurations: {error.message}
        </AlertDescription>
      </Alert>
    );
  }

  const configMap = new Map(configs?.map((c) => [c.configKey, c]) || []);

  return (
    <div className="space-y-6">
      {CONFIG_GROUPS.map((group) => (
        <Card key={group.title}>
          <CardHeader>
            <CardTitle>{group.title}</CardTitle>
            <CardDescription>{group.description}</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            {group.configs.map((key) => {
              const config = configMap.get(key);
              const isEditing = editingKey === key;

              return (
                <div key={key} className="grid gap-2">
                  <Label htmlFor={key} className="text-sm font-mono">
                    {key}
                  </Label>
                  {config?.description && (
                    <p className="text-xs text-muted-foreground">
                      {config.description}
                    </p>
                  )}
                  <div className="flex gap-2">
                    {isEditing ? (
                      <>
                        <Input
                          id={key}
                          value={editValue}
                          onChange={(e) => setEditValue(e.target.value)}
                          className="flex-1"
                        />
                        <Button
                          size="sm"
                          onClick={() => handleSave(key)}
                          disabled={updateConfig.isPending}
                        >
                          {updateConfig.isPending ? (
                            <Loader2 className="h-4 w-4 animate-spin" />
                          ) : (
                            'Save'
                          )}
                        </Button>
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={handleCancel}
                          disabled={updateConfig.isPending}
                        >
                          Cancel
                        </Button>
                      </>
                    ) : (
                      <>
                        <Input
                          id={key}
                          value={config?.configValue || ''}
                          readOnly
                          className="flex-1 bg-muted"
                        />
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => handleEdit(key, config?.configValue || '')}
                        >
                          Edit
                        </Button>
                      </>
                    )}
                  </div>
                </div>
              );
            })}
          </CardContent>
        </Card>
      ))}
    </div>
  );
}
