'use client';

import { useState } from 'react';
import {
  useSystemConfigs,
  useUpdateSystemConfig,
  useSystemCredentials,
  useHealthCheck,
} from '@/hooks/use-admin-config';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { Badge } from '@/components/ui/badge';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Loader2, CheckCircle2, XCircle, AlertCircle, ExternalLink } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { Link } from '@/i18n/routing';

export function Neo4jConfig() {
  const t = useTranslations('admin');
  const { data: configs, isLoading: configsLoading, error: configsError } = useSystemConfigs();
  const { data: credentials, isLoading: credentialsLoading } = useSystemCredentials();
  const { data: health } = useHealthCheck();
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

  const handleToggle = async (key: string, currentValue: string) => {
    const newValue = currentValue === 'true' ? 'false' : 'true';
    try {
      await updateConfig.mutateAsync({
        key,
        request: { configValue: newValue },
      });
    } catch (err) {
      console.error('Failed to toggle config:', err);
    }
  };

  const handleCancel = () => {
    setEditingKey(null);
    setEditValue('');
  };

  if (configsLoading || credentialsLoading) {
    return (
      <div className="flex items-center justify-center p-8">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (configsError) {
    return (
      <Alert variant="destructive">
        <AlertDescription>
          Failed to load Neo4j configuration: {configsError.message}
        </AlertDescription>
      </Alert>
    );
  }

  const configMap = new Map(configs?.map((c) => [c.configKey, c]) || []);
  const enabled = configMap.get('neo4j.enabled');
  const uri = configMap.get('neo4j.uri');
  const maxHop = configMap.get('neo4j.max-hop');
  const entityModel = configMap.get('neo4j.entity-extraction-model');

  // NEO4J_AUTH 크리덴셜 필터링
  const neo4jCredentials = credentials?.filter((c) => c.type === 'NEO4J_AUTH') || [];
  const activeCredential = neo4jCredentials.find((c) => c.active);

  // Neo4j Health Status
  const neo4jHealth = health?.services['neo4j'];
  const isHealthy = neo4jHealth?.status === 'UP';
  const healthIcon = isHealthy ? (
    <CheckCircle2 className="h-5 w-5 text-green-500" />
  ) : neo4jHealth?.status === 'DOWN' ? (
    <XCircle className="h-5 w-5 text-red-500" />
  ) : (
    <AlertCircle className="h-5 w-5 text-yellow-500" />
  );

  return (
    <div className="space-y-6">
      {/* Connection Status Card */}
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <div>
              <CardTitle>Neo4j Connection Status</CardTitle>
              <CardDescription>Current connection and credential information</CardDescription>
            </div>
            {healthIcon}
          </div>
        </CardHeader>
        <CardContent className="space-y-4">
          {/* Health Status */}
          <div className="flex items-center gap-2">
            <span className="text-sm font-medium">Status:</span>
            <Badge variant={isHealthy ? 'default' : 'destructive'}>
              {neo4jHealth?.status || 'UNKNOWN'}
            </Badge>
            <span className="text-sm text-muted-foreground">{neo4jHealth?.message}</span>
          </div>

          {/* Neo4j Version (if healthy) */}
          {isHealthy && neo4jHealth?.details && (
            <div className="grid gap-2 text-sm">
              {neo4jHealth.details.version && (
                <div className="flex gap-2">
                  <span className="text-muted-foreground">Version:</span>
                  <span>{neo4jHealth.details.version}</span>
                </div>
              )}
              {neo4jHealth.details.edition && (
                <div className="flex gap-2">
                  <span className="text-muted-foreground">Edition:</span>
                  <span>{neo4jHealth.details.edition}</span>
                </div>
              )}
            </div>
          )}

          {/* Current Credential */}
          <div className="pt-2 border-t">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium">Active Credential</p>
                <p className="text-xs text-muted-foreground">
                  Currently used for Neo4j authentication
                </p>
              </div>
              {activeCredential ? (
                <div className="flex items-center gap-2">
                  <Badge variant="outline">{activeCredential.name}</Badge>
                  <Badge className="bg-green-500">Active</Badge>
                </div>
              ) : (
                <Badge variant="destructive">No credential configured</Badge>
              )}
            </div>
          </div>

          {/* Available Credentials */}
          <div className="pt-2 border-t">
            <div className="flex items-center justify-between mb-2">
              <p className="text-sm font-medium">Available NEO4J_AUTH Credentials</p>
              <Link href="/admin/settings?tab=credentials">
                <Button variant="ghost" size="sm">
                  <ExternalLink className="h-4 w-4 mr-1" />
                  Manage Credentials
                </Button>
              </Link>
            </div>
            {neo4jCredentials.length > 0 ? (
              <div className="space-y-1">
                {neo4jCredentials.map((cred) => (
                  <div
                    key={cred.id}
                    className="flex items-center gap-2 text-sm p-2 rounded border"
                  >
                    <span className="flex-1">{cred.name}</span>
                    {cred.description && (
                      <span className="text-xs text-muted-foreground">{cred.description}</span>
                    )}
                    {cred.active ? (
                      <Badge className="bg-green-500">In Use</Badge>
                    ) : (
                      <Badge variant="secondary">Inactive</Badge>
                    )}
                  </div>
                ))}
              </div>
            ) : (
              <Alert>
                <AlertDescription>
                  No NEO4J_AUTH credentials found. Create one in the{' '}
                  <Link href="/admin/settings?tab=credentials" className="underline">
                    Credentials tab
                  </Link>{' '}
                  with type <code>NEO4J_AUTH</code> and format:{' '}
                  <code className="text-xs">
                    {`{"username":"neo4j","password":"your-password"}`}
                  </code>
                </AlertDescription>
              </Alert>
            )}
          </div>
        </CardContent>
      </Card>

      {/* Configuration Card */}
      <Card>
        <CardHeader>
          <CardTitle>Neo4j Configuration</CardTitle>
          <CardDescription>Graph database settings for knowledge graph RAG</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {/* Enable Toggle */}
          <div className="flex items-center justify-between">
            <div className="space-y-0.5">
              <Label>Enable Neo4j</Label>
              <p className="text-xs text-muted-foreground">Turn on graph-based retrieval</p>
            </div>
            <Switch
              checked={enabled?.configValue === 'true'}
              onCheckedChange={() =>
                handleToggle('neo4j.enabled', enabled?.configValue || 'false')
              }
              disabled={updateConfig.isPending}
            />
          </div>

          {/* URI */}
          <div className="grid gap-2">
            <Label htmlFor="neo4j.uri" className="text-sm font-mono">
              neo4j.uri
            </Label>
            <div className="flex gap-2">
              {editingKey === 'neo4j.uri' ? (
                <>
                  <Input
                    id="neo4j.uri"
                    value={editValue}
                    onChange={(e) => setEditValue(e.target.value)}
                    placeholder="bolt://localhost:7687"
                    className="flex-1"
                  />
                  <Button
                    size="sm"
                    onClick={() => handleSave('neo4j.uri')}
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
                    id="neo4j.uri"
                    value={uri?.configValue || ''}
                    readOnly
                    className="flex-1 bg-muted"
                  />
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => handleEdit('neo4j.uri', uri?.configValue || '')}
                  >
                    Edit
                  </Button>
                </>
              )}
            </div>
          </div>

          {/* Max Hop */}
          <div className="grid gap-2">
            <Label htmlFor="neo4j.max-hop" className="text-sm font-mono">
              neo4j.max-hop
            </Label>
            <p className="text-xs text-muted-foreground">Maximum graph traversal depth</p>
            <div className="flex gap-2">
              {editingKey === 'neo4j.max-hop' ? (
                <>
                  <Input
                    id="neo4j.max-hop"
                    type="number"
                    value={editValue}
                    onChange={(e) => setEditValue(e.target.value)}
                    className="flex-1"
                  />
                  <Button
                    size="sm"
                    onClick={() => handleSave('neo4j.max-hop')}
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
                    id="neo4j.max-hop"
                    value={maxHop?.configValue || '2'}
                    readOnly
                    className="flex-1 bg-muted"
                  />
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => handleEdit('neo4j.max-hop', maxHop?.configValue || '2')}
                  >
                    Edit
                  </Button>
                </>
              )}
            </div>
          </div>

          {/* Entity Extraction Model */}
          <div className="grid gap-2">
            <Label htmlFor="neo4j.entity-extraction-model" className="text-sm font-mono">
              neo4j.entity-extraction-model
            </Label>
            <p className="text-xs text-muted-foreground">LLM model for entity extraction</p>
            <div className="flex gap-2">
              {editingKey === 'neo4j.entity-extraction-model' ? (
                <>
                  <Input
                    id="neo4j.entity-extraction-model"
                    value={editValue}
                    onChange={(e) => setEditValue(e.target.value)}
                    placeholder="gpt-4o-mini"
                    className="flex-1"
                  />
                  <Button
                    size="sm"
                    onClick={() => handleSave('neo4j.entity-extraction-model')}
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
                    id="neo4j.entity-extraction-model"
                    value={entityModel?.configValue || 'gpt-4o-mini'}
                    readOnly
                    className="flex-1 bg-muted"
                  />
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() =>
                      handleEdit(
                        'neo4j.entity-extraction-model',
                        entityModel?.configValue || 'gpt-4o-mini'
                      )
                    }
                  >
                    Edit
                  </Button>
                </>
              )}
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
