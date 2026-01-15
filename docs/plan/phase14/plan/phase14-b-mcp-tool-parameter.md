# Phase 14-B: MCP Tool Parameter 고도화

## 목표

MCP `search_documents` 도구에 시맨틱 서치 파라미터를 추가하여 AI 에이전트가 검색을 최적화할 수 있도록 한다.

## 변경 파일

| 파일 | 변경 내용 |
|-----|---------|
| `McpDocumentTools.java` | search_documents 파라미터 확장, graph 모드 지원 |
| `McpModels.java` | SearchMetadata 필드 확장 |

## 현재 상태 분석

**현재 MCP search_documents 파라미터**:

| 파라미터 | 필수 | 기본값 | 설명 |
|---------|-----|-------|-----|
| projectId | O | - | 프로젝트 ID |
| query | O | - | 검색 쿼리 |
| mode | X | keyword | keyword, semantic, hybrid |
| topK | X | 10 | 결과 개수 |

**문제점**:
- `graph` 모드 미지원 (REST API는 지원)
- `similarityThreshold` 미노출 (하드코딩 0.5)
- 융합 전략 파라미터 미노출

## 구현 내용

### 1. search_documents 도구 파라미터 확장

**현재 구현**:
```java
@Tool(name = "search_documents", description = "...")
public SearchDocumentsResult searchDocuments(
    @ToolParam(description = "Project ID") String projectId,
    @ToolParam(description = "Search query") String query,
    @ToolParam(description = "Search mode", required = false) String mode,
    @ToolParam(description = "Max results", required = false) Integer topK
)
```

**변경 후**:
```java
@Tool(name = "search_documents", description = "Search documents with advanced semantic options. " +
    "Supports keyword, semantic, graph, and hybrid modes with configurable parameters.")
public SearchDocumentsResult searchDocuments(
    @ToolParam(description = "Project ID (UUID format)")
    String projectId,

    @ToolParam(description = "Search query (keywords or natural language)")
    String query,

    @ToolParam(description = "Search mode: keyword, semantic, graph, hybrid (default: semantic)", required = false)
    String mode,

    @ToolParam(description = "Maximum number of results (default: 10)", required = false)
    Integer topK,

    @ToolParam(description = "Similarity threshold 0.0-1.0 for semantic/hybrid search. " +
        "Lower values return more results (default: 0.3)", required = false)
    Double similarityThreshold,

    @ToolParam(description = "Fusion strategy for hybrid mode: 'rrf' (Reciprocal Rank Fusion) or " +
        "'weighted_sum' (default: rrf)", required = false)
    String fusionStrategy,

    @ToolParam(description = "RRF constant K for rrf strategy (default: 60)", required = false)
    Integer rrfK,

    @ToolParam(description = "Vector search weight 0.0-1.0 for weighted_sum strategy (default: 0.6)", required = false)
    Double vectorWeight
)
```

### 2. 새 파라미터 설명

| 파라미터 | 타입 | 필수 | 기본값 | AI 에이전트 활용 시나리오 |
|---------|-----|-----|-------|----------------------|
| `similarityThreshold` | Double | X | 0.3 | 결과가 적을 때 임계값 낮춤 (0.2), 정확도 필요시 높임 (0.5) |
| `fusionStrategy` | String | X | rrf | 키워드 매칭 중요시 weighted_sum + keywordWeight 높임 |
| `rrfK` | Integer | X | 60 | 랭킹 민감도 조절 (낮을수록 상위 결과 가중치 증가) |
| `vectorWeight` | Double | X | 0.6 | 의미 검색 중요도 조절 |

### 3. graph 모드 지원 추가

**검색 로직 수정**:
```java
var results = switch (searchMode) {
    case "semantic" -> semanticSearchService.searchSemantic(projId, query, limit, threshold);
    case "graph" -> neo4jSearchStrategy.search(projId, query, limit);  // 추가
    case "hybrid" -> hybridSearchService.hybridSearch(projId, query, fusionParams, strategy);
    default -> searchService.searchByKeyword(projId, query, limit);
};
```

### 4. 응답 메타데이터 확장

**McpModels.java**:
```java
public record SearchMetadata(
    String mode,
    int totalResults,
    String queryTime,
    // 새 필드들
    Double similarityThreshold,
    String fusionStrategy,
    Integer rrfK,
    Double vectorWeight
) {}
```

## AI 에이전트 활용 예시

### 기본 시맨틱 검색
```json
{
  "tool": "search_documents",
  "parameters": {
    "projectId": "uuid",
    "query": "authentication flow"
  }
}
```

### 정확도 높은 검색
```json
{
  "tool": "search_documents",
  "parameters": {
    "projectId": "uuid",
    "query": "JWT token validation",
    "mode": "semantic",
    "similarityThreshold": 0.5,
    "topK": 5
  }
}
```

### 재현율 높은 검색
```json
{
  "tool": "search_documents",
  "parameters": {
    "projectId": "uuid",
    "query": "error handling",
    "mode": "hybrid",
    "similarityThreshold": 0.2,
    "topK": 20
  }
}
```

### 그래프 기반 관계 탐색
```json
{
  "tool": "search_documents",
  "parameters": {
    "projectId": "uuid",
    "query": "UserService dependencies",
    "mode": "graph",
    "topK": 15
  }
}
```

## 검증 방법

### MCP 클라이언트 테스트

```bash
# mcp-remote를 통한 테스트
# 1. 기본 검색 (새 기본값 확인)
mcp call search_documents '{"projectId": "uuid", "query": "authentication"}'

# 2. 임계값 조정
mcp call search_documents '{"projectId": "uuid", "query": "authentication", "similarityThreshold": 0.5}'

# 3. graph 모드
mcp call search_documents '{"projectId": "uuid", "query": "dependencies", "mode": "graph"}'

# 4. 하이브리드 + 가중치
mcp call search_documents '{"projectId": "uuid", "query": "login", "mode": "hybrid", "fusionStrategy": "weighted_sum", "vectorWeight": 0.8}'
```

### 예상 응답

```json
{
  "results": [
    {
      "documentId": "uuid",
      "path": "docs/auth/login.md",
      "headingPath": "Authentication > Login Flow",
      "score": 0.85,
      "snippet": "...authentication flow..."
    }
  ],
  "metadata": {
    "mode": "semantic",
    "totalResults": 5,
    "queryTime": "45ms",
    "similarityThreshold": 0.3,
    "fusionStrategy": null,
    "rrfK": null,
    "vectorWeight": null
  }
}
```

## 구현 순서

1. `McpModels.java`에 SearchMetadata 필드 확장
2. `McpDocumentTools.java` search_documents 메서드 파라미터 추가
3. graph 모드 분기 로직 추가
4. 파라미터를 서비스 레이어로 전달하는 로직 구현
5. MCP 클라이언트 테스트
