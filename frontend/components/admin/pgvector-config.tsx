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
import { Loader2, CheckCircle2, XCircle, AlertCircle, ExternalLink, RefreshCw } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { Link } from '@/i18n/routing';
import { useToast } from '@/hooks/use-toast';

export function PgVectorConfig() {
  const t = useTranslations('admin');
  const { toast } = useToast();
  const { data: configs, isLoading: configsLoading, error: configsError } = useSystemConfigs();
  const { data: credentials, isLoading: credentialsLoading } = useSystemCredentials();
  const { data: health, refetch: refetchHealth } = useHealthCheck();
  const updateConfig = useUpdateSystemConfig();

  const [editingKey, setEditingKey] = useState<string | null>(null);
  const [editValue, setEditValue] = useState('');
  const [testingConnection, setTestingConnection] = useState(false);
  const [refreshing, setRefreshing] = useState(false);

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

  const handleTestConnection = async () => {
    setTestingConnection(true);
    try {
      const response = await fetch('/api/admin/pgvector/test-connection', {
        method: 'POST',
      });
      const result = await response.json();

      if (result.success) {
        toast.success('Connection Successful', {
          description: result.message,
        });
      } else {
        toast.error('Connection Failed', {
          description: result.message,
        });
      }

      refetchHealth();
    } catch (err) {
      toast.error('Connection Test Failed', {
        description: err instanceof Error ? err.message : 'Unknown error',
      });
    } finally {
      setTestingConnection(false);
    }
  };

  const handleRefresh = async () => {
    setRefreshing(true);
    try {
      const response = await fetch('/api/admin/pgvector/refresh', {
        method: 'POST',
      });
      const result = await response.json();

      if (result.success) {
        toast.success('Refresh Successful', {
          description: 'PgVector connection and caches refreshed',
        });
        refetchHealth();
      } else {
        toast.error('Refresh Failed', {
          description: result.message,
        });
      }
    } catch (err) {
      toast.error('Refresh Failed', {
        description: err instanceof Error ? err.message : 'Unknown error',
      });
    } finally {
      setRefreshing(false);
    }
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
          Failed to load PgVector configuration: {configsError.message}
        </AlertDescription>
      </Alert>
    );
  }

  const configMap = new Map(configs?.map((c) => [c.configKey, c]) || []);
  const enabled = configMap.get('pgvector.enabled');
  const host = configMap.get('pgvector.host');
  const port = configMap.get('pgvector.port');
  const database = configMap.get('pgvector.database');
  const schema = configMap.get('pgvector.schema');
  const table = configMap.get('pgvector.table');
  const dimensions = configMap.get('pgvector.dimensions');

  // PGVECTOR_AUTH 크리덴셜 필터링
  const pgvectorCredentials = credentials?.filter((c) => c.type === 'PGVECTOR_AUTH') || [];
  const activeCredential = pgvectorCredentials.find((c) => c.active);

  // PgVector Health Status
  const pgvectorHealth = health?.services['pgvector'];
  const isHealthy = pgvectorHealth?.status === 'UP';
  const healthIcon = isHealthy ? (
    <CheckCircle2 className="h-5 w-5 text-green-500" />
  ) : pgvectorHealth?.status === 'DOWN' ? (
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
              <CardTitle>PgVector Connection Status</CardTitle>
              <CardDescription>Current connection and credential information</CardDescription>
            </div>
            <div className="flex items-center gap-2">
              {healthIcon}
              <Button
                size="sm"
                variant="outline"
                onClick={handleTestConnection}
                disabled={testingConnection}
              >
                {testingConnection ? (
                  <Loader2 className="h-4 w-4 animate-spin mr-1" />
                ) : null}
                Test Connection
              </Button>
              <Button
                size="sm"
                variant="outline"
                onClick={handleRefresh}
                disabled={refreshing}
              >
                {refreshing ? (
                  <Loader2 className="h-4 w-4 animate-spin mr-1" />
                ) : (
                  <RefreshCw className="h-4 w-4 mr-1" />
                )}
                Refresh
              </Button>
            </div>
          </div>
        </CardHeader>
        <CardContent className="space-y-4">
          {/* Health Status */}
          <div className="flex items-center gap-2">
            <span className="text-sm font-medium">Status:</span>
            <Badge variant={isHealthy ? 'default' : 'destructive'}>
              {pgvectorHealth?.status || 'UNKNOWN'}
            </Badge>
            <span className="text-sm text-muted-foreground">{pgvectorHealth?.message}</span>
          </div>

          {/* Current Credential */}
          <div className="pt-2 border-t">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium">Active Credential</p>
                <p className="text-xs text-muted-foreground">
                  Currently used for PgVector database authentication
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
              <p className="text-sm font-medium">Available PGVECTOR_AUTH Credentials</p>
              <Link href="/admin/settings?tab=credentials">
                <Button variant="ghost" size="sm">
                  <ExternalLink className="h-4 w-4 mr-1" />
                  Manage Credentials
                </Button>
              </Link>
            </div>
            {pgvectorCredentials.length > 0 ? (
              <div className="space-y-1">
                {pgvectorCredentials.map((cred) => (
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
                  No PGVECTOR_AUTH credentials found. Create one in the{' '}
                  <Link href="/admin/settings?tab=credentials" className="underline">
                    Credentials tab
                  </Link>{' '}
                  with type <code>PGVECTOR_AUTH</code> and format:{' '}
                  <code className="text-xs">
                    {`{"username":"postgres","password":"your-password"}`}
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
          <CardTitle>PgVector Configuration</CardTitle>
          <CardDescription>PostgreSQL pgvector extension settings for semantic search</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {/* Enable Toggle */}
          <div className="flex items-center justify-between">
            <div className="space-y-0.5">
              <Label>Enable PgVector</Label>
              <p className="text-xs text-muted-foreground">Turn on vector-based semantic search</p>
            </div>
            <Switch
              checked={enabled?.configValue === 'true'}
              onCheckedChange={() =>
                handleToggle('pgvector.enabled', enabled?.configValue || 'false')
              }
              disabled={updateConfig.isPending}
            />
          </div>

          {/* Host */}
          <div className="grid gap-2">
            <Label htmlFor="pgvector.host" className="text-sm font-mono">
              pgvector.host
            </Label>
            <div className="flex gap-2">
              {editingKey === 'pgvector.host' ? (
                <>
                  <Input
                    id="pgvector.host"
                    value={editValue}
                    onChange={(e) => setEditValue(e.target.value)}
                    placeholder="localhost"
                    className="flex-1"
                  />
                  <Button
                    size="sm"
                    onClick={() => handleSave('pgvector.host')}
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
                    id="pgvector.host"
                    value={host?.configValue || 'localhost'}
                    readOnly
                    className="flex-1 bg-muted"
                  />
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => handleEdit('pgvector.host', host?.configValue || 'localhost')}
                  >
                    Edit
                  </Button>
                </>
              )}
            </div>
          </div>

          {/* Port */}
          <div className="grid gap-2">
            <Label htmlFor="pgvector.port" className="text-sm font-mono">
              pgvector.port
            </Label>
            <div className="flex gap-2">
              {editingKey === 'pgvector.port' ? (
                <>
                  <Input
                    id="pgvector.port"
                    type="number"
                    value={editValue}
                    onChange={(e) => setEditValue(e.target.value)}
                    className="flex-1"
                  />
                  <Button
                    size="sm"
                    onClick={() => handleSave('pgvector.port')}
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
                    id="pgvector.port"
                    value={port?.configValue || '5432'}
                    readOnly
                    className="flex-1 bg-muted"
                  />
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => handleEdit('pgvector.port', port?.configValue || '5432')}
                  >
                    Edit
                  </Button>
                </>
              )}
            </div>
          </div>

          {/* Database */}
          <div className="grid gap-2">
            <Label htmlFor="pgvector.database" className="text-sm font-mono">
              pgvector.database
            </Label>
            <p className="text-xs text-muted-foreground">Database name for vector store</p>
            <div className="flex gap-2">
              {editingKey === 'pgvector.database' ? (
                <>
                  <Input
                    id="pgvector.database"
                    value={editValue}
                    onChange={(e) => setEditValue(e.target.value)}
                    placeholder="docst_vector"
                    className="flex-1"
                  />
                  <Button
                    size="sm"
                    onClick={() => handleSave('pgvector.database')}
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
                    id="pgvector.database"
                    value={database?.configValue || 'docst_vector'}
                    readOnly
                    className="flex-1 bg-muted"
                  />
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => handleEdit('pgvector.database', database?.configValue || 'docst_vector')}
                  >
                    Edit
                  </Button>
                </>
              )}
            </div>
          </div>

          {/* Schema */}
          <div className="grid gap-2">
            <Label htmlFor="pgvector.schema" className="text-sm font-mono">
              pgvector.schema
            </Label>
            <div className="flex gap-2">
              {editingKey === 'pgvector.schema' ? (
                <>
                  <Input
                    id="pgvector.schema"
                    value={editValue}
                    onChange={(e) => setEditValue(e.target.value)}
                    placeholder="public"
                    className="flex-1"
                  />
                  <Button
                    size="sm"
                    onClick={() => handleSave('pgvector.schema')}
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
                    id="pgvector.schema"
                    value={schema?.configValue || 'public'}
                    readOnly
                    className="flex-1 bg-muted"
                  />
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => handleEdit('pgvector.schema', schema?.configValue || 'public')}
                  >
                    Edit
                  </Button>
                </>
              )}
            </div>
          </div>

          {/* Table */}
          <div className="grid gap-2">
            <Label htmlFor="pgvector.table" className="text-sm font-mono">
              pgvector.table
            </Label>
            <div className="flex gap-2">
              {editingKey === 'pgvector.table' ? (
                <>
                  <Input
                    id="pgvector.table"
                    value={editValue}
                    onChange={(e) => setEditValue(e.target.value)}
                    placeholder="vector_store"
                    className="flex-1"
                  />
                  <Button
                    size="sm"
                    onClick={() => handleSave('pgvector.table')}
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
                    id="pgvector.table"
                    value={table?.configValue || 'vector_store'}
                    readOnly
                    className="flex-1 bg-muted"
                  />
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => handleEdit('pgvector.table', table?.configValue || 'vector_store')}
                  >
                    Edit
                  </Button>
                </>
              )}
            </div>
          </div>

          {/* Dimensions */}
          <div className="grid gap-2">
            <Label htmlFor="pgvector.dimensions" className="text-sm font-mono">
              pgvector.dimensions
            </Label>
            <p className="text-xs text-muted-foreground">
              Embedding vector dimensions (OpenAI: 1536, Ollama nomic-embed-text: 768)
            </p>
            <div className="flex gap-2">
              {editingKey === 'pgvector.dimensions' ? (
                <>
                  <Input
                    id="pgvector.dimensions"
                    type="number"
                    value={editValue}
                    onChange={(e) => setEditValue(e.target.value)}
                    className="flex-1"
                  />
                  <Button
                    size="sm"
                    onClick={() => handleSave('pgvector.dimensions')}
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
                    id="pgvector.dimensions"
                    value={dimensions?.configValue || '1536'}
                    readOnly
                    className="flex-1 bg-muted"
                  />
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => handleEdit('pgvector.dimensions', dimensions?.configValue || '1536')}
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
