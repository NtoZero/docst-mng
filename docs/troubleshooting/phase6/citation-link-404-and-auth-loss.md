# Citation 링크 404 및 새 탭에서 인증 풀림 문제

## 문제 요약

| 항목 | 내용 |
|------|------|
| **발생일** | 2026-01-09 |
| **증상** | 1) Citation 클릭 시 404 페이지 <br> 2) 새 탭에서 URL 직접 입력 시 인증 풀림 |
| **영향 범위** | Playground Citation 링크, 문서 상세 페이지 직접 접근 |
| **해결 상태** | 해결됨 |

---

## 증상 상세

### 증상 1: Citation 링크 클릭 시 404

1. Playground (`/ko/playground`)에서 문서 관련 질문
2. LLM이 RAG 검색 결과로 Citation 표시
3. Citation 카드 클릭
4. `/documents/29e380db-...` 로 이동 → **404 Not Found**

**예상 동작:** `/ko/documents/29e380db-...` 로 이동하여 문서 표시

### 증상 2: 새 탭에서 인증 풀림

1. 기존 탭에서 로그인 상태 유지
2. 새 탭에서 `/ko/documents/xxx` 직접 입력
3. 잠시 로딩 후 `/ko/login`으로 리다이렉트
4. 다시 로그인 필요

**예상 동작:** localStorage의 인증 정보 유지로 문서 바로 표시

---

## 원인 분석

### 문제 1: Citation URL에 locale prefix 누락

**위치:** `frontend/components/playground/citation-card.tsx`

```typescript
// 문제 코드
const handleClick = () => {
  window.open(`/documents/${citation.documentId}`, '_blank');  // locale 없음!
};
```

`next-intl`의 `localePrefix: 'always'` 설정으로 모든 경로에 locale이 필요하지만,
`window.open`에서 locale 없이 `/documents/...`로 이동하여 404 발생.

### 문제 2: proxy.ts matcher 불완전

**위치:** `frontend/proxy.ts`

```typescript
// 기존 설정
export const config = {
  matcher: ['/', '/(ko|en)/:path*'],  // locale 없는 경로 미처리
};
```

`/documents/xxx` (locale 없는 경로)가 matcher에 포함되지 않아 proxy(middleware)가 실행되지 않음.
결과적으로 locale 리다이렉트가 발생하지 않고 404 반환.

### 문제 3: Zustand hydration 타이밍 이슈

**위치:** `frontend/lib/store.ts`, `frontend/app/[locale]/documents/[docId]/page.tsx`

```typescript
// 문제 코드
export default function DocumentDetailPage(...) {
  const user = useAuthStore((state) => state.user);

  useEffect(() => {
    if (!user) {
      router.push('/login');  // hydration 전에 실행됨!
    }
  }, [user, router]);

  if (!user) return null;  // hydration 전에 null 반환!
  // ...
}
```

**문제 흐름:**

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. 새 탭 열기 → 페이지 로드                                      │
├─────────────────────────────────────────────────────────────────┤
│ 2. React 첫 렌더링                                               │
│    - useAuthStore → user: null (localStorage 아직 안 읽음)      │
│    - useEffect 실행 → !user이므로 /login 리다이렉트 시작        │
├─────────────────────────────────────────────────────────────────┤
│ 3. Zustand hydration (이미 늦음!)                               │
│    - localStorage에서 docst-auth 읽기                           │
│    - user 상태 업데이트 → 하지만 이미 리다이렉트 중             │
└─────────────────────────────────────────────────────────────────┘
```

---

## 해결 방법

### 수정 1: citation-card.tsx - locale prefix 추가

```typescript
// 수정 후
import { useLocale } from 'next-intl';

export function CitationCard({ citation, index }: CitationCardProps) {
  const locale = useLocale();  // 현재 locale 가져오기

  const handleClick = () => {
    window.open(`/${locale}/documents/${citation.documentId}`, '_blank');
  };
  // ...
}
```

### 수정 2: proxy.ts - matcher 확장

```typescript
// 수정 후
export const config = {
  matcher: [
    '/',
    '/(ko|en)/:path*',
    // locale 없는 경로도 매칭하여 리다이렉트
    '/((?!api|_next|_vercel|.*\\..*).*)',
  ],
};
```

### 수정 3: Zustand hydration 대기

**store.ts 수정:**

```typescript
interface AuthState {
  user: User | null;
  token: string | null;
  _hasHydrated: boolean;  // hydration 상태 추적
  setAuth: (user: User, token: string) => void;
  clearAuth: () => void;
  setHasHydrated: (state: boolean) => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      user: null,
      token: null,
      _hasHydrated: false,
      setAuth: (user, token) => set({ user, token }),
      clearAuth: () => set({ user: null, token: null }),
      setHasHydrated: (state) => set({ _hasHydrated: state }),
    }),
    {
      name: 'docst-auth',
      onRehydrateStorage: () => (state) => {
        state?.setHasHydrated(true);  // hydration 완료 시 호출
      },
    }
  )
);

/**
 * Hydration 완료를 기다리는 Hook
 */
export function useAuthHydrated() {
  const [isHydrated, setIsHydrated] = useState(false);
  const user = useAuthStore((state) => state.user);
  const token = useAuthStore((state) => state.token);
  const hasHydrated = useAuthStore((state) => state._hasHydrated);

  useEffect(() => {
    if (hasHydrated) {
      setIsHydrated(true);
    }
  }, [hasHydrated]);

  return { isHydrated, user, token };
}
```

**페이지 컴포넌트 수정:**

```typescript
export default function DocumentDetailPage(...) {
  const { isHydrated, user } = useAuthHydrated();

  useEffect(() => {
    // hydration 완료 후에만 인증 체크
    if (isHydrated && !user) {
      router.push('/login');
    }
  }, [isHydrated, user, router]);

  // hydration 중이면 로딩 표시
  if (!isHydrated) {
    return <Loader2 className="animate-spin" />;
  }

  if (!user) return null;
  // ...
}
```

### 수정 후 흐름

```
┌─────────────────────────────────────────────────────────────────┐
│ 1. 새 탭 열기 → 페이지 로드                                      │
├─────────────────────────────────────────────────────────────────┤
│ 2. React 첫 렌더링                                               │
│    - useAuthHydrated → isHydrated: false                        │
│    - 로딩 스피너 표시 (인증 체크 안 함)                          │
├─────────────────────────────────────────────────────────────────┤
│ 3. Zustand hydration                                            │
│    - localStorage에서 docst-auth 읽기                           │
│    - user 상태 업데이트                                         │
│    - onRehydrateStorage → _hasHydrated: true                    │
├─────────────────────────────────────────────────────────────────┤
│ 4. useAuthHydrated 업데이트                                     │
│    - isHydrated: true, user: { ... }                            │
│    - useEffect 실행 → user 있으므로 리다이렉트 안 함            │
│    - 문서 정상 표시 ✓                                           │
└─────────────────────────────────────────────────────────────────┘
```

---

## 수정된 파일

| 파일 | 변경 내용 |
|------|----------|
| `frontend/proxy.ts` | matcher 확장하여 locale 없는 경로도 처리 |
| `frontend/components/playground/citation-card.tsx` | `useLocale()` 사용하여 locale prefix 추가 |
| `frontend/lib/store.ts` | `_hasHydrated` 상태 추가, `useAuthHydrated` hook 생성 |
| `frontend/app/[locale]/documents/[docId]/page.tsx` | `useAuthHydrated` 사용하여 hydration 대기 |
| `frontend/app/[locale]/documents/[docId]/versions/page.tsx` | `useAuthHydrated` 사용하여 hydration 대기 |

---

## 검증 방법

### 1. Citation 링크 테스트

1. Playground에서 문서 검색 질문 입력
2. Citation 카드 클릭
3. 새 탭에서 `/ko/documents/xxx` 형태로 열리고 문서 표시 확인

### 2. 직접 URL 접근 테스트

1. 기존 탭에서 로그인
2. 새 탭 열기
3. `/ko/documents/xxx` 직접 입력
4. 로그인 상태 유지되고 문서 표시 확인

### 3. locale 없는 URL 리다이렉트 테스트

1. `/documents/xxx` 직접 입력
2. 자동으로 `/en/documents/xxx` (또는 기본 locale)로 리다이렉트 확인

---

## 교훈 및 주의사항

### 1. next-intl에서 URL 생성 시 locale 포함

`window.open`, `window.location.href` 등 네이티브 브라우저 API 사용 시:
- `useLocale()` hook으로 현재 locale 가져오기
- URL에 locale prefix 명시적 포함

**권장:** next-intl의 `Link` 컴포넌트나 `useRouter().push()` 사용

### 2. Next.js 16의 proxy.ts

Next.js 16에서는 `middleware.ts` 대신 `proxy.ts` 사용:
- 파일명: `frontend/proxy.ts`
- export: `export const proxy = ...`
- 두 파일이 동시에 존재하면 빌드 오류 발생

### 3. Zustand persist와 SSR/hydration

Zustand persist는 localStorage를 사용하므로:
- 서버 사이드에서는 항상 초기값 (null)
- 클라이언트에서 hydration 후에야 실제 값 로드
- 인증 체크는 반드시 hydration 완료 후 수행

**패턴:**
```typescript
const { isHydrated, user } = useAuthHydrated();

if (!isHydrated) return <Loading />;
if (!user) return <Redirect to="/login" />;
return <ProtectedContent />;
```

---

## 참고 자료

- [next-intl Routing](https://next-intl-docs.vercel.app/docs/routing)
- [Next.js 16 Proxy (Middleware)](https://nextjs.org/docs/app/building-your-application/routing/middleware)
- [Zustand Persist Hydration](https://docs.pmnd.rs/zustand/integrations/persisting-store-data#hydration-and-asynchronous-storages)
