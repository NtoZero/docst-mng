# Phase 17-A: 기본 용어 관리

**상태**: ✅ 완료

## 개요

프로젝트별 용어 사전의 기본 CRUD 기능과 검색 기능 구현.

## 구현 범위

### 1. 데이터베이스

```sql
CREATE TABLE dm_glossary_term (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES dm_project(id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,
    definition TEXT NOT NULL,
    synonyms JSONB DEFAULT '[]',      -- ["API Key", "api-key"]
    category VARCHAR(100),             -- "Architecture", "API", "Domain"
    abbreviation VARCHAR(50),          -- "SSO" for "Single Sign-On"
    related_terms JSONB DEFAULT '[]',  -- [<term_id>, ...]
    created_by UUID REFERENCES dm_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (project_id, name)
);

-- Full-text search index
CREATE INDEX idx_glossary_term_fts ON dm_glossary_term
    USING GIN (to_tsvector('english', name || ' ' || COALESCE(definition, '')));
```

### 2. Backend 구조

```
backend/src/main/java/com/docst/glossary/
├── GlossaryTerm.java                 # Entity
├── repository/
│   └── GlossaryTermRepository.java   # JPA Repository
├── service/
│   └── GlossaryService.java          # Business Logic
└── api/
    └── GlossaryController.java       # REST Controller
```

### 3. 검색 기능

#### 키워드 검색
```java
@Query("SELECT t FROM GlossaryTerm t WHERE t.project.id = :projectId " +
       "AND (LOWER(t.name) LIKE LOWER(CONCAT('%', :query, '%')) " +
       "OR LOWER(t.definition) LIKE LOWER(CONCAT('%', :query, '%')))")
List<GlossaryTerm> searchByKeyword(UUID projectId, String query);
```

#### 시맨틱 검색
- PgVectorStore 활용
- metadata.content_type = 'glossary_term' 필터링
- 용어 정의 텍스트 임베딩 저장

### 4. Frontend 구조

```
frontend/
├── app/[locale]/projects/[projectId]/glossary/
│   └── page.tsx                      # 메인 페이지
├── components/glossary/
│   ├── glossary-term-card.tsx        # 카드 컴포넌트
│   ├── glossary-form-dialog.tsx      # 추가/수정 다이얼로그
│   └── glossary-search.tsx           # 검색 컴포넌트
└── hooks/
    └── use-glossary.ts               # TanStack Query hooks
```

### 5. MCP 조회 도구

| Tool | Description |
|------|-------------|
| `list_glossary_terms` | 용어 목록 조회 (카테고리 필터) |
| `search_glossary` | 키워드/시맨틱 검색 |
| `get_glossary_term` | 용어 상세 조회 |

## 구현 파일 목록

### Backend
- `backend/src/main/resources/db/migration/V19__add_glossary_term.sql`
- `backend/src/main/java/com/docst/glossary/GlossaryTerm.java`
- `backend/src/main/java/com/docst/glossary/repository/GlossaryTermRepository.java`
- `backend/src/main/java/com/docst/glossary/service/GlossaryService.java`
- `backend/src/main/java/com/docst/glossary/api/GlossaryController.java`
- `backend/src/main/java/com/docst/mcp/tools/McpGlossaryTools.java`

### Frontend
- `frontend/app/[locale]/projects/[projectId]/glossary/page.tsx`
- `frontend/components/glossary/glossary-term-card.tsx`
- `frontend/components/glossary/glossary-form-dialog.tsx`
- `frontend/components/glossary/glossary-search.tsx`
- `frontend/hooks/use-glossary.ts`
- `frontend/lib/types.ts` (GlossaryTerm 타입 추가)
- `frontend/lib/api.ts` (glossaryApi 추가)
- `frontend/components/sidebar.tsx` (메뉴 추가)

## 검증 결과

- ✅ Backend 컴파일: `./gradlew compileJava` 성공
- ✅ Frontend 타입 검사: `npx tsc --noEmit` 성공
- ✅ MCP 도구 등록: McpServerConfig에 glossaryTools 추가 완료
