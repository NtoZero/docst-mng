# Credential 관리 UI 일원화 계획

## 목표
분산된 Credential 관리 UI (4곳)를 **단일 통합 페이지**로 일원화

## 현재 상태 (문제점)

| 위치 | 스코프 | 지원 타입 | CRUD 지원 | 문제 |
|------|--------|----------|-----------|------|
| `/credentials` | USER | 3개 (GITHUB_PAT, BASIC_AUTH, SSH_KEY) | Full CRUD | 개별 페이지 |
| `/projects/{id}/settings/credentials` | PROJECT | 8개 | Create/Delete만 | 프로젝트별 분산, Update 미지원 |
| `/admin/settings` (Credentials 탭) | SYSTEM | 5개 | Full CRUD | Admin 설정에 혼재 |
| `/settings/api-keys` | MCP | - | Create/Revoke | **제외** (별도 유지) |

### 지원 타입 상세

| CredentialType | USER | SYSTEM | PROJECT | 특이사항 |
|----------------|------|--------|---------|----------|
| `GITHUB_PAT` | ✅ | - | ✅ | Git 인증용 |
| `BASIC_AUTH` | ✅ | - | ✅ | Git 인증용 |
| `SSH_KEY` | ✅ | - | ✅ | Git 인증용 |
| `OPENAI_API_KEY` | - | ✅ | ✅ | LLM 서비스 |
| `ANTHROPIC_API_KEY` | - | ✅ | ✅ | LLM 서비스 |
| `NEO4J_AUTH` | - | ✅ | ✅ | JSON 필드 (username/password) |
| `PGVECTOR_AUTH` | - | ✅ | ✅ | JSON 필드 (username/password) |
| `CUSTOM_API_KEY` | - | ✅ | ✅ | 범용 |

---

## 통합 후 구조

**새 경로**: `/settings/credentials`

```
/settings/credentials                    # 기본: USER 탭
/settings/credentials?scope=user         # USER 스코프
/settings/credentials?scope=system       # SYSTEM 스코프 (Admin 전용)
/settings/credentials?scope=project&projectId={uuid}  # PROJECT 스코프
```

### CRUD 지원 (통합 후)

| 스코프 | Create | Read | Update | Delete |
|--------|--------|------|--------|--------|
| USER | ✅ | ✅ | ✅ | ✅ |
| SYSTEM | ✅ | ✅ | ✅ | ✅ |
| PROJECT | ✅ | ✅ | ✅ (신규) | ✅ |

> **결정**: PROJECT 스코프도 Full CRUD 지원 (Backend API 이미 존재)

---

## UI 레이아웃

```
+------------------------------------------------------------------+
| Settings > Credentials                                            |
| Manage authentication credentials for services and repositories   |
+------------------------------------------------------------------+
| [USER] [SYSTEM*] [PROJECT*]                    [+ Add Credential] |
+------------------------------------------------------------------+
|                                                                   |
| USER Tab: 카드 그리드                                              |
| +-------------------+  +-------------------+                      |
| | my-github-token   |  | gitlab-token      |                      |
| | GitHub PAT        |  | Basic Auth        |                      |
| | [Edit] [Delete]   |  | [Edit] [Delete]   |                      |
| +-------------------+  +-------------------+                      |
|                                                                   |
| SYSTEM Tab (Admin only): 테이블                                   |
| +----------------------------------------------------------+     |
| | Name        | Type           | Status | Actions          |     |
| | openai-main | OPENAI_API_KEY | Active | [Edit] [Delete]  |     |
| +----------------------------------------------------------+     |
|                                                                   |
| PROJECT Tab: 프로젝트 선택 드롭다운 + 테이블                        |
| [Project Selector v]                                              |
| +----------------------------------------------------------+     |
| | Name         | Type           | Status | Actions         |     |
| | proj-openai  | OPENAI_API_KEY | Active | [Edit] [Delete] |     |
| +----------------------------------------------------------+     |
+------------------------------------------------------------------+
```

---

## 권한 모델

| 역할 | USER | SYSTEM | PROJECT |
|------|------|--------|---------|
| 일반 사용자 | 자신의 것만 (RW) | 탭 숨김 | 소속 프로젝트만 (Admin 역할시 RW) |
| Admin | 자신의 것만 (RW) | 전체 (RW) | 전체 프로젝트 (RW) |

### 권한 체크 구현

```typescript
// useAuthStore에서 사용자 정보 확인
const { user } = useAuthStore();
const isAdmin = user?.role === 'ADMIN';

// 탭 표시 로직
const visibleTabs = [
  { value: 'user', label: 'USER' },
  ...(isAdmin ? [{ value: 'system', label: 'SYSTEM' }] : []),
  { value: 'project', label: 'PROJECT' },
];

// 프로젝트 목록: useProjects() 훅 사용 (소속 프로젝트만 반환됨)
```

---

## 구현 단계

### Phase 1: 공통 인프라 (기반 작업)

**1.1 타입 설정 파일 생성**
- 파일: `frontend/components/credentials/credential-type-config.ts`
- 내용: 각 CredentialType별 메타데이터 (label, scopes, placeholder, helpUrl 등)

```typescript
import { CredentialType, CredentialScope } from '@/lib/types';

export interface CredentialTypeConfig {
  label: string;
  scopes: CredentialScope[];
  secretLabel: string;
  placeholder: string;
  helpUrl?: string;
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
  },
  BASIC_AUTH: {
    label: 'Basic Auth',
    scopes: ['USER', 'PROJECT'],
    secretLabel: 'Password',
    placeholder: '••••••••',
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
  },
  ANTHROPIC_API_KEY: {
    label: 'Anthropic API Key',
    scopes: ['SYSTEM', 'PROJECT'],
    secretLabel: 'API Key',
    placeholder: 'sk-ant-...',
    helpUrl: 'https://console.anthropic.com/settings/keys',
  },
  NEO4J_AUTH: {
    label: 'Neo4j Auth',
    scopes: ['SYSTEM', 'PROJECT'],
    secretLabel: 'Credentials',
    placeholder: '',
    isJsonAuth: true,
    fields: ['username', 'password'],
  },
  PGVECTOR_AUTH: {
    label: 'PgVector Auth',
    scopes: ['SYSTEM', 'PROJECT'],
    secretLabel: 'Credentials',
    placeholder: '',
    isJsonAuth: true,
    fields: ['username', 'password'],
  },
  CUSTOM_API_KEY: {
    label: 'Custom API Key',
    scopes: ['SYSTEM', 'PROJECT'],
    secretLabel: 'API Key',
    placeholder: 'your-api-key',
  },
};

// 스코프별 사용 가능 타입 필터링 유틸리티
export function getTypesForScope(scope: CredentialScope): CredentialType[] {
  return (Object.entries(CREDENTIAL_TYPE_CONFIG) as [CredentialType, CredentialTypeConfig][])
    .filter(([_, config]) => config.scopes.includes(scope))
    .map(([type]) => type);
}

// JSON 인증 타입 여부 확인
export function isJsonAuthType(type: CredentialType): boolean {
  return CREDENTIAL_TYPE_CONFIG[type]?.isJsonAuth ?? false;
}
```

**1.2 통합 타입 추가**
- 파일: `frontend/lib/types.ts`
- 추가: `UnifiedCredential` 인터페이스 및 변환 유틸리티

```typescript
// 기존 타입 (변경 없음)
export interface Credential { /* USER 스코프 */ }
export interface SystemCredential { /* SYSTEM 스코프 */ }
export interface ProjectCredential { /* PROJECT 스코프 */ }

// 신규: 통합 타입
export interface UnifiedCredential {
  id: string;
  name: string;
  type: CredentialType;
  scope: CredentialScope;
  projectId?: string;
  projectName?: string;
  username?: string | null;  // USER 스코프에서만 사용
  description: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string | null;
}

// 통합 요청 타입
export interface CreateUnifiedCredentialRequest {
  name: string;
  type: CredentialType;
  secret: string;
  username?: string;  // USER 스코프 BASIC_AUTH용
  description?: string;
}

export interface UpdateUnifiedCredentialRequest {
  secret?: string;
  username?: string;
  description?: string;
  active?: boolean;
}
```

**1.3 API 파사드 생성**
- 파일: `frontend/lib/unified-credentials-api.ts`
- 역할: 3개 API 엔드포인트를 단일 인터페이스로 통합
- **중요**: 토큰 처리 통일 (기존 `api.ts`와 `admin-api.ts` 방식 차이 해소)

```typescript
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

// 토큰 가져오기 헬퍼 (통일된 방식)
function getToken(): string {
  const token = useAuthStore.getState().token;
  if (!token) throw new Error('Authentication required');
  return token;
}

// 기존 타입 → UnifiedCredential 변환
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

// 통합 API 인터페이스
export const unifiedCredentialsApi = {
  // 목록 조회
  async list(scope: CredentialScope, projectId?: string): Promise<UnifiedCredential[]> {
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
        if (!projectId) throw new Error('projectId required for PROJECT scope');
        const credentials = await listProjectCredentials(getToken(), projectId);
        return credentials.map((c) => toUnifiedCredential(c, 'PROJECT'));
      }
    }
  },

  // 생성
  async create(
    scope: CredentialScope,
    request: CreateUnifiedCredentialRequest,
    projectId?: string
  ): Promise<UnifiedCredential> {
    switch (scope) {
      case 'USER': {
        const credential = await credentialsApi.create(request);
        return toUnifiedCredential(credential, 'USER');
      }
      case 'SYSTEM': {
        const credential = await createSystemCredential(getToken(), request);
        return toUnifiedCredential(credential, 'SYSTEM');
      }
      case 'PROJECT': {
        if (!projectId) throw new Error('projectId required for PROJECT scope');
        const credential = await createProjectCredential(getToken(), projectId, request);
        return toUnifiedCredential(credential, 'PROJECT');
      }
    }
  },

  // 수정
  async update(
    scope: CredentialScope,
    id: string,
    request: UpdateUnifiedCredentialRequest,
    projectId?: string
  ): Promise<UnifiedCredential> {
    switch (scope) {
      case 'USER': {
        const credential = await credentialsApi.update(id, request);
        return toUnifiedCredential(credential, 'USER');
      }
      case 'SYSTEM': {
        const credential = await updateSystemCredential(getToken(), id, request);
        return toUnifiedCredential(credential, 'SYSTEM');
      }
      case 'PROJECT': {
        if (!projectId) throw new Error('projectId required for PROJECT scope');
        const credential = await updateProjectCredential(getToken(), projectId, id, request);
        return toUnifiedCredential(credential, 'PROJECT');
      }
    }
  },

  // 삭제
  async delete(scope: CredentialScope, id: string, projectId?: string): Promise<void> {
    switch (scope) {
      case 'USER':
        await credentialsApi.delete(id);
        break;
      case 'SYSTEM':
        await deleteSystemCredential(getToken(), id);
        break;
      case 'PROJECT':
        if (!projectId) throw new Error('projectId required for PROJECT scope');
        await deleteProjectCredential(getToken(), projectId, id);
        break;
    }
  },
};
```

**1.4 통합 Hooks 생성**
- 파일: `frontend/hooks/use-unified-credentials.ts`
- 내용: TanStack Query 기반 통합 훅

```typescript
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { unifiedCredentialsApi } from '@/lib/unified-credentials-api';
import type { CredentialScope, CreateUnifiedCredentialRequest, UpdateUnifiedCredentialRequest } from '@/lib/types';

// Query Key 팩토리
export const credentialKeys = {
  all: ['unified-credentials'] as const,
  lists: () => [...credentialKeys.all, 'list'] as const,
  list: (scope: CredentialScope, projectId?: string) =>
    [...credentialKeys.lists(), scope, projectId] as const,
};

// 목록 조회
export function useUnifiedCredentials(scope: CredentialScope, projectId?: string) {
  return useQuery({
    queryKey: credentialKeys.list(scope, projectId),
    queryFn: () => unifiedCredentialsApi.list(scope, projectId),
    enabled: scope !== 'PROJECT' || !!projectId,
  });
}

// 생성
export function useCreateUnifiedCredential() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      scope,
      request,
      projectId,
    }: {
      scope: CredentialScope;
      request: CreateUnifiedCredentialRequest;
      projectId?: string;
    }) => unifiedCredentialsApi.create(scope, request, projectId),
    onSuccess: (_, { scope, projectId }) => {
      queryClient.invalidateQueries({ queryKey: credentialKeys.list(scope, projectId) });
    },
  });
}

// 수정
export function useUpdateUnifiedCredential() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      scope,
      id,
      request,
      projectId,
    }: {
      scope: CredentialScope;
      id: string;
      request: UpdateUnifiedCredentialRequest;
      projectId?: string;
    }) => unifiedCredentialsApi.update(scope, id, request, projectId),
    onSuccess: (_, { scope, projectId }) => {
      queryClient.invalidateQueries({ queryKey: credentialKeys.list(scope, projectId) });
    },
  });
}

// 삭제
export function useDeleteUnifiedCredential() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      scope,
      id,
      projectId,
    }: {
      scope: CredentialScope;
      id: string;
      projectId?: string;
    }) => unifiedCredentialsApi.delete(scope, id, projectId),
    onSuccess: (_, { scope, projectId }) => {
      queryClient.invalidateQueries({ queryKey: credentialKeys.list(scope, projectId) });
    },
  });
}
```

---

### Phase 2: 컴포넌트 구현

**2.1 탭 네비게이션**
- 파일: `frontend/components/credentials/credential-scope-tabs.tsx`
- 역할: USER/SYSTEM/PROJECT 탭 전환 (권한에 따라 탭 숨김)

```typescript
interface CredentialScopeTabsProps {
  activeScope: CredentialScope;
  onScopeChange: (scope: CredentialScope) => void;
  isAdmin: boolean;
}
```

**2.2 프로젝트 선택기**
- 파일: `frontend/components/credentials/project-selector.tsx`
- 역할: PROJECT 탭에서 프로젝트 선택 드롭다운
- 데이터: `useProjects()` 훅 사용 (소속 프로젝트만 반환)

**2.3 통합 폼 다이얼로그**
- 파일: `frontend/components/credentials/credential-form-dialog.tsx`
- 역할: 모든 스코프/타입 지원 (기존 admin 버전 확장)
- 참고: `frontend/components/admin/credential-form-dialog.tsx`의 NEO4J_AUTH/PGVECTOR_AUTH JSON 처리 패턴 재사용
- **중요**: 스코프별 사용 가능 타입 필터링 (`getTypesForScope()` 사용)

**2.4 카드 컴포넌트**
- 파일: `frontend/components/credentials/credential-card.tsx`
- 역할: USER 스코프용 카드 뷰 (기존 패턴 재사용)
- 참고: `frontend/app/[locale]/credentials/page.tsx`의 CredentialCard

**2.5 테이블 컴포넌트**
- 파일: `frontend/components/credentials/credential-table.tsx`
- 역할: SYSTEM/PROJECT 스코프용 테이블 뷰

**2.6 목록 오케스트레이터**
- 파일: `frontend/components/credentials/credential-list-view.tsx`
- 역할: 스코프에 따라 카드/테이블 선택하여 렌더링

---

### Phase 3: 페이지 통합

**3.1 통합 페이지 생성**
- 파일: `frontend/app/[locale]/settings/credentials/page.tsx`
- 내용:
  - URL 쿼리 파라미터 처리 (scope, projectId)
  - 권한 확인 로직
  - 탭 상태 관리
  - 컴포넌트 조합

**URL 상태 동기화 구현**:
```typescript
'use client';

import { useSearchParams, useRouter } from 'next/navigation';
import { useCallback } from 'react';

export default function CredentialsPage() {
  const searchParams = useSearchParams();
  const router = useRouter();

  // URL에서 상태 읽기
  const scope = (searchParams.get('scope') as CredentialScope) || 'USER';
  const projectId = searchParams.get('projectId') || undefined;

  // 탭 변경 시 URL 업데이트
  const handleScopeChange = useCallback((newScope: CredentialScope) => {
    const params = new URLSearchParams();
    params.set('scope', newScope.toLowerCase());
    if (newScope === 'PROJECT' && projectId) {
      params.set('projectId', projectId);
    }
    router.push(`/settings/credentials?${params.toString()}`);
  }, [router, projectId]);

  // 프로젝트 변경 시 URL 업데이트
  const handleProjectChange = useCallback((newProjectId: string) => {
    const params = new URLSearchParams();
    params.set('scope', 'project');
    params.set('projectId', newProjectId);
    router.push(`/settings/credentials?${params.toString()}`);
  }, [router]);

  // ...
}
```

---

### Phase 4: 네비게이션 업데이트

**4.1 사이드바 수정**
- 파일: `frontend/components/sidebar.tsx`
- 변경: `/credentials` → `/settings/credentials`

**4.2 설정 페이지 업데이트**
- 파일: `frontend/app/[locale]/settings/page.tsx`
- 변경: Credentials 카드 추가

**4.3 Admin 설정 정리**
- 파일: `frontend/app/[locale]/admin/settings/page.tsx`
- 변경: Credentials 탭 제거, 통합 페이지 링크로 대체

**4.4 프로젝트 설정 정리**
- 파일: `frontend/app/[locale]/projects/[projectId]/settings/layout.tsx`
- 변경: Credentials 탭 제거, 통합 페이지 링크로 대체

---

### Phase 5: 마이그레이션 & 정리

**5.1 리다이렉트 설정 (즉시 리다이렉트 방식 확정)**

| 기존 URL | 새 URL |
|----------|--------|
| `/credentials` | `/settings/credentials?scope=user` |
| `/projects/{id}/settings/credentials` | `/settings/credentials?scope=project&projectId={id}` |
| `/admin/settings` (credentials 탭) | `/settings/credentials?scope=system` |

**구현 방법**: 기존 페이지에서 redirect 처리

```typescript
// frontend/app/[locale]/credentials/page.tsx
import { redirect } from 'next/navigation';

export default function CredentialsPage() {
  redirect('/settings/credentials?scope=user');
}
```

```typescript
// frontend/app/[locale]/projects/[projectId]/settings/credentials/page.tsx
import { redirect } from 'next/navigation';

export default function ProjectCredentialsPage({
  params,
}: {
  params: { projectId: string };
}) {
  redirect(`/settings/credentials?scope=project&projectId=${params.projectId}`);
}
```

**5.2 삭제 대상 파일** (리다이렉트 적용 후)
- `frontend/app/[locale]/credentials/page.tsx` → 리다이렉트 페이지로 교체
- `frontend/app/[locale]/projects/[projectId]/settings/credentials/page.tsx` → 리다이렉트 페이지로 교체
- `frontend/components/admin/credential-list.tsx` (통합 후 삭제)
- `frontend/components/admin/credential-form-dialog.tsx` (통합 후 삭제)

---

## 핵심 파일 목록

### 새로 생성
```
frontend/
├── app/[locale]/settings/credentials/
│   └── page.tsx                           # 통합 페이지
├── components/credentials/
│   ├── credential-type-config.ts          # 타입 설정 (8개 타입 메타데이터)
│   ├── credential-scope-tabs.tsx          # 탭 네비게이션
│   ├── credential-form-dialog.tsx         # 통합 폼 (JSON 필드 지원)
│   ├── credential-card.tsx                # 카드 뷰 (USER용)
│   ├── credential-table.tsx               # 테이블 뷰 (SYSTEM/PROJECT용)
│   ├── credential-list-view.tsx           # 목록 오케스트레이터
│   └── project-selector.tsx               # 프로젝트 선택기
├── hooks/
│   └── use-unified-credentials.ts         # 통합 hooks (TanStack Query)
└── lib/
    └── unified-credentials-api.ts         # API 파사드 (토큰 통일 처리)
```

### 수정 대상
```
frontend/
├── lib/types.ts                           # UnifiedCredential 타입 추가
├── components/sidebar.tsx                 # 네비게이션 링크 수정
├── app/[locale]/settings/page.tsx         # Credentials 카드 추가
├── app/[locale]/admin/settings/page.tsx   # Credentials 탭 제거
├── app/[locale]/credentials/page.tsx      # 리다이렉트로 교체
├── app/[locale]/projects/[projectId]/settings/credentials/page.tsx  # 리다이렉트로 교체
└── messages/en.json, ko.json              # i18n 추가
```

---

## 검증 체크리스트

### 기능 검증
- [ ] USER 스코프: 목록 조회, 생성, 수정, 삭제
- [ ] SYSTEM 스코프: 목록 조회, 생성, 수정, 삭제 (Admin 전용)
- [ ] PROJECT 스코프: 프로젝트 선택, 목록 조회, 생성, **수정**, 삭제

### 권한 검증
- [ ] 일반 사용자 → SYSTEM 탭 숨김
- [ ] 일반 사용자 → 소속되지 않은 프로젝트 숨김
- [ ] Admin → 모든 탭 표시, 모든 프로젝트 접근

### 특수 타입 검증
- [ ] NEO4J_AUTH: JSON 폼 필드 (username, password) 동작
- [ ] PGVECTOR_AUTH: JSON 폼 필드 (username, password) 동작
- [ ] 스코프별 타입 필터링 (`getTypesForScope()`) 동작

### URL 검증
- [ ] 딥링크: `/settings/credentials?scope=system` 정상 동작
- [ ] 딥링크: `/settings/credentials?scope=project&projectId={uuid}` 정상 동작
- [ ] 탭 전환 시 URL 파라미터 업데이트
- [ ] 프로젝트 선택 시 URL 파라미터 업데이트

### 마이그레이션 검증
- [ ] `/credentials` → `/settings/credentials?scope=user` 리다이렉트
- [ ] `/projects/{id}/settings/credentials` → `/settings/credentials?scope=project&projectId={id}` 리다이렉트
- [ ] Admin 설정에서 Credentials 탭 제거 확인

### API 검증
- [ ] 토큰 처리 통일 확인 (credentialsApi vs admin-api)
- [ ] PROJECT 스코프 Update API 호출 정상 동작
- [ ] Query 캐시 무효화 정상 동작

### UI 검증
- [ ] 반응형: 모바일 레이아웃 확인
- [ ] 로딩 상태 표시
- [ ] 에러 상태 표시
- [ ] 빈 상태 표시

---

## 참고: Backend 변경 없음

기존 3개 API 엔드포인트를 그대로 사용:
- `/api/credentials` - USER 스코프 (Full CRUD)
- `/api/admin/credentials` - SYSTEM 스코프 (Full CRUD)
- `/api/projects/{projectId}/credentials` - PROJECT 스코프 (Full CRUD, Update 이미 지원)

Frontend에서 파사드 패턴으로 통합하여 사용
