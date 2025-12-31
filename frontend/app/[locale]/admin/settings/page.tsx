'use client';

import { useState } from 'react';
import { SystemConfigForm } from '@/components/admin/system-config-form';
import { CredentialList } from '@/components/admin/credential-list';
import { CredentialFormDialog } from '@/components/admin/credential-form-dialog';
import { Neo4jConfig } from '@/components/admin/neo4j-config';
import { HealthStatus } from '@/components/admin/health-status';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useTranslations } from 'next-intl';
import { SystemCredential } from '@/lib/types';

export default function AdminSettingsPage() {
  const t = useTranslations('admin');
  const [credentialDialogOpen, setCredentialDialogOpen] = useState(false);
  const [editingCredential, setEditingCredential] = useState<SystemCredential | null>(
    null
  );

  const handleCreateCredential = () => {
    setEditingCredential(null);
    setCredentialDialogOpen(true);
  };

  const handleEditCredential = (credential: SystemCredential) => {
    setEditingCredential(credential);
    setCredentialDialogOpen(true);
  };

  const handleDialogClose = () => {
    setCredentialDialogOpen(false);
    setEditingCredential(null);
  };

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
          <TabsTrigger value="neo4j">Neo4j</TabsTrigger>
          <TabsTrigger value="health">Health</TabsTrigger>
        </TabsList>

        <TabsContent value="config" className="space-y-4">
          <SystemConfigForm />
        </TabsContent>

        <TabsContent value="credentials" className="space-y-4">
          <CredentialList
            onCreateClick={handleCreateCredential}
            onEditClick={handleEditCredential}
          />
        </TabsContent>

        <TabsContent value="neo4j" className="space-y-4">
          <Neo4jConfig />
        </TabsContent>

        <TabsContent value="health" className="space-y-4">
          <HealthStatus />
        </TabsContent>
      </Tabs>

      <CredentialFormDialog
        open={credentialDialogOpen}
        onOpenChange={handleDialogClose}
        credential={editingCredential}
      />
    </div>
  );
}
