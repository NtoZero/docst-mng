# Phase 17-C: 일괄 등록 (Batch Import)

**상태**: 🔲 예정

## 개요

CSV 또는 JSON 파일을 업로드하여 용어를 일괄 등록/수정하는 기능.

## 파일 형식

### CSV Format

```csv
name,definition,category,abbreviation,synonyms
"Single Sign-On","A session and user authentication service...","Authentication","SSO","SSO;싱글사인온"
"API Gateway","A server that acts as an API front-end...","Architecture",,"API GW;게이트웨이"
```

**컬럼 설명**:
| 컬럼 | 필수 | 설명 |
|------|------|------|
| name | ✅ | 용어명 (unique per project) |
| definition | ✅ | 정의 |
| category | ❌ | 카테고리 |
| abbreviation | ❌ | 약어 |
| synonyms | ❌ | 동의어 (세미콜론 구분) |

### JSON Format

```json
{
  "terms": [
    {
      "name": "Single Sign-On",
      "definition": "A session and user authentication service...",
      "category": "Authentication",
      "abbreviation": "SSO",
      "synonyms": ["SSO", "싱글사인온"]
    },
    {
      "name": "API Gateway",
      "definition": "A server that acts as an API front-end...",
      "category": "Architecture",
      "synonyms": ["API GW", "게이트웨이"]
    }
  ]
}
```

## Backend 구현

### 1. DTO

```java
// GlossaryController.java 내 추가
public record BatchImportRequest(
    List<GlossaryTermImportItem> terms,
    boolean updateExisting  // 기존 용어 업데이트 여부
) {}

public record GlossaryTermImportItem(
    @NotBlank String name,
    @NotBlank String definition,
    String category,
    String abbreviation,
    List<String> synonyms
) {}

public record BatchImportResponse(
    int totalCount,
    int createdCount,
    int updatedCount,
    int skippedCount,
    List<BatchImportError> errors
) {}

public record BatchImportError(
    int lineNumber,
    String termName,
    String errorMessage
) {}
```

### 2. Service 메서드

```java
// GlossaryService.java
@Transactional
public BatchImportResponse batchImport(
    UUID projectId,
    UUID userId,
    List<GlossaryTermImportItem> items,
    boolean updateExisting
) {
    int created = 0, updated = 0, skipped = 0;
    List<BatchImportError> errors = new ArrayList<>();

    for (int i = 0; i < items.size(); i++) {
        GlossaryTermImportItem item = items.get(i);
        try {
            Optional<GlossaryTerm> existing = repository
                .findByProjectIdAndName(projectId, item.name());

            if (existing.isPresent()) {
                if (updateExisting) {
                    updateFromItem(existing.get(), item);
                    updated++;
                } else {
                    skipped++;
                }
            } else {
                createFromItem(projectId, userId, item);
                created++;
            }
        } catch (Exception e) {
            errors.add(new BatchImportError(i + 1, item.name(), e.getMessage()));
        }
    }

    return new BatchImportResponse(items.size(), created, updated, skipped, errors);
}
```

### 3. Controller Endpoint

```java
// GlossaryController.java
@PostMapping("/batch")
@RequireProjectRole(ProjectRole.EDITOR)
public ResponseEntity<BatchImportResponse> batchImport(
    @PathVariable UUID projectId,
    @RequestBody @Valid BatchImportRequest request
) {
    UUID userId = SecurityUtils.requireCurrentUserId();
    return ResponseEntity.ok(
        glossaryService.batchImport(projectId, userId, request.terms(), request.updateExisting())
    );
}

@PostMapping("/batch/csv")
@RequireProjectRole(ProjectRole.EDITOR)
public ResponseEntity<BatchImportResponse> batchImportCsv(
    @PathVariable UUID projectId,
    @RequestParam("file") MultipartFile file,
    @RequestParam(defaultValue = "false") boolean updateExisting
) {
    UUID userId = SecurityUtils.requireCurrentUserId();
    List<GlossaryTermImportItem> items = parseCsv(file);
    return ResponseEntity.ok(
        glossaryService.batchImport(projectId, userId, items, updateExisting)
    );
}
```

### 4. CSV 파싱

```java
private List<GlossaryTermImportItem> parseCsv(MultipartFile file) {
    try (CSVReader reader = new CSVReaderBuilder(
            new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))
            .withSkipLines(1)  // Skip header
            .build()) {

        return reader.readAll().stream()
            .map(row -> new GlossaryTermImportItem(
                row[0],  // name
                row[1],  // definition
                row.length > 2 ? row[2] : null,  // category
                row.length > 3 ? row[3] : null,  // abbreviation
                row.length > 4 && !row[4].isBlank()
                    ? Arrays.asList(row[4].split(";"))
                    : null
            ))
            .toList();
    }
}
```

**의존성 추가** (`build.gradle.kts`):
```kotlin
implementation("com.opencsv:opencsv:5.9")
```

## Frontend 구현

### 1. Import 다이얼로그

**컴포넌트**: `frontend/components/glossary/glossary-import-dialog.tsx`

```typescript
interface GlossaryImportDialogProps {
  projectId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
}
```

**UI 구성**:
- 파일 드래그 앤 드롭 영역
- 파일 형식 선택 (CSV/JSON)
- "기존 용어 업데이트" 체크박스
- 미리보기 테이블 (선택적)
- Import 결과 요약

### 2. API Client

```typescript
// lib/api.ts
export const glossaryApi = {
  // ... existing methods

  batchImport: async (
    projectId: string,
    terms: GlossaryTermImportItem[],
    updateExisting: boolean
  ) => {
    const response = await fetch(
      `${API_BASE}/api/projects/${projectId}/glossary/batch`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...authHeaders() },
        body: JSON.stringify({ terms, updateExisting }),
      }
    );
    return response.json() as Promise<BatchImportResponse>;
  },

  batchImportCsv: async (
    projectId: string,
    file: File,
    updateExisting: boolean
  ) => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('updateExisting', String(updateExisting));

    const response = await fetch(
      `${API_BASE}/api/projects/${projectId}/glossary/batch/csv`,
      {
        method: 'POST',
        headers: authHeaders(),
        body: formData,
      }
    );
    return response.json() as Promise<BatchImportResponse>;
  },
};
```

### 3. Types

```typescript
// lib/types.ts
export interface GlossaryTermImportItem {
  name: string;
  definition: string;
  category?: string;
  abbreviation?: string;
  synonyms?: string[];
}

export interface BatchImportResponse {
  totalCount: number;
  createdCount: number;
  updatedCount: number;
  skippedCount: number;
  errors: BatchImportError[];
}

export interface BatchImportError {
  lineNumber: number;
  termName: string;
  errorMessage: string;
}
```

## 신규/수정 파일

### Backend 신규
- CSV 파싱 로직 (GlossaryController 내)

### Backend 수정
- `GlossaryController.java` - batch endpoints
- `GlossaryService.java` - batchImport 메서드
- `build.gradle.kts` - opencsv 의존성

### Frontend 신규
- `frontend/components/glossary/glossary-import-dialog.tsx`

### Frontend 수정
- `frontend/app/[locale]/projects/[projectId]/glossary/page.tsx`
- `frontend/lib/api.ts` - batch API
- `frontend/lib/types.ts` - import types
- `frontend/hooks/use-glossary.ts` - useBatchImport mutation

## Export 기능 (선택적)

### Backend
```java
@GetMapping("/export")
@RequireProjectRole(ProjectRole.VIEWER)
public ResponseEntity<byte[]> exportCsv(@PathVariable UUID projectId) {
    List<GlossaryTerm> terms = glossaryService.listByProject(projectId, null);
    byte[] csv = generateCsv(terms);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=glossary.csv")
        .contentType(MediaType.parseMediaType("text/csv"))
        .body(csv);
}
```

### Frontend
- "Export CSV" 버튼 추가
- 다운로드 처리

## 구현 순서

1. Backend DTO 및 Service 구현
2. CSV 파싱 로직 (opencsv)
3. Controller endpoints
4. Frontend import dialog
5. API client 및 hooks
6. (선택) Export 기능

## 의존성

- Phase 17-A 완료 (✅)
