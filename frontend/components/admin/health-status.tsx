'use client';

import { useQuery } from '@tanstack/react-query';
import { useAuthStore } from '@/lib/store';
import { getHealthCheck } from '@/lib/admin-api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { CheckCircle2, XCircle, AlertCircle, Loader2, RefreshCw } from 'lucide-react';
import { useTranslations } from 'next-intl';

export function HealthStatus() {
  const t = useTranslations('admin.health');
  const token = useAuthStore((state) => state.token);

  const {
    data: health,
    isLoading,
    error,
    refetch,
    isRefetching,
  } = useQuery({
    queryKey: ['admin', 'health'],
    queryFn: () => getHealthCheck(token!),
    enabled: !!token,
    refetchInterval: 30000, // Auto-refresh every 30 seconds
  });

  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'UP':
        return <CheckCircle2 className="h-5 w-5 text-green-500" />;
      case 'DOWN':
        return <XCircle className="h-5 w-5 text-red-500" />;
      case 'UNKNOWN':
        return <AlertCircle className="h-5 w-5 text-yellow-500" />;
      default:
        return <AlertCircle className="h-5 w-5 text-gray-500" />;
    }
  };

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'UP':
        return <Badge className="bg-green-500">{status}</Badge>;
      case 'DOWN':
        return <Badge variant="destructive">{status}</Badge>;
      case 'DEGRADED':
        return <Badge className="bg-yellow-500">{status}</Badge>;
      case 'UNKNOWN':
        return <Badge variant="secondary">{status}</Badge>;
      default:
        return <Badge variant="outline">{status}</Badge>;
    }
  };

  if (isLoading) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>System Health</CardTitle>
          <CardDescription>Checking system health...</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="flex items-center justify-center p-8">
            <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
          </div>
        </CardContent>
      </Card>
    );
  }

  if (error) {
    return (
      <Alert variant="destructive">
        <AlertDescription>
          Failed to check system health: {error.message}
        </AlertDescription>
      </Alert>
    );
  }

  if (!health) {
    return null;
  }

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between">
          <div>
            <CardTitle>System Health</CardTitle>
            <div className="flex items-center gap-2 mt-2 text-sm text-muted-foreground">
              <span>Overall Status:</span>
              {getStatusBadge(health.status)}
              <span className="text-xs">
                Last checked: {new Date(health.timestamp).toLocaleString()}
              </span>
            </div>
          </div>
          <Button
            variant="outline"
            size="sm"
            onClick={() => refetch()}
            disabled={isRefetching}
          >
            {isRefetching ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <RefreshCw className="h-4 w-4" />
            )}
          </Button>
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        {Object.entries(health.services).map(([serviceName, service]) => (
          <div
            key={serviceName}
            className="flex items-start gap-3 p-3 rounded-lg border bg-card"
          >
            <div className="mt-0.5">{getStatusIcon(service.status)}</div>
            <div className="flex-1 space-y-1">
              <div className="flex items-center justify-between">
                <h4 className="font-medium capitalize">{serviceName}</h4>
                {getStatusBadge(service.status)}
              </div>
              <p className="text-sm text-muted-foreground">{service.message}</p>
              {service.details && Object.keys(service.details).length > 0 && (
                <details className="text-xs">
                  <summary className="cursor-pointer text-muted-foreground hover:text-foreground">
                    Show details
                  </summary>
                  <div className="mt-2 p-2 bg-muted rounded font-mono">
                    {Object.entries(service.details).map(([key, value]) => (
                      <div key={key} className="flex gap-2">
                        <span className="text-muted-foreground">{key}:</span>
                        <span>{JSON.stringify(value)}</span>
                      </div>
                    ))}
                  </div>
                </details>
              )}
            </div>
          </div>
        ))}
      </CardContent>
    </Card>
  );
}
