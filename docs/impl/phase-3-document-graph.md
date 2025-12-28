# Phase 3-D: 문서 관계 그래프 구현

## 개요

문서 간 링크를 분석하여 그래프로 시각화하고, 영향 분석(Impact Analysis) 기능을 제공합니다.

### 주요 기능
- **링크 추출**: 마크다운 문서에서 링크 자동 추출 (`[text](url)`, `[[wiki]]`)
- **그래프 생성**: 프로젝트/레포지토리/문서 단위 관계 그래프
- **영향 분석**: 문서 변경 시 영향 받는 문서 파악
- **깨진 링크 탐지**: 목적지가 없는 링크 자동 탐지

---

## 구현 내용

### 1. DocumentLink 엔티티

**위치**: `backend/src/main/java/com/docst/domain/DocumentLink.java`

문서 간 링크 관계를 저장하는 엔티티입니다.

```java
@Entity
@Table(name = "dm_document_link")
public class DocumentLink {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Document sourceDocument;  // 링크 시작점

    @ManyToOne(fetch = FetchType.LAZY)
    private Document targetDocument;  // 링크 목적지 (null이면 깨진 링크)

    private String linkText;         // 원본 링크 텍스트
    private LinkType linkType;       // INTERNAL, WIKI, EXTERNAL, ANCHOR
    private boolean broken;          // 깨진 링크 여부
    private Integer lineNumber;      // 라인 번호
    private String anchorText;       // 표시 텍스트
    private Instant createdAt;
}
```

**링크 타입**:
- `INTERNAL`: 상대 경로 링크 (`./docs/api.md`, `../README.md`)
- `WIKI`: Wiki 스타일 링크 (`[[API Documentation]]`)
- `EXTERNAL`: 외부 링크 (`https://example.com`)
- `ANCHOR`: 앵커 링크 (`#section-1`)

**인덱스**:
- `idx_document_link_source`: 나가는 링크 조회 최적화
- `idx_document_link_target`: 들어오는 링크 조회 최적화
- `idx_document_link_type`: 링크 타입별 필터링
- `idx_document_link_broken`: 깨진 링크 조회 최적화

---

### 2. LinkParser

**위치**: `backend/src/main/java/com/docst/git/LinkParser.java`

마크다운 문서에서 링크를 추출하는 파서입니다.

#### 지원하는 링크 형식

```markdown
# 1. 마크다운 링크: [text](url)
[Getting Started](./docs/start.md)
[API Reference](../api/README.md)
[External Link](https://example.com)
[Anchor](#section-1)

# 2. Wiki 링크: [[page]] 또는 [[page|text]]
[[API Documentation]]
[[concepts/architecture]]
[[Getting Started|Start Here]]
```

#### 링크 추출 로직

```java
public List<ParsedLink> extractLinks(String content) {
    List<ParsedLink> links = new ArrayList<>();
    String[] lines = content.split("\n");

    for (int i = 0; i < lines.length; i++) {
        String line = lines[i];
        int lineNumber = i + 1;

        // Wiki 링크: [[page]] 또는 [[page|text]]
        Matcher wikiMatcher = WIKI_LINK_PATTERN.matcher(line);
        while (wikiMatcher.find()) {
            String target = wikiMatcher.group(1).trim();
            String anchorText = wikiMatcher.group(2);
            if (anchorText == null) anchorText = target;
            links.add(new ParsedLink(target, LinkType.WIKI, anchorText.trim(), lineNumber));
        }

        // 마크다운 링크: [text](url)
        Matcher mdMatcher = MARKDOWN_LINK_PATTERN.matcher(line);
        while (mdMatcher.find()) {
            String anchorText = mdMatcher.group(1).trim();
            String url = mdMatcher.group(2).trim();
            LinkType linkType = determineLinkType(url);
            links.add(new ParsedLink(url, linkType, anchorText, lineNumber));
        }
    }

    return links;
}
```

---

### 3. DocumentLinkService

**위치**: `backend/src/main/java/com/docst/service/DocumentLinkService.java`

링크 추출, 저장, 해결 로직을 제공하는 서비스입니다.

#### 링크 추출 및 저장

```java
@Transactional
public void extractAndSaveLinks(Document document, String content) {
    // 기존 링크 삭제
    documentLinkRepository.deleteBySourceDocumentId(document.getId());

    // 링크 추출
    List<ParsedLink> parsedLinks = linkParser.extractLinks(content);

    // 링크 저장
    for (ParsedLink parsedLink : parsedLinks) {
        DocumentLink link = new DocumentLink(
            document,
            parsedLink.getLinkText(),
            parsedLink.getLinkType(),
            parsedLink.getAnchorText(),
            parsedLink.getLineNumber()
        );

        // 내부 링크인 경우 목적지 문서 해결 시도
        if (link.isInternal()) {
            Optional<Document> targetDoc = resolveTargetDocument(document, parsedLink.getLinkText());
            targetDoc.ifPresent(link::setTargetDocument);
        }

        documentLinkRepository.save(link);
    }
}
```

#### 목적지 문서 해결

```java
private Optional<Document> resolveTargetDocument(Document sourceDocument, String linkText) {
    // Wiki 링크: [[page]] → page.md 또는 page/index.md
    if (!linkText.contains("/") && !linkText.endsWith(".md")) {
        String parentPath = getParentPath(sourceDocument.getPath());
        String candidatePath1 = parentPath.isEmpty() ? linkText + ".md" : parentPath + "/" + linkText + ".md";
        String candidatePath2 = parentPath.isEmpty() ? linkText + "/index.md" : parentPath + "/" + linkText + "/index.md";

        Optional<Document> doc = documentRepository.findByRepositoryIdAndPath(
            sourceDocument.getRepository().getId(), candidatePath1);
        if (doc.isPresent()) return doc;

        return documentRepository.findByRepositoryIdAndPath(
            sourceDocument.getRepository().getId(), candidatePath2);
    }

    // 상대 경로 링크: ./docs/api.md, ../README.md
    String resolvedPath = resolveRelativePath(sourceDocument.getPath(), linkText);
    return documentRepository.findByRepositoryIdAndPath(
        sourceDocument.getRepository().getId(), resolvedPath);
}
```

**경로 해결 로직**:
1. Wiki 링크 (`[[page]]`):
   - 같은 디렉토리에서 `page.md` 찾기
   - 없으면 `page/index.md` 찾기
2. 상대 경로 링크 (`./docs/api.md`, `../README.md`):
   - Java Path API로 상대 경로 해결
   - 앵커(`#section`) 및 쿼리(`?param=value`) 제거

---

### 4. GitSyncService 통합

**위치**: `backend/src/main/java/com/docst/service/GitSyncService.java:257`

동기화 시 자동으로 링크를 추출하도록 통합했습니다.

```java
private void processDocument(Git git, Repository repo, String path,
                               String commitSha, CommitInfo commitInfo) {
    // ... 문서 파싱 및 저장 ...

    if (newVersion != null) {
        try {
            // Step 1: Chunking
            chunkingService.chunkAndSave(newVersion);

            // Step 2: Embedding
            embeddingService.embedDocumentVersion(newVersion);

            // Step 3: Link extraction (NEW!)
            documentLinkService.extractAndSaveLinks(newVersion.getDocument(), content);
            log.debug("Extracted links for document: {}", path);

        } catch (Exception error) {
            log.error("Failed to chunk/embed/extract links for document: {}", path, error);
        }
    }
}
```

---

### 5. GraphService

**위치**: `backend/src/main/java/com/docst/service/GraphService.java`

문서 관계 그래프 분석을 제공하는 서비스입니다.

#### 5.1 프로젝트 그래프

```java
@Transactional(readOnly = true)
public GraphData getProjectGraph(UUID projectId) {
    // 프로젝트의 모든 내부 링크 조회
    List<DocumentLink> links = documentLinkRepository.findInternalLinksByProjectId(projectId);
    return buildGraph(links);
}
```

#### 5.2 문서 중심 그래프 (BFS 탐색)

```java
@Transactional(readOnly = true)
public GraphData getDocumentGraph(UUID documentId, int depth) {
    Set<UUID> visitedDocuments = new HashSet<>();
    Set<DocumentLink> collectedLinks = new HashSet<>();
    Queue<UUID> queue = new LinkedList<>();
    Map<UUID, Integer> depthMap = new HashMap<>();

    queue.add(documentId);
    depthMap.put(documentId, 0);
    visitedDocuments.add(documentId);

    while (!queue.isEmpty()) {
        UUID currentDocId = queue.poll();
        int currentDepth = depthMap.get(currentDocId);

        if (currentDepth >= depth) continue;

        // 나가는 링크
        List<DocumentLink> outgoingLinks = documentLinkRepository.findBySourceDocumentId(currentDocId);
        for (DocumentLink link : outgoingLinks) {
            if (link.getTargetDocument() != null && !link.isBroken()) {
                collectedLinks.add(link);
                UUID targetId = link.getTargetDocument().getId();
                if (!visitedDocuments.contains(targetId)) {
                    visitedDocuments.add(targetId);
                    depthMap.put(targetId, currentDepth + 1);
                    queue.add(targetId);
                }
            }
        }

        // 들어오는 링크
        List<DocumentLink> incomingLinks = documentLinkRepository.findByTargetDocumentId(currentDocId);
        for (DocumentLink link : incomingLinks) {
            collectedLinks.add(link);
            UUID sourceId = link.getSourceDocument().getId();
            if (!visitedDocuments.contains(sourceId)) {
                visitedDocuments.add(sourceId);
                depthMap.put(sourceId, currentDepth + 1);
                queue.add(sourceId);
            }
        }
    }

    return buildGraph(new ArrayList<>(collectedLinks));
}
```

#### 5.3 영향 분석 (Impact Analysis)

```java
@Transactional(readOnly = true)
public ImpactAnalysis analyzeImpact(UUID documentId) {
    // 직접 영향: 이 문서를 참조하는 문서 (depth 1)
    List<DocumentLink> incomingLinks = documentLinkRepository.findByTargetDocumentId(documentId);
    List<ImpactedDocument> directImpact = incomingLinks.stream()
        .map(link -> new ImpactedDocument(
            link.getSourceDocument().getId(),
            link.getSourceDocument().getPath(),
            link.getSourceDocument().getTitle(),
            1, // depth 1
            link.getLinkType().name(),
            link.getAnchorText()
        ))
        .collect(Collectors.toList());

    // 간접 영향: 직접 참조 문서를 참조하는 문서 (depth 2)
    Set<UUID> indirectDocIds = new HashSet<>();
    for (DocumentLink link : incomingLinks) {
        UUID sourceDocId = link.getSourceDocument().getId();
        List<DocumentLink> indirectLinks = documentLinkRepository.findByTargetDocumentId(sourceDocId);
        for (DocumentLink indirectLink : indirectLinks) {
            indirectDocIds.add(indirectLink.getSourceDocument().getId());
        }
    }

    // 직접 영향 문서 및 자기 자신 제외
    Set<UUID> directDocIds = incomingLinks.stream()
        .map(link -> link.getSourceDocument().getId())
        .collect(Collectors.toSet());
    indirectDocIds.removeAll(directDocIds);
    indirectDocIds.remove(documentId);

    List<ImpactedDocument> indirectImpact = ...;

    return new ImpactAnalysis(documentId, totalImpacted, directImpact, indirectImpact);
}
```

---

### 6. GraphController

**위치**: `backend/src/main/java/com/docst/api/GraphController.java`

그래프 및 링크 분석 REST API를 제공합니다.

#### API 엔드포인트

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/projects/{projectId}/graph` | 프로젝트 전체 그래프 |
| GET | `/api/repositories/{repositoryId}/graph` | 레포지토리 그래프 |
| GET | `/api/documents/{documentId}/graph?depth=1` | 문서 중심 그래프 |
| GET | `/api/documents/{documentId}/links/outgoing` | 나가는 링크 |
| GET | `/api/documents/{documentId}/links/incoming` | 들어오는 링크 |
| GET | `/api/documents/{documentId}/impact` | 영향 분석 |
| GET | `/api/repositories/{repositoryId}/links/broken` | 깨진 링크 |

---

## 데이터베이스 스키마

### dm_document_link 테이블

```sql
CREATE TABLE dm_document_link (
    id UUID PRIMARY KEY,
    source_document_id UUID NOT NULL REFERENCES dm_document(id) ON DELETE CASCADE,
    target_document_id UUID REFERENCES dm_document(id) ON DELETE CASCADE,
    link_text VARCHAR(1000) NOT NULL,
    link_type VARCHAR(20) NOT NULL,
    is_broken BOOLEAN NOT NULL DEFAULT TRUE,
    line_number INTEGER,
    anchor_text VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_document_link_source ON dm_document_link(source_document_id);
CREATE INDEX idx_document_link_target ON dm_document_link(target_document_id);
CREATE INDEX idx_document_link_type ON dm_document_link(link_type);
CREATE INDEX idx_document_link_broken ON dm_document_link(is_broken);
```

---

## API 명세

### GET /api/projects/{projectId}/graph

프로젝트 전체의 문서 관계 그래프를 조회합니다.

**Response**:
```json
{
  "nodes": [
    {
      "id": "uuid",
      "path": "README.md",
      "title": "Getting Started",
      "docType": "MD",
      "outgoingLinks": 3,
      "incomingLinks": 5
    }
  ],
  "edges": [
    {
      "id": "uuid",
      "source": "source-doc-uuid",
      "target": "target-doc-uuid",
      "linkType": "INTERNAL",
      "anchorText": "API Documentation"
    }
  ]
}
```

### GET /api/documents/{documentId}/graph?depth=1

특정 문서를 중심으로 연결된 문서 그래프를 조회합니다.

**Parameters**:
- `depth`: 탐색 깊이 (기본값: 1)
  - `0`: 직접 연결된 문서만
  - `1`: 1단계 이웃 문서
  - `2`: 2단계 이웃 문서

**Response**: 프로젝트 그래프와 동일

### GET /api/documents/{documentId}/links/outgoing

특정 문서에서 나가는 링크 목록을 조회합니다.

**Response**:
```json
[
  {
    "id": "uuid",
    "sourceDocumentId": "uuid",
    "sourceDocumentPath": "docs/start.md",
    "targetDocumentId": "uuid",
    "targetDocumentPath": "docs/api.md",
    "linkText": "./api.md",
    "linkType": "INTERNAL",
    "broken": false,
    "lineNumber": 15,
    "anchorText": "API Documentation"
  }
]
```

### GET /api/documents/{documentId}/links/incoming

특정 문서로 들어오는 링크 목록을 조회합니다 (역참조).

**Response**: outgoing과 동일

### GET /api/documents/{documentId}/impact

특정 문서의 영향 분석을 수행합니다.

**Response**:
```json
{
  "documentId": "uuid",
  "totalImpacted": 10,
  "directImpact": [
    {
      "id": "uuid",
      "path": "docs/tutorial.md",
      "title": "Tutorial",
      "depth": 1,
      "linkType": "INTERNAL",
      "anchorText": "Getting Started"
    }
  ],
  "indirectImpact": [
    {
      "id": "uuid",
      "path": "docs/advanced.md",
      "title": "Advanced Topics",
      "depth": 2,
      "linkType": null,
      "anchorText": null
    }
  ]
}
```

### GET /api/repositories/{repositoryId}/links/broken

레포지토리 내 깨진 링크 목록을 조회합니다.

**Response**: 링크 목록 (outgoing과 동일)

---

## 사용 예시

### 1. 링크 추출 예시

**입력** (README.md):
```markdown
# Getting Started

Check out the [API Documentation](./docs/api.md) for details.

Also see:
- [[Installation]]
- [[Configuration|Config Guide]]
- [External Guide](https://example.com)
```

**추출 결과**:
```
[API Documentation](./docs/api.md) at line 3 → INTERNAL
[[Installation]] at line 6 → WIKI
[[Configuration|Config Guide]] at line 7 → WIKI
[External Guide](https://example.com) at line 8 → EXTERNAL
```

### 2. 경로 해결 예시

**시작 문서**: `docs/guides/start.md`

**링크 텍스트** → **해결된 경로**:
- `./api.md` → `docs/guides/api.md`
- `../README.md` → `docs/README.md`
- `../../LICENSE` → `LICENSE`
- `[[Installation]]` → `docs/guides/Installation.md` 또는 `docs/guides/Installation/index.md`

### 3. 영향 분석 예시

**시나리오**: `docs/api.md`를 수정하려고 함

**영향 받는 문서**:
- 직접 영향 (depth 1):
  - `README.md` → `[API Docs](./docs/api.md)`
  - `docs/tutorial.md` → `[[api|API Reference]]`
- 간접 영향 (depth 2):
  - `docs/advanced.md` → `README.md`를 참조

**결론**: 총 3개 문서가 영향을 받으므로, 변경 시 주의 필요

---

## 동작 흐름

```
┌──────────────┐
│ Git Push     │
└──────┬───────┘
       │
       ▼
┌──────────────────┐
│ GitSyncService   │
│ - syncRepository │
└──────┬───────────┘
       │
       ▼
┌─────────────────────────────────────────────┐
│ processDocument                             │
│ 1. Parse markdown                           │
│ 2. Chunk & Embed                           │
│ 3. Extract links ←─────────────────┐       │
└──────┬──────────────────────────────┘       │
       │                                       │
       ▼                                       │
┌──────────────────────┐               ┌──────────────┐
│ DocumentLinkService  │               │ LinkParser   │
│ - extractAndSaveLinks│───────────────│ - extractLinks│
└──────┬───────────────┘               └──────────────┘
       │
       ▼
┌──────────────────────┐
│ DocumentLink Entity  │
│ - source, target     │
│ - linkType, broken   │
└──────────────────────┘
       │
       ▼
┌──────────────────────┐
│ GraphService         │
│ - getProjectGraph    │
│ - analyzeImpact      │
└──────┬───────────────┘
       │
       ▼
┌──────────────────────┐
│ GraphController      │
│ GET /graph           │
│ GET /impact          │
└──────────────────────┘
```

---

## 향후 개선 사항

### 1. 프론트엔드 그래프 시각화
- **react-flow** 또는 **d3.js**를 사용한 인터랙티브 그래프
- 노드 클릭 시 문서 상세 페이지로 이동
- 링크 타입별 색상 구분

### 2. 링크 자동 수정
- 깨진 링크 감지 시 자동 수정 제안
- 파일 이동 시 관련 링크 자동 업데이트

### 3. PageRank 알고리즘
- 문서 중요도 계산 (들어오는 링크 수 기반)
- 허브 문서(Hub) 및 권위 문서(Authority) 탐지

### 4. 링크 타입 확장
- `@mention` 스타일 링크
- 태그 기반 링크 (`#tag`)

### 5. 실시간 업데이트
- 문서 수정 시 링크 즉시 재분석
- WebSocket으로 그래프 실시간 업데이트

---

## 트러블슈팅

### 링크가 추출되지 않음

1. 링크 형식 확인
   - 마크다운: `[text](url)` (괄호 사이 공백 없어야 함)
   - Wiki: `[[page]]` (대괄호 2개)

2. 지원하지 않는 형식
   - HTML 링크: `<a href="...">` (지원 안 함)
   - 자동 링크: `<https://example.com>` (지원 안 함)

### 깨진 링크로 표시됨

1. 경로 확인
   - 상대 경로가 올바른지 확인
   - 대소문자 구분 (Linux/Mac)

2. 파일 확장자
   - `.md` 확장자가 명시되어 있는지 확인
   - Wiki 링크는 자동으로 `.md` 추가

### 성능 이슈

1. 대규모 프로젝트에서 그래프 조회가 느림
   - `depth` 파라미터를 줄여서 탐색 범위 축소
   - 레포지토리 단위로 분할 조회

2. 링크 추출 시간이 오래 걸림
   - 정규표현식 최적화 (미래 개선)
   - 배치 처리로 변경

---

## 파일 변경 사항

### 생성된 파일
- `backend/src/main/java/com/docst/domain/DocumentLink.java`
- `backend/src/main/java/com/docst/repository/DocumentLinkRepository.java`
- `backend/src/main/java/com/docst/git/LinkParser.java`
- `backend/src/main/java/com/docst/service/DocumentLinkService.java`
- `backend/src/main/java/com/docst/service/GraphService.java`
- `backend/src/main/java/com/docst/api/GraphController.java`
- `backend/src/main/resources/db/migration/V7__add_document_link.sql`

### 수정된 파일
- `backend/src/main/java/com/docst/service/GitSyncService.java`: 링크 추출 통합

---

## 결론

Phase 3-D에서는 문서 간 링크를 분석하여 그래프로 시각화하고 영향 분석 기능을 구현했습니다.

**주요 성과**:
- ✅ 마크다운 및 Wiki 스타일 링크 자동 추출
- ✅ 문서 관계 그래프 생성 (프로젝트/레포지토리/문서 단위)
- ✅ BFS 기반 연결된 문서 탐색 (depth 파라미터)
- ✅ 영향 분석으로 문서 변경 시 파급 효과 파악
- ✅ 깨진 링크 자동 탐지

**다음 단계**:
- Phase 3-E: 권한 체크 AOP 구현
- 프론트엔드 그래프 시각화 구현
