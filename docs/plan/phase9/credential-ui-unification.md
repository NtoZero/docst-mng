# Credential 관리 UI 일원화 계획

## 목표
분산된 Credential 관리 UI (4곳)를 **단일 통합 페이지**로 일원화

## 현재 상태 (문제점)

| 위치 | 스코프 | 지원 타입 | 문제 |
|------|--------|----------|------|
| `/credentials` | USER | 3개 (Git) | 개별 페이지 |
| `/projects/{id}/settings/credentials` | PROJECT | 8개 | 프로젝트별 분산 |
| `/admin/settings` (Credentials 탭) | SYSTEM | 5개 | Admin 설정에 혼재 |
| `/settings/api-keys` | MCP | - | **제외** (별도 유지) |

## 통합 후 구조

**새 경로**: `/settings/credentials`

```
/settings/credentials                    # 기본: USER 탭
/settings/credentials?scope=user         # USER 스코프
/settings/credentials?scope=system       # SYSTEM 스코프 (Admin 전용)
/settings/credentials?scope=project&projectId={uuid}  # PROJECT 스코프
```

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

## 권한 모델

| 역할 | USER | SYSTEM | PROJECT |
|------|------|--------|---------|
| 일반 사용자 | 자신의 것만 (RW) | 숨김 | 소속 프로젝트만 (Admin 역할시 RW) |
| Admin | 자신의 것만 (RW) | 전체 (RW) | 전체 프로젝트 (RW) |

---

## 구현 단계

### Phase 1: 공통 인프라 (기반 작업)

**1.1 타입 설정 파일 생성**
- 파일: `frontend/components/credentials/credential-type-config.ts`
- 내용: 각 CredentialType별 메타데이터 (label, scopes, placeholder, helpUrl 등)

```typescript
export const CREDENTIAL_TYPE_CONFIG = {
  GITHUB_PAT: {
    label: 'GitHub PAT',
    scopes: ['USER', 'PROJECT'],
    secretLabel: 'Personal Access Token',
    placeholder: 'ghp_...',
  },
  OPENAI_API_KEY: {
    label: 'OpenAI API Key',
    scopes: ['SYSTEM', 'PROJECT'],
    secretLabel: 'API Key',
    placeholder: 'sk-proj-...',
  },
  NEO4J_AUTH: {
    label: 'Neo4j Auth',
    scopes: ['SYSTEM', 'PROJECT'],
    isJsonAuth: true,
    fields: ['username', 'password'],
  },
  // ... 나머지 타입
};
```

**1.2 통합 타입 추가**
- 파일: `frontend/lib/types.ts`
- 추가: `UnifiedCredential` 인터페이스

```typescript
export interface UnifiedCredential {
  id: string;
  name: string;
  type: CredentialType;
  scope: CredentialScope;
  projectId?: string;
  projectName?: string;
  username?: string | null;
  description: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string | null;
}
```

**1.3 API 파사드 생성**
- 파일: `frontend/lib/unified-credentials-api.ts`
- 역할: 3개 API 엔드포인트를 단일 인터페이스로 통합

**1.4 통합 Hooks 생성**
- 파일: `frontend/hooks/use-unified-credentials.ts`
- 내용: `useUnifiedCredentials`, `useCreateUnifiedCredential`, `useUpdateUnifiedCredential`, `useDeleteUnifiedCredential`

---

### Phase 2: 컴포넌트 구현

**2.1 탭 네비게이션**
- 파일: `frontend/components/credentials/credential-scope-tabs.tsx`
- 역할: USER/SYSTEM/PROJECT 탭 전환 (권한에 따라 탭 숨김)

**2.2 프로젝트 선택기**
- 파일: `frontend/components/credentials/project-selector.tsx`
- 역할: PROJECT 탭에서 프로젝트 선택 드롭다운

**2.3 통합 폼 다이얼로그**
- 파일: `frontend/components/credentials/credential-form-dialog.tsx`
- 역할: 모든 스코프/타입 지원 (기존 admin 버전 확장)
- 참고: `frontend/components/admin/credential-form-dialog.tsx`의 NEO4J_AUTH/PGVECTOR_AUTH JSON 처리 패턴 재사용

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
- 변경: Credentials 탭 제거 (또는 통합 페이지 링크로 대체)

**4.4 프로젝트 설정 정리**
- 파일: `frontend/app/[locale]/projects/[projectId]/settings/layout.tsx`
- 변경: Credentials 탭 제거 (또는 통합 페이지 링크로 대체)

---

### Phase 5: 마이그레이션 & 정리

**5.1 리다이렉트 설정**

| 기존 URL | 새 URL |
|----------|--------|
| `/credentials` | `/settings/credentials?scope=user` |
| `/projects/{id}/settings/credentials` | `/settings/credentials?scope=project&projectId={id}` |
| `/admin/settings` (credentials 탭) | `/settings/credentials?scope=system` |

**5.2 기존 페이지 정리**
- 옵션 A (권장): 즉시 리다이렉트로 교체
- 옵션 B: deprecation 배너 표시 후 2-3 릴리스 후 삭제

**5.3 삭제 대상 파일** (최종)
- `frontend/app/[locale]/credentials/page.tsx`
- `frontend/app/[locale]/projects/[projectId]/settings/credentials/page.tsx`
- `frontend/components/admin/credential-list.tsx` (통합 후)
- `frontend/components/admin/credential-form-dialog.tsx` (통합 후)

---

## 핵심 파일 목록

### 새로 생성
```
frontend/
├── app/[locale]/settings/credentials/
│   └── page.tsx                           # 통합 페이지
├── components/credentials/
│   ├── credential-type-config.ts          # 타입 설정
│   ├── credential-scope-tabs.tsx          # 탭 네비게이션
│   ├── credential-form-dialog.tsx         # 통합 폼
│   ├── credential-card.tsx                # 카드 뷰
│   ├── credential-table.tsx               # 테이블 뷰
│   ├── credential-list-view.tsx           # 목록 오케스트레이터
│   └── project-selector.tsx               # 프로젝트 선택기
├── hooks/
│   └── use-unified-credentials.ts         # 통합 hooks
└── lib/
    └── unified-credentials-api.ts         # API 파사드
```

### 수정 대상
```
frontend/
├── lib/types.ts                           # UnifiedCredential 타입 추가
├── components/sidebar.tsx                 # 네비게이션 링크 수정
├── app/[locale]/settings/page.tsx         # Credentials 카드 추가
├── app/[locale]/admin/settings/page.tsx   # Credentials 탭 제거
└── messages/en.json, ko.json              # i18n 추가
```

---

## 검증 체크리스트

- [ ] USER 스코프: 목록 조회, 생성, 수정, 삭제
- [ ] SYSTEM 스코프: 목록 조회, 생성, 수정, 삭제 (Admin 전용)
- [ ] PROJECT 스코프: 프로젝트 선택, 목록 조회, 생성, 수정, 삭제
- [ ] 권한: 일반 사용자 → SYSTEM 탭 숨김
- [ ] 권한: 소속되지 않은 프로젝트 숨김
- [ ] NEO4J_AUTH/PGVECTOR_AUTH: JSON 폼 필드 (username, password)
- [ ] URL 딥링크: `/settings/credentials?scope=system` 정상 동작
- [ ] 리다이렉트: 기존 URL에서 새 URL로 정상 이동
- [ ] 반응형: 모바일 레이아웃 확인

---

## 참고: Backend 변경 없음

기존 3개 API 엔드포인트를 그대로 사용:
- `/api/credentials` - USER 스코프
- `/api/admin/credentials` - SYSTEM 스코프
- `/api/projects/{projectId}/credentials` - PROJECT 스코프

Frontend에서 파사드 패턴으로 통합하여 사용