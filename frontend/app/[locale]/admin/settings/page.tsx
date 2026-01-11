'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { ExternalLink } from 'lucide-react';
import { SystemConfigForm } from '@/components/admin/system-config-form';
import { Neo4jConfig } from '@/components/admin/neo4j-config';
import { PgVectorConfig } from '@/components/admin/pgvector-config';
import { HealthStatus } from '@/components/admin/health-status';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { useTranslations } from 'next-intl';

export default function AdminSettingsPage() {
  const t = useTranslations('admin');
  const params = useParams();
  const locale = params.locale as string;

  return (
    <div className="container mx-auto py-8 space-y-6">
      <div>
        <h1 className="text-3xl font-bold">System Settings</h1>
        <p className="text-muted-foreground mt-2">
          Manage system-wide configuration and credentials (Admin only)
        </p>
      </div>

      <Tabs defaultValue="config" className="space-y-4">
        <TabsList>
          <TabsTrigger value="config">Configuration</TabsTrigger>
          <TabsTrigger value="credentials">Credentials</TabsTrigger>
          <TabsTrigger value="pgvector">PgVector</TabsTrigger>
          <TabsTrigger value="neo4j">Neo4j</TabsTrigger>
          <TabsTrigger value="health">Health</TabsTrigger>
        </TabsList>

        <TabsContent value="config" className="space-y-4">
          <SystemConfigForm />
        </TabsContent>

        <TabsContent value="credentials" className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle>System Credentials</CardTitle>
              <CardDescription>
                System credentials have been moved to the unified Credentials management page.
              </CardDescription>
            </CardHeader>
            <CardContent>
              <Button asChild>
                <Link href={`/${locale}/settings/credentials?scope=system`}>
                  Go to Credentials
                  <ExternalLink className="ml-2 h-4 w-4" />
                </Link>
              </Button>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="pgvector" className="space-y-4">
          <PgVectorConfig />
        </TabsContent>

        <TabsContent value="neo4j" className="space-y-4">
          <Neo4jConfig />
        </TabsContent>

        <TabsContent value="health" className="space-y-4">
          <HealthStatus />
        </TabsContent>
      </Tabs>
    </div>
  );
}
