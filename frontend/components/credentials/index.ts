// Credential UI Components - Phase 9
export { CredentialScopeTabs } from './credential-scope-tabs';
export { ProjectSelector } from './project-selector';
export { CredentialFormDialog } from './credential-form-dialog';
export { CredentialCard } from './credential-card';
export { CredentialTable } from './credential-table';
export { CredentialListView } from './credential-list-view';

// Type config utilities
export {
  CREDENTIAL_TYPE_CONFIG,
  getTypesForScope,
  isJsonAuthType,
  getCredentialTypeLabel,
  getSecretLabel,
  getSecretPlaceholder,
  getHelpUrl,
} from './credential-type-config';
export type { CredentialTypeConfig } from './credential-type-config';
