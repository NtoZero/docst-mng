'use client';

import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Shield, User, FolderKanban } from 'lucide-react';
import type { CredentialScope } from '@/lib/types';

interface CredentialScopeTabsProps {
  activeScope: CredentialScope;
  onScopeChange: (scope: CredentialScope) => void;
  isAdmin: boolean;
}

export function CredentialScopeTabs({
  activeScope,
  onScopeChange,
  isAdmin,
}: CredentialScopeTabsProps) {
  return (
    <Tabs
      value={activeScope}
      onValueChange={(value) => onScopeChange(value as CredentialScope)}
    >
      <TabsList>
        <TabsTrigger value="USER" className="gap-2">
          <User className="h-4 w-4" />
          User
        </TabsTrigger>
        {isAdmin && (
          <TabsTrigger value="SYSTEM" className="gap-2">
            <Shield className="h-4 w-4" />
            System
          </TabsTrigger>
        )}
        <TabsTrigger value="PROJECT" className="gap-2">
          <FolderKanban className="h-4 w-4" />
          Project
        </TabsTrigger>
      </TabsList>
    </Tabs>
  );
}
