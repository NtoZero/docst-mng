# Phase 14-A: Backend Search API 고도화

## 목표

SearchController에 시맨틱 서치 관련 파라미터를 노출하고, 검색 품질을 향상시킨다.

## 변경 파일

| 파일 | 변경 내용 |
|-----|---------|
| `SearchController.java` | 파라미터 확장, 응답 구조 변경 |
| `ApiModels.java` | SearchResponse, SearchMetadata 레코드 추가 |
| `SemanticSearchService.java` | 임계값 파라미터 전달 로직 |

## 구현 내용

### 1. SearchController 파라미터 확장

**현재 구현**:
```java
@GetMapping
public ResponseEntity<List<SearchResultResponse>> search(
    @PathVariable UUID projectId,
    @RequestParam(name = "q") String query,
    @RequestParam(required = false, defaultValue = "keyword") String mode,
    @RequestParam(required = false, defaultValue = "10") Integer topK
)
```

**변경 후**:
```java
@GetMapping
public ResponseEntity<SearchResponse> search(
    @PathVariable UUID projectId,
    @RequestParam(name = "q") String query,
    @RequestParam(required = false, defaultValue = "semantic") String mode,  // 기본값 변경
    @RequestParam(required = false, defaultValue = "10") Integer topK,
    // 새 파라미터들
    @RequestParam(required = false) Double similarityThreshold,  // 0.0-1.0
    @RequestParam(required = false) String fusionStrategy,       // rrf, weighted_sum
    @RequestParam(required = false) Integer rrfK,                // RRF K값
    @RequestParam(required = false) Double vectorWeight,         // 벡터 가중치
    @RequestParam(required = false) Double keywordWeight         // 키워드 가중치
)
```

### 2. 새 파라미터 설명

| 파라미터 | 타입 | 기본값 | 범위 | 설명 |
|---------|-----|-------|-----|-----|
| `mode` | String | semantic | keyword, semantic, graph, hybrid | 검색 모드 |
| `topK` | Integer | 10 | 1-100 | 결과 개수 제한 |
| `similarityThreshold` | Double | 0.3 | 0.0-1.0 | 벡터 유사도 임계값 |
| `fusionStrategy` | String | rrf | rrf, weighted_sum | 하이브리드 융합 전략 |
| `rrfK` | Integer | 60 | 1-100 | RRF 상수 K |
| `vectorWeight` | Double | 0.6 | 0.0-1.0 | 벡터 검색 가중치 |
| `keywordWeight` | Double | 0.4 | 0.0-1.0 | 키워드 검색 가중치 |

### 3. 응답 모델 확장

**ApiModels.java에 추가**:
```java
public record SearchResponse(
    List<SearchResultResponse> results,
    SearchMetadata metadata
) {}

public record SearchMetadata(
    String mode,
    int totalResults,
    double similarityThreshold,
    String fusionStrategy,
    long queryTimeMs
) {}
```

### 4. 기본값 최적화 근거

| 파라미터 | 현재 기본값 | 새 기본값 | 변경 근거 |
|---------|-----------|----------|---------|
| mode | keyword | semantic | 시맨틱 서치가 프로젝트 주 목적 |
| similarityThreshold | 0.5 (하드코딩) | 0.3 | 더 많은 관련 결과 포함, 사용자가 조정 가능 |
| fusionStrategy | rrf | rrf | RRF가 일반적으로 더 안정적인 결과 |
| rrfK | 60 | 60 | 표준값 유지 (IR 연구 기반) |

## 검증 방법

```bash
# 1. 기본 시맨틱 검색 (새 기본값 확인)
curl "http://localhost:8342/api/projects/{projectId}/search?q=authentication"

# 2. 임계값 조정 테스트
curl "http://localhost:8342/api/projects/{projectId}/search?q=authentication&similarityThreshold=0.5"

# 3. 하이브리드 검색 + 융합 전략
curl "http://localhost:8342/api/projects/{projectId}/search?q=authentication&mode=hybrid&fusionStrategy=weighted_sum&vectorWeight=0.7"

# 4. 응답 메타데이터 확인
# 응답에 metadata 필드가 포함되어야 함
```

**예상 응답**:
```json
{
  "results": [
    {
      "documentId": "uuid",
      "path": "docs/auth/login.md",
      "score": 0.85,
      "snippet": "...authentication flow...",
      "highlightedSnippet": "...**authentication** flow..."
    }
  ],
  "metadata": {
    "mode": "semantic",
    "totalResults": 5,
    "similarityThreshold": 0.3,
    "fusionStrategy": null,
    "queryTimeMs": 45
  }
}
```

## 구현 순서

1. `ApiModels.java`에 SearchResponse, SearchMetadata 레코드 추가
2. `SearchController.java` 파라미터 확장 및 응답 타입 변경
3. 파라미터를 서비스 레이어로 전달하는 로직 구현
4. 단위 테스트 작성
5. 통합 테스트 검증
