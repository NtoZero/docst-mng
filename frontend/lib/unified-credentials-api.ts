import { useAuthStore } from '@/lib/store';
import { credentialsApi } from '@/lib/api';
import {
  listSystemCredentials,
  createSystemCredential,
  updateSystemCredential,
  deleteSystemCredential,
  listProjectCredentials,
  createProjectCredential,
  updateProjectCredential,
  deleteProjectCredential,
} from '@/lib/admin-api';
import type {
  Credential,
  SystemCredential,
  ProjectCredential,
  UnifiedCredential,
  CredentialScope,
  CreateUnifiedCredentialRequest,
  UpdateUnifiedCredentialRequest,
} from '@/lib/types';

/**
 * Get authentication token from store
 * @throws Error if not authenticated
 */
function getToken(): string {
  const token = useAuthStore.getState().token;
  if (!token) {
    throw new Error('Authentication required');
  }
  return token;
}

/**
 * Convert existing credential types to UnifiedCredential
 */
export function toUnifiedCredential(
  credential: Credential | SystemCredential | ProjectCredential,
  scope: CredentialScope,
  projectName?: string
): UnifiedCredential {
  return {
    id: credential.id,
    name: credential.name,
    type: credential.type,
    scope: 'scope' in credential ? credential.scope : scope,
    projectId: 'projectId' in credential ? credential.projectId : undefined,
    projectName,
    username: 'username' in credential ? credential.username : null,
    description: credential.description,
    active: credential.active,
    createdAt: credential.createdAt,
    updatedAt: credential.updatedAt,
  };
}

/**
 * Unified Credentials API facade
 * Provides a single interface for all three credential scopes (USER, SYSTEM, PROJECT)
 */
export const unifiedCredentialsApi = {
  /**
   * List credentials for a specific scope
   */
  async list(
    scope: CredentialScope,
    projectId?: string
  ): Promise<UnifiedCredential[]> {
    switch (scope) {
      case 'USER': {
        const credentials = await credentialsApi.list();
        return credentials.map((c) => toUnifiedCredential(c, 'USER'));
      }
      case 'SYSTEM': {
        const credentials = await listSystemCredentials(getToken());
        return credentials.map((c) => toUnifiedCredential(c, 'SYSTEM'));
      }
      case 'PROJECT': {
        if (!projectId) {
          throw new Error('projectId is required for PROJECT scope');
        }
        const credentials = await listProjectCredentials(getToken(), projectId);
        return credentials.map((c) => toUnifiedCredential(c, 'PROJECT'));
      }
      default:
        throw new Error(`Unknown scope: ${scope}`);
    }
  },

  /**
   * Create a new credential
   */
  async create(
    scope: CredentialScope,
    request: CreateUnifiedCredentialRequest,
    projectId?: string
  ): Promise<UnifiedCredential> {
    switch (scope) {
      case 'USER': {
        const credential = await credentialsApi.create({
          name: request.name,
          type: request.type,
          secret: request.secret,
          username: request.username,
          description: request.description,
        });
        return toUnifiedCredential(credential, 'USER');
      }
      case 'SYSTEM': {
        const credential = await createSystemCredential(getToken(), {
          name: request.name,
          type: request.type,
          secret: request.secret,
          description: request.description,
        });
        return toUnifiedCredential(credential, 'SYSTEM');
      }
      case 'PROJECT': {
        if (!projectId) {
          throw new Error('projectId is required for PROJECT scope');
        }
        const credential = await createProjectCredential(getToken(), projectId, {
          name: request.name,
          type: request.type,
          secret: request.secret,
          description: request.description,
        });
        return toUnifiedCredential(credential, 'PROJECT');
      }
      default:
        throw new Error(`Unknown scope: ${scope}`);
    }
  },

  /**
   * Update an existing credential
   */
  async update(
    scope: CredentialScope,
    id: string,
    request: UpdateUnifiedCredentialRequest,
    projectId?: string
  ): Promise<UnifiedCredential> {
    switch (scope) {
      case 'USER': {
        const credential = await credentialsApi.update(id, {
          secret: request.secret,
          username: request.username,
          description: request.description,
          active: request.active,
        });
        return toUnifiedCredential(credential, 'USER');
      }
      case 'SYSTEM': {
        const credential = await updateSystemCredential(getToken(), id, {
          secret: request.secret,
          description: request.description,
        });
        return toUnifiedCredential(credential, 'SYSTEM');
      }
      case 'PROJECT': {
        if (!projectId) {
          throw new Error('projectId is required for PROJECT scope');
        }
        const credential = await updateProjectCredential(
          getToken(),
          projectId,
          id,
          {
            secret: request.secret,
            description: request.description,
          }
        );
        return toUnifiedCredential(credential, 'PROJECT');
      }
      default:
        throw new Error(`Unknown scope: ${scope}`);
    }
  },

  /**
   * Delete a credential
   */
  async delete(
    scope: CredentialScope,
    id: string,
    projectId?: string
  ): Promise<void> {
    switch (scope) {
      case 'USER':
        await credentialsApi.delete(id);
        break;
      case 'SYSTEM':
        await deleteSystemCredential(getToken(), id);
        break;
      case 'PROJECT':
        if (!projectId) {
          throw new Error('projectId is required for PROJECT scope');
        }
        await deleteProjectCredential(getToken(), projectId, id);
        break;
      default:
        throw new Error(`Unknown scope: ${scope}`);
    }
  },
};
