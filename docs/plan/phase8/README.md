# Phase 8: Document Editor with Git Commit

> **작성일**: 2026-01-10
> **전제 조건**: Phase 7 완료 (Document Rendering UI Enhancement)
> **목표**: 웹 UI에서 문서 편집 및 Git 커밋 기능 구현

---

## 개요

문서 상세 페이지(`/documents/[docId]`)에서 직접 마크다운 문서를 편집하고 Git 커밋으로 저장하는 기능을 구현합니다.

### 현재 상태 (Phase 7)
- **Document Detail Page**: 문서 상세 조회 및 Markdown 렌더링
- **편집 기능**: 미지원 (읽기 전용)
- **버전 관리**: Git 커밋 히스토리 조회만 가능

### Phase 8 목표
- **View/Edit 모드 전환**: Edit 버튼으로 편집 모드 진입
- **두 가지 편집 뷰**:
  - **Source**: 전체 화면 마크다운 에디터
  - **Source + Preview**: 좌측 에디터, 우측 렌더링 미리보기 (Split View)
- **Git 커밋**: 저장 시 커밋 메시지 입력 후 실제 Git 커밋 생성
- **변경 사항 보호**: 저장하지 않고 나갈 때 경고

---

## 기능 상세

### 1. View/Edit Mode Toggle

문서 상세 페이지에서 View 모드와 Edit 모드를 전환합니다.

**View 모드**:
- 기존 문서 렌더링 (MarkdownViewer)
- TOC 사이드바
- Edit 버튼 표시 (MD 문서만)

**Edit 모드**:
- 전체 화면 에디터 UI
- Source/Split 뷰 토글
- Save/Cancel 버튼

### 2. Editor View Modes

#### Source View
```
┌─────────────────────────────────────────────┐
│ [X] Document Title          [Source|Split] │
│ path/to/document.md              [Save]    │
├─────────────────────────────────────────────┤
│                                             │
│   # Markdown Content                        │
│                                             │
│   Edit your content here...                 │
│                                             │
│                                             │
└─────────────────────────────────────────────┘
```

#### Split View (Source + Preview)
```
┌─────────────────────────────────────────────┐
│ [X] Document Title          [Source|Split] │
│ path/to/document.md              [Save]    │
├────────────────────┬────────────────────────┤
│     Source         │      Preview           │
├────────────────────┼────────────────────────┤
│ # Markdown Content │  Markdown Content      │
│                    │  ─────────────────     │
│ Edit here...       │  Edit here...          │
│                    │                        │
└────────────────────┴────────────────────────┘
```

### 3. Git Commit Dialog

Save 버튼 클릭 시 커밋 다이얼로그 표시:

```
┌─────────────────────────────────────────────┐
│ 🔗 Commit Changes                           │
│                                             │
│ Save your changes to `path/to/document.md` │
│                                             │
│ Commit message                              │
│ ┌─────────────────────────────────────────┐ │
│ │ Update document.md                      │ │
│ └─────────────────────────────────────────┘ │
│                                             │
│ Description (optional)                      │
│ ┌─────────────────────────────────────────┐ │
│ │ Add more details about your changes...  │ │
│ │                                         │ │
│ └─────────────────────────────────────────┘ │
│                                             │
│              [Cancel]  [Commit]             │
└─────────────────────────────────────────────┘
```

### 4. Unsaved Changes Protection

편집 중 Cancel 클릭 시 확인 다이얼로그:

```
┌─────────────────────────────────────────────┐
│ ⚠️ Unsaved changes                          │
│                                             │
│ You have unsaved changes. Are you sure you │
│ want to leave? Your changes will be lost.  │
│                                             │
│        [Continue editing] [Discard changes]│
└─────────────────────────────────────────────┘
```

---

## 아키텍처

### Data Flow

```
User Edit -> Frontend Editor -> PUT /api/documents/{docId}
           -> DocumentWriteService.updateDocument()
           -> GitWriteService.commitFile()
           -> GitSyncService.syncRepository()
           -> DB Update -> Return updated document
```

### Component Structure

```
DocumentDetailPage
  │
  ├── [View Mode]
  │     ├── Header (Edit button)
  │     ├── MarkdownViewer
  │     └── TableOfContents
  │
  └── [Edit Mode]
        ├── EditHeader
        │     ├── ViewModeToggle (Source / Split)
        │     └── Save / Cancel buttons
        ├── DocumentEditor
        │     ├── MarkdownEditor (left)
        │     └── MarkdownViewer (right, split mode only)
        ├── CommitDialog
        └── UnsavedChangesAlert
```

### State Management

Zustand store로 에디터 상태 관리:

```typescript
interface EditorState {
  isEditMode: boolean;
  viewMode: EditorViewMode;     // 'source' | 'split'
  hasUnsavedChanges: boolean;
  originalContent: string | null;
  editedContent: string | null;
  // Actions
  setEditMode: (mode: boolean) => void;
  setViewMode: (mode: EditorViewMode) => void;
  setContent: (original: string, edited?: string) => void;
  updateEditedContent: (content: string) => void;
  resetEditor: () => void;
}
```

---

## API 스펙

### PUT /api/documents/{docId}

문서 내용 업데이트 및 Git 커밋 생성.

**Request**:
```json
{
  "content": "# Updated Content\n\nNew markdown content...",
  "commitMessage": "Update document.md\n\nAdded new section",
  "branch": "main"  // optional, defaults to repository default
}
```

**Response**:
```json
{
  "documentId": "550e8400-e29b-41d4-a716-446655440000",
  "path": "docs/guide.md",
  "commitSha": "abc1234",
  "message": "Document updated successfully"
}
```

---

## 파일 변경 목록

### Backend 수정

| File | Changes |
|------|---------|
| `backend/.../api/ApiModels.java` | `UpdateDocumentRequest`, `UpdateDocumentResponse` 추가 |
| `backend/.../api/DocumentsController.java` | PUT endpoint 추가 |

### Frontend 수정

| File | Changes |
|------|---------|
| `frontend/lib/types.ts` | `EditorViewMode`, `UpdateDocumentRequest`, `UpdateDocumentResponse` 타입 추가 |
| `frontend/lib/api.ts` | `documentsApi.update()` 메서드 추가 |
| `frontend/lib/store.ts` | `useEditorStore` 추가 |
| `frontend/hooks/use-api.ts` | `useUpdateDocument` hook 추가 |
| `frontend/app/[locale]/documents/[docId]/page.tsx` | Edit 모드 지원 |

### Frontend 신규 파일

| File | Description |
|------|-------------|
| `frontend/components/editor/index.ts` | Barrel export |
| `frontend/components/editor/editor-view-mode-toggle.tsx` | Source/Split 토글 버튼 |
| `frontend/components/editor/markdown-editor.tsx` | Textarea 기반 마크다운 에디터 |
| `frontend/components/editor/document-editor.tsx` | Split view 컨테이너 |
| `frontend/components/editor/commit-dialog.tsx` | 커밋 메시지 다이얼로그 |
| `frontend/components/editor/unsaved-changes-alert.tsx` | 변경 사항 경고 다이얼로그 |

---

## 기술적 고려사항

### Permission
- Backend: `DocumentWriteService`에서 EDITOR 권한 체크 (기존 로직)
- Frontend: 권한 없는 사용자에게 Edit 버튼 숨김 (추후 구현)

### Conflict Handling
- 현재: 낙관적 저장 (마지막 저장 우선)
- 향후: content hash 기반 optimistic locking 고려

### Large Documents
- Textarea 기반 기본 구현
- 향후: CodeMirror/Monaco 고려 (syntax highlighting, 대용량 파일)

### Split View 동기화 스크롤 (Implemented)
- **구현 완료**: Source 스크롤 시 Preview가 동기화되어 스크롤
- **구현 방식**: 비율 기반 (스크롤 퍼센트 계산)
- **향후 개선**: 섹션 기반 (헤딩 매핑)으로 더 정확한 동기화 가능

### Tab Key Support
- Textarea에서 Tab 키 입력 시 2칸 들여쓰기
- 기본 브라우저 동작(포커스 이동) 방지

---

## 검증 계획

### 테스트 케이스

1. **Edit 모드 진입**: Edit 버튼 클릭 시 에디터 표시
2. **View 모드 전환**: Source ↔ Split 토글 동작
3. **실시간 미리보기**: Split 모드에서 편집 시 우측 미리보기 갱신
4. **커밋 저장**: 커밋 메시지 입력 후 저장 → Git 커밋 생성
5. **변경 사항 보호**: 저장 없이 Cancel 시 경고 표시
6. **버전 히스토리**: 저장 후 View History에 새 커밋 표시
7. **MD 문서만 편집 가능**: 다른 타입 문서는 Edit 버튼 숨김

### 테스트 실행

```bash
# Backend 실행
cd backend && ./gradlew bootRun

# Frontend 실행
cd frontend && npm run dev

# 테스트
# 1. http://localhost:3000/ko/documents/{docId} 접속
# 2. Edit 버튼 클릭
# 3. 내용 수정
# 4. Save 클릭 → 커밋 메시지 입력 → Commit
# 5. View History에서 새 커밋 확인
```

---

## 다음 단계

Phase 8 완료 후:
- **Phase 9**: Multi-tenant 지원, 팀 협업
- **Phase 10**: Advanced RAG (Hybrid Search 고도화, Re-ranking)
- **Phase 11**: 모니터링 & 분석 (사용 패턴, 비용 분석)
