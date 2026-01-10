# Phase 8: Document Editor Implementation

> **구현일**: 2026-01-10
> **상태**: 완료
> **관련 계획**: [Phase 8 Plan](../../plan/phase8/README.md)

---

## 개요

문서 상세 페이지에서 마크다운 문서를 직접 편집하고 Git 커밋으로 저장하는 기능을 구현했습니다.

### 구현된 기능
- View/Edit 모드 전환
- Source/Split 편집 뷰
- Git 커밋 다이얼로그
- 미저장 변경 사항 경고

---

## Backend 구현

### 1. ApiModels.java

**파일**: `backend/src/main/java/com/docst/api/ApiModels.java`

문서 업데이트 요청/응답 레코드 추가:

```java
// Document Update Models
public record UpdateDocumentRequest(
    String content,
    String commitMessage,
    String branch           // optional, defaults to repository default
) {}

public record UpdateDocumentResponse(
    UUID documentId,
    String path,
    String commitSha,
    String message
) {}
```

### 2. DocumentsController.java

**파일**: `backend/src/main/java/com/docst/api/DocumentsController.java`

PUT endpoint 추가:

```java
@PutMapping("/documents/{docId}")
public ResponseEntity<UpdateDocumentResponse> updateDocument(
    @PathVariable UUID docId,
    @RequestBody UpdateDocumentRequest request,
    @AuthenticationPrincipal UserPrincipal principal
) {
    var input = new UpdateDocumentInput(
        request.content(),
        request.commitMessage(),
        request.branch(),
        true  // createCommit = true
    );

    var result = documentWriteService.updateDocument(docId, input, principal.getUserId());

    return ResponseEntity.ok(new UpdateDocumentResponse(
        result.documentId(),
        result.path(),
        result.commitSha(),
        "Document updated successfully"
    ));
}
```

---

## Frontend 구현

### 1. Types

**파일**: `frontend/lib/types.ts`

```typescript
// Editor Types
export type EditorViewMode = 'source' | 'split';

export interface UpdateDocumentRequest {
  content: string;
  commitMessage: string;
  branch?: string;
}

export interface UpdateDocumentResponse {
  documentId: string;
  path: string;
  commitSha: string;
  message: string;
}
```

### 2. API Client

**파일**: `frontend/lib/api.ts`

```typescript
export const documentsApi = {
  // ... existing methods

  update: (id: string, data: UpdateDocumentRequest): Promise<UpdateDocumentResponse> =>
    request(`/api/documents/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),
};
```

### 3. React Query Hook

**파일**: `frontend/hooks/use-api.ts`

```typescript
export function useUpdateDocument() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdateDocumentRequest }) =>
      documentsApi.update(id, data),
    onSuccess: (result) => {
      // Invalidate document detail and versions
      queryClient.invalidateQueries({
        queryKey: queryKeys.documents.detail(result.documentId)
      });
      queryClient.invalidateQueries({
        queryKey: queryKeys.documents.versions(result.documentId)
      });
    },
  });
}
```

### 4. Editor Store

**파일**: `frontend/lib/store.ts`

Zustand 기반 에디터 상태 관리:

```typescript
interface EditorState {
  isEditMode: boolean;
  viewMode: EditorViewMode;
  hasUnsavedChanges: boolean;
  originalContent: string | null;
  editedContent: string | null;
  setEditMode: (mode: boolean) => void;
  setViewMode: (mode: EditorViewMode) => void;
  setContent: (original: string, edited?: string) => void;
  updateEditedContent: (content: string) => void;
  resetEditor: () => void;
}

export const useEditorStore = create<EditorState>()((set, get) => ({
  isEditMode: false,
  viewMode: 'split',
  hasUnsavedChanges: false,
  originalContent: null,
  editedContent: null,

  setEditMode: (mode) => set({ isEditMode: mode }),
  setViewMode: (mode) => set({ viewMode: mode }),

  setContent: (original, edited) =>
    set({
      originalContent: original,
      editedContent: edited ?? original,
      hasUnsavedChanges: false,
    }),

  updateEditedContent: (content) =>
    set((state) => ({
      editedContent: content,
      hasUnsavedChanges: content !== state.originalContent,
    })),

  resetEditor: () =>
    set({
      isEditMode: false,
      viewMode: 'split',
      hasUnsavedChanges: false,
      originalContent: null,
      editedContent: null,
    }),
}));
```

---

## Editor Components

### 컴포넌트 구조

```
frontend/components/editor/
├── index.ts                      # Barrel export
├── editor-view-mode-toggle.tsx   # Source/Split 토글
├── markdown-editor.tsx           # Textarea 에디터
├── document-editor.tsx           # Split view 컨테이너
├── commit-dialog.tsx             # 커밋 다이얼로그
└── unsaved-changes-alert.tsx     # 변경 경고
```

### 1. EditorViewModeToggle

Source/Split 뷰 전환 버튼 그룹:

```typescript
interface EditorViewModeToggleProps {
  mode: EditorViewMode;
  onModeChange: (mode: EditorViewMode) => void;
}

export function EditorViewModeToggle({ mode, onModeChange }: EditorViewModeToggleProps) {
  return (
    <div className="flex rounded-md border">
      <Button
        variant={mode === 'source' ? 'default' : 'ghost'}
        size="sm"
        onClick={() => onModeChange('source')}
      >
        <Code className="h-4 w-4 mr-1" />
        Source
      </Button>
      <Button
        variant={mode === 'split' ? 'default' : 'ghost'}
        size="sm"
        onClick={() => onModeChange('split')}
      >
        <Columns className="h-4 w-4 mr-1" />
        Split
      </Button>
    </div>
  );
}
```

### 2. MarkdownEditor

Textarea 기반 에디터 (Tab 키 지원):

```typescript
export function MarkdownEditor({ value, onChange, className, placeholder }: MarkdownEditorProps) {
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  // Tab key for indentation
  const handleKeyDown = useCallback((e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Tab') {
      e.preventDefault();
      const textarea = textareaRef.current;
      if (!textarea) return;

      const start = textarea.selectionStart;
      const end = textarea.selectionEnd;
      const newValue = value.substring(0, start) + '  ' + value.substring(end);
      onChange(newValue);

      requestAnimationFrame(() => {
        textarea.selectionStart = textarea.selectionEnd = start + 2;
      });
    }
  }, [value, onChange]);

  return (
    <Textarea
      ref={textareaRef}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      onKeyDown={handleKeyDown}
      placeholder={placeholder}
      className={cn('min-h-[500px] font-mono text-sm resize-none', className)}
      spellCheck={false}
    />
  );
}
```

### 3. DocumentEditor

Split view 컨테이너:

```typescript
export function DocumentEditor({ content, onChange, viewMode, className }: DocumentEditorProps) {
  if (viewMode === 'source') {
    return (
      <div className={cn('h-full', className)}>
        <MarkdownEditor value={content} onChange={onChange} className="h-full w-full" />
      </div>
    );
  }

  // Split view
  return (
    <div className={cn('flex gap-4 h-full', className)}>
      {/* Source Panel */}
      <div className="flex-1 min-w-0 flex flex-col">
        <div className="text-xs font-medium text-muted-foreground mb-2">Source</div>
        <MarkdownEditor value={content} onChange={onChange} className="flex-1" />
      </div>

      {/* Preview Panel */}
      <div className="flex-1 min-w-0 flex flex-col">
        <div className="text-xs font-medium text-muted-foreground mb-2">Preview</div>
        <div className="flex-1 border rounded-md overflow-auto p-4 bg-background">
          <MarkdownViewer content={content} showFrontmatter={false} />
        </div>
      </div>
    </div>
  );
}
```

### 4. CommitDialog

커밋 메시지 입력 다이얼로그:

- 커밋 메시지 (제목)
- 설명 (선택사항)
- 로딩 상태 표시
- 기본 메시지: `Update {filename}`

### 5. UnsavedChangesAlert

미저장 변경 경고 다이얼로그:

- "Continue editing" / "Discard changes" 선택
- AlertDialog 컴포넌트 기반

---

## Page Integration

**파일**: `frontend/app/[locale]/documents/[docId]/page.tsx`

### 주요 변경사항

1. **Edit 버튼 추가**: MD 문서에만 표시
2. **Edit 모드 UI**: 별도의 전체 화면 레이아웃
3. **Store 연동**: `useEditorStore`로 상태 관리
4. **Commit 플로우**: 저장 시 CommitDialog → API 호출 → 성공 시 Toast

### 핵심 코드

```typescript
// Editor state from store
const {
  isEditMode, viewMode, hasUnsavedChanges, editedContent,
  setEditMode, setViewMode, setContent, updateEditedContent, resetEditor,
} = useEditorStore();

// Enter edit mode
const handleEnterEditMode = useCallback(() => {
  if (document?.content) {
    setContent(document.content);
    setEditMode(true);
  }
}, [document?.content, setContent, setEditMode]);

// Handle commit
const handleCommit = useCallback(async (message: string) => {
  if (!editedContent) return;

  try {
    await updateMutation.mutateAsync({
      id: docId,
      data: { content: editedContent, commitMessage: message },
    });
    setCommitDialogOpen(false);
    resetEditor();
    toast.success('Document saved successfully');
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Failed to save document');
  }
}, [docId, editedContent, updateMutation, resetEditor]);
```

---

## 빌드 및 테스트

### 빌드 결과

```bash
# Backend
cd backend && ./gradlew compileJava
# BUILD SUCCESSFUL

# Frontend
cd frontend && npm run build
# ✓ Compiled successfully
# ✓ Generating static pages (28/28)
```

### 테스트 방법

1. Backend 실행: `cd backend && ./gradlew bootRun`
2. Frontend 실행: `cd frontend && npm run dev`
3. 문서 상세 페이지 접속: `http://localhost:3000/ko/documents/{docId}`
4. Edit 버튼 클릭
5. Source/Split 뷰 전환 테스트
6. 내용 수정 후 Save 클릭
7. 커밋 메시지 입력 후 Commit
8. View History에서 새 커밋 확인

---

## 파일 목록

### Backend (2 files modified)

| File | Lines Changed |
|------|---------------|
| `ApiModels.java` | +12 |
| `DocumentsController.java` | +25 |

### Frontend (11 files)

| File | Type | Description |
|------|------|-------------|
| `lib/types.ts` | Modified | Editor 타입 추가 |
| `lib/api.ts` | Modified | update 메서드 추가 |
| `lib/store.ts` | Modified | useEditorStore 추가 |
| `hooks/use-api.ts` | Modified | useUpdateDocument hook 추가 |
| `app/[locale]/documents/[docId]/page.tsx` | Modified | Edit 모드 UI 통합 |
| `components/editor/index.ts` | New | Barrel export |
| `components/editor/editor-view-mode-toggle.tsx` | New | 뷰 모드 토글 |
| `components/editor/markdown-editor.tsx` | New | 마크다운 에디터 |
| `components/editor/document-editor.tsx` | New | Split view 컨테이너 |
| `components/editor/commit-dialog.tsx` | New | 커밋 다이얼로그 |
| `components/editor/unsaved-changes-alert.tsx` | New | 변경 경고 |

---

## 구현된 개선사항

### Split View 동기화 스크롤 (Implemented)

**구현일**: 2026-01-10

Source 에디터 스크롤 시 Preview 패널이 동기화되어 스크롤됩니다.

```
┌─────────────────────┬─────────────────────┐
│     Source          │      Preview        │
├─────────────────────┼─────────────────────┤
│ # Section 1         │  Section 1          │
│ content...          │  content...         │
│                     │                     │
│ # Section 2  ←───── │ ─────→ Section 2    │  ← 동기화 스크롤
│ editing here...     │  editing here...    │
│                     │                     │
└─────────────────────┴─────────────────────┘
```

**구현 방식**: 비율 기반 동기화

```typescript
// MarkdownEditor - 스크롤 이벤트 핸들러
const handleScroll = useCallback((e: React.UIEvent<HTMLTextAreaElement>) => {
  if (!onScroll) return;
  const target = e.currentTarget;
  const maxScroll = target.scrollHeight - target.clientHeight;
  const scrollRatio = maxScroll > 0 ? target.scrollTop / maxScroll : 0;
  onScroll(scrollRatio);
}, [onScroll]);

// DocumentEditor - 동기화 로직
const handleSourceScroll = useCallback((scrollRatio: number) => {
  if (isScrollingFromSource) return;
  const preview = previewRef.current;
  if (!preview) return;

  setIsScrollingFromSource(true);
  const maxScroll = preview.scrollHeight - preview.clientHeight;
  preview.scrollTop = scrollRatio * maxScroll;

  requestAnimationFrame(() => {
    setIsScrollingFromSource(false);
  });
}, [isScrollingFromSource]);
```

**변경 파일**:
- `components/editor/markdown-editor.tsx`: `onScroll` 콜백, `forwardRef` 추가
- `components/editor/document-editor.tsx`: 스크롤 동기화 로직 추가

---

## 향후 개선사항

| 우선순위 | 기능 | 설명 |
|----------|------|------|
| High | **권한 체크** | Edit 버튼을 EDITOR 권한이 있는 사용자에게만 표시 |
| High | **Browser Warning** | 페이지 이탈 시 브라우저 경고 (`beforeunload` 이벤트) |
| Medium | **Conflict Detection** | 동시 편집 시 충돌 감지 (content hash 비교) |
| Medium | **Auto-save Draft** | 로컬 스토리지에 임시 저장 |
| Low | **Advanced Editor** | CodeMirror/Monaco로 업그레이드 (syntax highlighting) |
