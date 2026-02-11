# Phase 17-B: 상세 조회 UI

**상태**: 🔲 예정

## 개요

용어 카드 클릭 시 전체 정의와 관련 정보를 보여주는 상세 조회 기능 구현.

## 현재 상태

- 용어 카드에서 2줄로 정의 truncate
- 상세 조회 UI 미구현
- 관련 용어(related_terms) 연결 UI 미구현

## 구현 범위

### 1. 용어 상세 다이얼로그

**컴포넌트**: `frontend/components/glossary/glossary-detail-dialog.tsx`

```typescript
interface GlossaryDetailDialogProps {
  term: GlossaryTerm | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onEdit: (term: GlossaryTerm) => void;
  onDelete: (term: GlossaryTerm) => void;
}
```

**표시 정보**:
- 용어명 (+ 약어 Badge)
- 카테고리 Badge
- 전체 정의 (Markdown 렌더링 지원)
- 동의어 목록 (Chip 형태)
- 관련 용어 링크 목록
- 생성/수정 일시
- 수정/삭제 버튼

### 2. 관련 용어 연결

**기능**:
- related_terms UUID 목록으로 연결된 용어 표시
- 클릭 시 해당 용어 상세로 이동
- 양방향 관계 표시 (A→B면 B에서도 A 표시)

**Backend 변경** (선택적):
```java
// GlossaryService.java
public List<GlossaryTerm> getRelatedTerms(UUID projectId, UUID termId) {
    GlossaryTerm term = findById(projectId, termId);
    if (term.getRelatedTerms() == null || term.getRelatedTerms().isEmpty()) {
        return List.of();
    }
    return repository.findAllById(term.getRelatedTerms());
}
```

### 3. 폼 다이얼로그 개선

**관련 용어 선택 UI**:
- Combobox로 프로젝트 내 용어 검색/선택
- 선택된 용어 Chip 표시
- 양방향 관계 자동 설정 옵션

### 4. UI 흐름

```
[카드 클릭] → [상세 다이얼로그]
                    │
                    ├── [수정] → [폼 다이얼로그]
                    ├── [삭제] → [확인 다이얼로그]
                    └── [관련 용어 클릭] → [해당 용어 상세]
```

## 신규/수정 파일

### Frontend 신규
- `frontend/components/glossary/glossary-detail-dialog.tsx`
- `frontend/components/glossary/related-terms-select.tsx`

### Frontend 수정
- `frontend/app/[locale]/projects/[projectId]/glossary/page.tsx`
  - 카드 클릭 시 상세 다이얼로그 열기
  - viewingTerm state 추가
- `frontend/components/glossary/glossary-term-card.tsx`
  - onClick prop 추가
- `frontend/components/glossary/glossary-form-dialog.tsx`
  - 관련 용어 선택 UI 추가
- `frontend/hooks/use-glossary.ts`
  - useRelatedTerms hook 추가 (선택적)

## 구현 순서

1. `glossary-detail-dialog.tsx` 컴포넌트 생성
2. `page.tsx`에 상세 다이얼로그 연결
3. 카드 클릭 이벤트 처리
4. 관련 용어 표시 기능
5. 관련 용어 선택 UI (폼 개선)

## 예상 작업량

- 컴포넌트 개발: 2-3시간
- 통합 테스트: 1시간

## 의존성

- Phase 17-A 완료 (✅)
