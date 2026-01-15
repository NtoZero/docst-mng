# Phase 14: Semantic Search 고도화 계획

## 개요

현재 시맨틱 서치 서비스의 주요 문제점:
1. **검색 모드 기본값이 keyword** - 시맨틱 서치 활용도 저하
2. **similarityThreshold 미노출** - 하드코딩 0.5로 고정
3. **topK 외 파라미터 제한** - 사용자 맞춤 검색 불가
4. **MCP Tool 파라미터 부족** - AI 에이전트의 검색 최적화 불가
5. **Playground UI 미제공** - 검색 파라미터 실험 불가

## 결정 사항

| 항목 | 결정 |
|-----|-----|
| 기본 검색 모드 | `keyword` → `semantic` 변경 |
| auto 모드(QueryRouter) | Phase 14 범위에서 제외 (파라미터 노출에 집중) |
| Frontend UI 위치 | 좌측 사이드바 (항상 표시, 실시간 조정) |

## Phase 구성

| Phase | 영역 | 주요 내용 |
|-------|-----|---------|
| 14-A | Backend Search API | SearchController 파라미터 확장, 응답 메타데이터 |
| 14-B | MCP Tool | search_documents 도구 파라미터 확장 |
| 14-C | Frontend Playground | 검색 파라미터 사이드바, 실시간 미리보기 |

## 예상 영향

| 영역 | 변경 사항 | 호환성 |
|-----|---------|--------|
| REST API | 새 쿼리 파라미터 추가 | 하위 호환 (optional) |
| MCP Tool | 새 @ToolParam 추가 | 하위 호환 (optional) |
| Frontend | 새 UI 컴포넌트 추가 | 영향 없음 |

## 파일 변경 요약

### Phase 14-A (Backend API)
- `backend/src/main/java/com/docst/search/api/SearchController.java`
- `backend/src/main/java/com/docst/api/ApiModels.java`
- `backend/src/main/java/com/docst/search/service/SemanticSearchService.java`

### Phase 14-B (MCP Tool)
- `backend/src/main/java/com/docst/mcp/tools/McpDocumentTools.java`
- `backend/src/main/java/com/docst/mcp/McpModels.java`

### Phase 14-C (Frontend)
- `frontend/app/[locale]/playground/page.tsx`
- `frontend/components/playground/search-params-panel.tsx` (신규)
- `frontend/components/playground/search-preview.tsx` (신규)
- `frontend/lib/types.ts`
- `frontend/hooks/use-llm-chat.ts`

## 관련 문서

- [Phase 14-A: Backend Search API 고도화](./phase14-a-backend-search-api.md)
- [Phase 14-B: MCP Tool Parameter 고도화](./phase14-b-mcp-tool-parameter.md)
- [Phase 14-C: Frontend Playground 고도화](./phase14-c-frontend-playground.md)
