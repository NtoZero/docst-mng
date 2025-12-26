# Phase 1 Frontend 구현 완료

> 작성일: 2025-12-26

---

## 개요

Phase 1 MVP 프론트엔드 구현이 완료되었습니다. Next.js 16 App Router 기반으로 TanStack Query, Zustand, Tailwind CSS, shadcn/ui 패턴을 적용하였습니다.

---

## 변경된 파일 목록

### 신규 생성

```
frontend/
├── .eslintrc.json                    # ESLint 8 설정
├── .prettierrc                       # Prettier 설정
├── .prettierignore                   # Prettier 제외 파일
├── tailwind.config.ts                # Tailwind CSS 3.4 설정
├── postcss.config.mjs                # PostCSS 설정
├── lib/
│   ├── utils.ts                      # cn() 유틸리티
│   ├── types.ts                      # TypeScript 타입 정의
│   ├── api.ts                        # API 클라이언트
│   └── store.ts                      # Zustand 스토어
├── hooks/
│   ├── use-api.ts                    # TanStack Query 훅
│   └── use-sync.ts                   # SSE 동기화 훅
├── providers/
│   └── query-provider.tsx            # QueryClient Provider
├── components/
│   ├── header.tsx                    # 앱 헤더
│   ├── sidebar.tsx                   # 사이드바
│   ├── markdown-viewer.tsx           # Markdown 렌더러
│   ├── sync-progress.tsx             # 동기화 진행 컴포넌트
│   └── ui/                           # shadcn/ui 컴포넌트
│       ├── button.tsx
│       ├── input.tsx
│       ├── textarea.tsx
│       ├── label.tsx
│       ├── card.tsx
│       ├── badge.tsx
│       └── progress.tsx
└── app/
    ├── layout.tsx                    # 루트 레이아웃
    ├── page.tsx                      # 대시보드
    ├── globals.css                   # 글로벌 스타일
    ├── login/
    │   └── page.tsx                  # 로그인 페이지
    ├── projects/
    │   ├── page.tsx                  # 프로젝트 목록
    │   ├── new/
    │   │   └── page.tsx              # 프로젝트 생성
    │   └── [projectId]/
    │       ├── page.tsx              # 프로젝트 상세
    │       ├── search/
    │       │   └── page.tsx          # 검색 페이지
    │       └── repositories/
    │           ├── new/
    │           │   └── page.tsx      # 레포 추가
    │           └── [repoId]/
    │               └── documents/
    │                   └── page.tsx  # 문서 목록
    └── documents/
        └── [docId]/
            ├── page.tsx              # 문서 상세
            └── versions/
                └── page.tsx          # 버전 히스토리/Diff
```

### 수정된 파일

| 파일 | 변경 내용 |
|------|----------|
| `tsconfig.json` | baseUrl, paths 별칭 추가 (`@/*`) |
| `package.json` | 스크립트 및 의존성 추가 |

---

## 의존성

### 추가된 패키지

```json
{
  "dependencies": {
    "@radix-ui/react-progress": "^1.x",
    "@radix-ui/react-slot": "^1.x",
    "@tanstack/react-query": "^5.x",
    "class-variance-authority": "^0.7.x",
    "clsx": "^2.x",
    "react-markdown": "^9.x",
    "remark-gfm": "^4.x",
    "tailwind-merge": "^2.x",
    "zustand": "^5.x"
  },
  "devDependencies": {
    "@typescript-eslint/eslint-plugin": "^7.x",
    "@typescript-eslint/parser": "^7.x",
    "eslint": "^8.57.0",
    "eslint-config-prettier": "^9.x",
    "eslint-plugin-prettier": "^5.x",
    "prettier": "^3.x",
    "tailwindcss": "^3.4.x"
  }
}
```

---

## 상세 구현 내용

### 1. 프로젝트 설정

#### ESLint + Prettier
- ESLint 8 (Next.js 호환)
- TypeScript 지원
- Prettier 통합
- 자동 수정 스크립트 (`npm run lint:fix`)

#### Tailwind CSS
- v3.4 사용 (v4 PostCSS 플러그인 이슈로 다운그레이드)
- CSS 변수 기반 테마 시스템
- dark mode 지원 (class 기반)

---

### 2. 상태 관리

#### Zustand Store (`lib/store.ts`)

```typescript
// 인증 상태
useAuthStore: {
  user: User | null;
  token: string | null;
  setAuth(user, token): void;
  clearAuth(): void;
}

// UI 상태
useUIStore: {
  selectedProjectId: string | null;
  sidebarOpen: boolean;
  setSelectedProjectId(id): void;
  toggleSidebar(): void;
}
```

#### TanStack Query Hooks (`hooks/use-api.ts`)

| Hook | 용도 |
|------|------|
| `useProjects` | 프로젝트 목록 |
| `useProject` | 프로젝트 상세 |
| `useCreateProject` | 프로젝트 생성 |
| `useRepositories` | 레포지토리 목록 |
| `useCreateRepository` | 레포 연결 |
| `useSyncStatus` | 동기화 상태 |
| `useDocuments` | 문서 목록 |
| `useDocument` | 문서 상세 |
| `useDocumentVersions` | 버전 목록 |
| `useDocumentDiff` | Diff 조회 |
| `useSearch` | 검색 |

---

### 3. UI 컴포넌트

#### shadcn/ui 패턴 적용

| 컴포넌트 | 기능 |
|----------|------|
| `Button` | 다양한 variant (default, outline, ghost, destructive), asChild 지원 |
| `Input` | 기본 텍스트 입력 |
| `Textarea` | 멀티라인 입력 |
| `Label` | 폼 라벨 |
| `Card` | 카드 레이아웃 (Header, Title, Description, Content, Footer) |
| `Badge` | 상태 표시 (success, warning, destructive) |
| `Progress` | 진행률 표시 |

#### 레이아웃 컴포넌트

- **Header**: 로고, 네비게이션, 사용자 메뉴
- **Sidebar**: 프로젝트 목록, 선택 상태 표시
- **MarkdownViewer**: react-markdown + remark-gfm 기반 렌더러

---

### 4. 페이지 구현

#### 인증
- **로그인** (`/login`): 이메일/비밀번호 폼, Zustand에 토큰 저장

#### 프로젝트
- **목록** (`/projects`): 프로젝트 카드 그리드, 생성 버튼
- **생성** (`/projects/new`): 이름, 설명 입력 폼
- **상세** (`/projects/[projectId]`): 레포 목록, 동기화 버튼, SSE 진행 표시

#### 레포지토리
- **추가** (`/projects/[projectId]/repositories/new`): GitHub/Local 선택, 정보 입력

#### 문서
- **목록** (`/projects/.../documents`): 폴더별 그룹화, 타입 Badge
- **상세** (`/documents/[docId]`): Markdown 렌더링, 메타데이터
- **버전** (`/documents/[docId]/versions`): 버전 목록, Diff 뷰어

#### 검색
- **검색** (`/projects/[projectId]/search`): 키워드 검색, 결과 카드

---

### 5. SSE 동기화

#### useSync Hook (`hooks/use-sync.ts`)

```typescript
function useSync(repositoryId: string, options?: UseSyncOptions) {
  return {
    startSync: (branch?: string) => Promise<void>;
    cancelSync: () => void;
    isConnecting: boolean;
    isSyncing: boolean;
    syncEvent: SyncEvent | null;
    error: string | null;
  };
}
```

**기능:**
- POST `/api/repositories/{id}/sync`로 동기화 시작
- SSE `/api/repositories/{id}/sync/stream`에 연결
- 실시간 진행률 업데이트
- 완료/실패 시 자동 연결 해제
- 쿼리 무효화로 UI 자동 갱신

---

### 6. API 클라이언트

#### 구조 (`lib/api.ts`)

```typescript
const API_BASE = process.env.NEXT_PUBLIC_API_BASE || 'http://localhost:8080';

// 자동 토큰 주입
async function request<T>(path: string, options?: RequestInit): Promise<T>;

// API 그룹
export const authApi = { localLogin, me };
export const projectsApi = { list, get, create, update, delete, search };
export const repositoriesApi = { listByProject, get, create, sync, getSyncStatus, getSyncStreamUrl };
export const documentsApi = { listByRepository, get, getVersions, getDiff };
```

---

### 7. 타입 정의

#### 주요 타입 (`lib/types.ts`)

```typescript
// 엔티티
interface User { id, provider, email, displayName, ... }
interface Project { id, name, description, active, ... }
interface Repository { id, projectId, provider, owner, name, cloneUrl, ... }
interface Document { id, repositoryId, path, title, docType, ... }
interface DocumentVersion { id, documentId, commitSha, authorName, ... }
interface SyncJob { id, repositoryId, status, targetBranch, ... }

// 요청/응답
interface SearchResult { documentId, path, score, snippet, highlightedSnippet, ... }
interface SyncEvent { jobId, status, message, progress, totalDocs, processedDocs }
```

---

## 라우트 구조

```
Route (app)
┌ ○ /                                           # 대시보드
├ ○ /login                                      # 로그인
├ ○ /projects                                   # 프로젝트 목록
├ ○ /projects/new                               # 프로젝트 생성
├ ƒ /projects/[projectId]                       # 프로젝트 상세
├ ƒ /projects/[projectId]/search                # 검색
├ ƒ /projects/[projectId]/repositories/new      # 레포 추가
├ ƒ /projects/.../[repoId]/documents            # 문서 목록
├ ƒ /documents/[docId]                          # 문서 상세
└ ƒ /documents/[docId]/versions                 # 버전/Diff

○  (Static)   정적 페이지
ƒ  (Dynamic)  동적 페이지
```

---

## 스크립트

```bash
# 개발 서버
npm run dev

# 프로덕션 빌드
npm run build

# 린트 검사
npm run lint

# 린트 자동 수정
npm run lint:fix

# 포맷팅
npm run format
```

---

## 다음 단계 (Phase 2)

- [ ] 의미 검색 UI (semantic search 모드 토글)
- [ ] 하이브리드 검색 결과 표시
- [ ] 문서 청크 하이라이트
- [ ] 임베딩 진행 상태 표시
