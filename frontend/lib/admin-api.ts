import {
  SystemConfig,
  UpdateSystemConfigRequest,
  SystemCredential,
  CreateSystemCredentialRequest,
  UpdateSystemCredentialRequest,
  ProjectCredential,
  CreateProjectCredentialRequest,
  UpdateProjectCredentialRequest,
  HealthCheckResponse,
} from './types';

const API_BASE = process.env.NEXT_PUBLIC_API_BASE || 'http://localhost:8342';

/**
 * 관리자 API 클라이언트.
 * Phase 4-E: 시스템 설정 및 크리덴셜 관리.
 */

// ===== System Config API =====

export async function getAllSystemConfigs(token: string): Promise<SystemConfig[]> {
  const response = await fetch(`${API_BASE}/api/admin/config`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });

  if (!response.ok) {
    throw new Error(`Failed to fetch system configs: ${response.statusText}`);
  }

  return response.json();
}

export async function getSystemConfig(
  token: string,
  key: string
): Promise<SystemConfig> {
  const response = await fetch(`${API_BASE}/api/admin/config/${encodeURIComponent(key)}`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });

  if (!response.ok) {
    throw new Error(`Failed to fetch system config: ${response.statusText}`);
  }

  return response.json();
}

export async function updateSystemConfig(
  token: string,
  key: string,
  request: UpdateSystemConfigRequest
): Promise<SystemConfig> {
  const response = await fetch(`${API_BASE}/api/admin/config/${encodeURIComponent(key)}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    throw new Error(`Failed to update system config: ${response.statusText}`);
  }

  return response.json();
}

export async function refreshSystemConfigCache(token: string): Promise<string> {
  const response = await fetch(`${API_BASE}/api/admin/config/refresh`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });

  if (!response.ok) {
    throw new Error(`Failed to refresh cache: ${response.statusText}`);
  }

  return response.text();
}

// ===== System Credential API =====

export async function listSystemCredentials(
  token: string
): Promise<SystemCredential[]> {
  const response = await fetch(`${API_BASE}/api/admin/credentials`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });

  if (!response.ok) {
    throw new Error(`Failed to list system credentials: ${response.statusText}`);
  }

  return response.json();
}

export async function getSystemCredential(
  token: string,
  id: string
): Promise<SystemCredential> {
  const response = await fetch(`${API_BASE}/api/admin/credentials/${id}`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });

  if (!response.ok) {
    throw new Error(`Failed to get system credential: ${response.statusText}`);
  }

  return response.json();
}

export async function createSystemCredential(
  token: string,
  request: CreateSystemCredentialRequest
): Promise<SystemCredential> {
  const response = await fetch(`${API_BASE}/api/admin/credentials`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    throw new Error(`Failed to create system credential: ${response.statusText}`);
  }

  return response.json();
}

export async function updateSystemCredential(
  token: string,
  id: string,
  request: UpdateSystemCredentialRequest
): Promise<SystemCredential> {
  const response = await fetch(`${API_BASE}/api/admin/credentials/${id}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    throw new Error(`Failed to update system credential: ${response.statusText}`);
  }

  return response.json();
}

export async function deleteSystemCredential(
  token: string,
  id: string
): Promise<void> {
  const response = await fetch(`${API_BASE}/api/admin/credentials/${id}`, {
    method: 'DELETE',
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });

  if (!response.ok) {
    throw new Error(`Failed to delete system credential: ${response.statusText}`);
  }
}

// ===== Project Credential API =====

export async function listProjectCredentials(
  token: string,
  projectId: string
): Promise<ProjectCredential[]> {
  const response = await fetch(
    `${API_BASE}/api/projects/${projectId}/credentials`,
    {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    }
  );

  if (!response.ok) {
    throw new Error(`Failed to list project credentials: ${response.statusText}`);
  }

  return response.json();
}

export async function getProjectCredential(
  token: string,
  projectId: string,
  credentialId: string
): Promise<ProjectCredential> {
  const response = await fetch(
    `${API_BASE}/api/projects/${projectId}/credentials/${credentialId}`,
    {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    }
  );

  if (!response.ok) {
    throw new Error(`Failed to get project credential: ${response.statusText}`);
  }

  return response.json();
}

export async function createProjectCredential(
  token: string,
  projectId: string,
  request: CreateProjectCredentialRequest
): Promise<ProjectCredential> {
  const response = await fetch(
    `${API_BASE}/api/projects/${projectId}/credentials`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify(request),
    }
  );

  if (!response.ok) {
    throw new Error(`Failed to create project credential: ${response.statusText}`);
  }

  return response.json();
}

export async function updateProjectCredential(
  token: string,
  projectId: string,
  credentialId: string,
  request: UpdateProjectCredentialRequest
): Promise<ProjectCredential> {
  const response = await fetch(
    `${API_BASE}/api/projects/${projectId}/credentials/${credentialId}`,
    {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
      },
      body: JSON.stringify(request),
    }
  );

  if (!response.ok) {
    throw new Error(`Failed to update project credential: ${response.statusText}`);
  }

  return response.json();
}

export async function deleteProjectCredential(
  token: string,
  projectId: string,
  credentialId: string
): Promise<void> {
  const response = await fetch(
    `${API_BASE}/api/projects/${projectId}/credentials/${credentialId}`,
    {
      method: 'DELETE',
      headers: {
        Authorization: `Bearer ${token}`,
      },
    }
  );

  if (!response.ok) {
    throw new Error(`Failed to delete project credential: ${response.statusText}`);
  }
}

// ===== Health Check API =====

export async function getHealthCheck(token: string): Promise<HealthCheckResponse> {
  const response = await fetch(`${API_BASE}/api/admin/health`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });

  if (!response.ok) {
    throw new Error(`Failed to get health check: ${response.statusText}`);
  }

  return response.json();
}
