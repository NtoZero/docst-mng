import type { CredentialType, CredentialScope } from '@/lib/types';
import type { GuideKey } from '@/lib/types/guide';

export interface CredentialTypeConfig {
  label: string;
  scopes: CredentialScope[];
  secretLabel: string;
  placeholder: string;
  helpUrl?: string;
  guideKey?: GuideKey;
  isJsonAuth?: boolean;
  fields?: string[];
}

export const CREDENTIAL_TYPE_CONFIG: Record<CredentialType, CredentialTypeConfig> = {
  GITHUB_PAT: {
    label: 'GitHub PAT',
    scopes: ['USER', 'PROJECT'],
    secretLabel: 'Personal Access Token',
    placeholder: 'ghp_xxxxxxxxxxxxxxxxxxxx',
    helpUrl: 'https://github.com/settings/tokens',
    guideKey: 'github_pat',
  },
  BASIC_AUTH: {
    label: 'Basic Auth',
    scopes: ['USER', 'PROJECT'],
    secretLabel: 'Password',
    placeholder: '',
  },
  SSH_KEY: {
    label: 'SSH Key',
    scopes: ['USER', 'PROJECT'],
    secretLabel: 'Private Key',
    placeholder: '-----BEGIN OPENSSH PRIVATE KEY-----',
  },
  OPENAI_API_KEY: {
    label: 'OpenAI API Key',
    scopes: ['SYSTEM', 'PROJECT'],
    secretLabel: 'API Key',
    placeholder: 'sk-proj-...',
    helpUrl: 'https://platform.openai.com/api-keys',
    guideKey: 'openai_api_key',
  },
  ANTHROPIC_API_KEY: {
    label: 'Anthropic API Key',
    scopes: ['SYSTEM', 'PROJECT'],
    secretLabel: 'API Key',
    placeholder: 'sk-ant-...',
    helpUrl: 'https://console.anthropic.com/settings/keys',
    guideKey: 'anthropic_api_key',
  },
  NEO4J_AUTH: {
    label: 'Neo4j Auth',
    scopes: ['SYSTEM', 'PROJECT'],
    secretLabel: 'Credentials',
    placeholder: '',
    isJsonAuth: true,
    fields: ['username', 'password'],
    guideKey: 'neo4j_auth',
  },
  PGVECTOR_AUTH: {
    label: 'PgVector Auth',
    scopes: ['SYSTEM', 'PROJECT'],
    secretLabel: 'Credentials',
    placeholder: '',
    isJsonAuth: true,
    fields: ['username', 'password'],
    guideKey: 'pgvector_auth',
  },
  CUSTOM_API_KEY: {
    label: 'Custom API Key',
    scopes: ['SYSTEM', 'PROJECT'],
    secretLabel: 'API Key',
    placeholder: 'your-api-key',
  },
};

/**
 * Get available credential types for a specific scope
 */
export function getTypesForScope(scope: CredentialScope): CredentialType[] {
  return (
    Object.entries(CREDENTIAL_TYPE_CONFIG) as [CredentialType, CredentialTypeConfig][]
  )
    .filter(([, config]) => config.scopes.includes(scope))
    .map(([type]) => type);
}

/**
 * Check if credential type uses JSON auth (username/password fields)
 */
export function isJsonAuthType(type: CredentialType): boolean {
  return CREDENTIAL_TYPE_CONFIG[type]?.isJsonAuth ?? false;
}

/**
 * Get label for credential type
 */
export function getCredentialTypeLabel(type: CredentialType): string {
  return CREDENTIAL_TYPE_CONFIG[type]?.label ?? type;
}

/**
 * Get secret field label for credential type
 */
export function getSecretLabel(type: CredentialType): string {
  return CREDENTIAL_TYPE_CONFIG[type]?.secretLabel ?? 'Secret';
}

/**
 * Get placeholder for secret field
 */
export function getSecretPlaceholder(type: CredentialType): string {
  return CREDENTIAL_TYPE_CONFIG[type]?.placeholder ?? '';
}

/**
 * Get help URL for credential type (if available)
 */
export function getHelpUrl(type: CredentialType): string | undefined {
  return CREDENTIAL_TYPE_CONFIG[type]?.helpUrl;
}

/**
 * Get guide key for credential type (if available)
 */
export function getGuideKey(type: CredentialType): GuideKey | undefined {
  return CREDENTIAL_TYPE_CONFIG[type]?.guideKey;
}
