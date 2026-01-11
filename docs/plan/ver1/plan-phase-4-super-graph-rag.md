# Phase 4: Graph RAG & Hybrid RAG 구현 계획

> **작성일**: 2025-12-29
> **기반 계획서**: `docs/plan/phase-4-flexible-rag-architecture.md`
> **목표**: Neo4j Graph RAG 및 Hybrid RAG를 섹션 6(동적 전략 선택)까지 구현

---

## 목표

Phase 4 계획서에 따라 Neo4j Graph RAG 및 Hybrid RAG를 섹션 6(동적 전략 선택)까지 구현

## 구현 범위

- ✅ 섹션 2-3: 아키텍처 및 인터페이스 (RagMode, RagSearchStrategy)
- ✅ 섹션 4: Mode 2 - Neo4j Graph RAG
- ✅ 섹션 5: Mode 3 - Hybrid RAG (PgVector + Neo4j)
- ✅ 섹션 6: 동적 전략 선택 (QueryRouter)

---

## 단계별 구현 순서

### Phase 4-A: 기반 구조 (3-4일)

#### 1. 핵심 인터페이스 및 Enum 정의

**RagMode.java** (신규)
```java
package com.docst.rag;

public enum RagMode {
    PGVECTOR,    // Mode 1: 벡터 검색만
    NEO4J,       // Mode 2: 그래프 검색만
    HYBRID       // Mode 3: 하이브리드
}
```

**RagSearchStrategy.java** (신규)
```java
package com.docst.rag;

import com.docst.domain.DocumentVersion;
import com.docst.service.SearchService.SearchResult;

import java.util.List;
import java.util.UUID;

public interface RagSearchStrategy {
    /**
     * 검색 실행
     */
    List<SearchResult> search(UUID projectId, String query, int topK);

    /**
     * 문서 인덱싱
     */
    void indexDocument(DocumentVersion documentVersion);

    /**
     * 지원하는 RAG 모드
     */
    RagMode getSupportedMode();
}
```

#### 2. 데이터베이스 마이그레이션

**V9__add_rag_config.sql** (신규)
```sql
-- Project에 RAG 모드 컬럼 추가 (nullable - UI에서 선택)
ALTER TABLE dm_project
ADD COLUMN rag_mode VARCHAR(20)
    CHECK (rag_mode IN ('pgvector', 'neo4j', 'hybrid'));

-- Project에 RAG 설정 컬럼 추가 (JSONB, nullable)
ALTER TABLE dm_project
ADD COLUMN rag_config JSONB;

-- 엔티티 테이블 생성
CREATE TABLE dm_entity (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES dm_project(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    type TEXT NOT NULL,  -- Concept, API, Component, Technology
    description TEXT,
    source_chunk_id UUID REFERENCES dm_doc_chunk(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (project_id, name, type)
);

CREATE INDEX idx_entity_project ON dm_entity(project_id);
CREATE INDEX idx_entity_name ON dm_entity(name);

-- 엔티티 관계 테이블 생성
CREATE TABLE dm_entity_relation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_entity_id UUID NOT NULL REFERENCES dm_entity(id) ON DELETE CASCADE,
    target_entity_id UUID NOT NULL REFERENCES dm_entity(id) ON DELETE CASCADE,
    relation_type TEXT NOT NULL,  -- RELATED_TO, DEPENDS_ON, USES, PART_OF
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_entity_rel_source ON dm_entity_relation(source_entity_id);
CREATE INDEX idx_entity_rel_target ON dm_entity_relation(target_entity_id);
```

#### 3. Project 엔티티 수정

**Project.java** (수정)
- `ragMode` 필드 추가 (RagMode enum, **nullable** - UI에서 설정)
- `ragConfig` 필드 추가 (String, JSONB 매핑, nullable)

```java
/**
 * 프로젝트별 기본 RAG 모드.
 * null이면 전역 기본값(auto) 사용.
 */
@Setter
@Enumerated(EnumType.STRING)
@Column(name = "rag_mode")
private RagMode ragMode;  // nullable

/**
 * RAG 모드별 상세 설정 (선택사항).
 */
@Setter
@Column(name = "rag_config", columnDefinition = "jsonb")
private String ragConfig;
```

---

### Phase 4-B: Mode 1 리팩토링 (2-3일)

#### 기존 SemanticSearchService를 전략 패턴으로 래핑

**PgVectorSearchStrategy.java** (신규)
```java
package com.docst.rag.pgvector;

import com.docst.domain.DocumentVersion;
import com.docst.rag.RagMode;
import com.docst.rag.RagSearchStrategy;
import com.docst.service.SearchService.SearchResult;
import com.docst.service.SemanticSearchService;
import com.docst.embedding.DocstEmbeddingService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "docst.rag.pgvector.enabled", havingValue = "true", matchIfMissing = true)
public class PgVectorSearchStrategy implements RagSearchStrategy {

    private final SemanticSearchService semanticSearchService;
    private final DocstEmbeddingService embeddingService;

    @Override
    public List<SearchResult> search(UUID projectId, String query, int topK) {
        return semanticSearchService.searchSemantic(projectId, query, topK);
    }

    @Override
    public void indexDocument(DocumentVersion documentVersion) {
        embeddingService.embedDocumentVersion(documentVersion);
    }

    @Override
    public RagMode getSupportedMode() {
        return RagMode.PGVECTOR;
    }
}
```

#### SearchController 수정

**SearchController.java** (수정)
- Map<RagMode, RagSearchStrategy> 자동 주입
- mode 파라미터에 "graph", "auto" 추가
- QueryRouter 활용 (auto 모드 시)

```java
@RestController
@RequestMapping("/api/projects/{projectId}/search")
@RequiredArgsConstructor
public class SearchController {

    private final Map<RagMode, RagSearchStrategy> strategyMap;
    private final QueryRouter queryRouter;

    @GetMapping
    public ResponseEntity<List<SearchResultResponse>> search(
            @PathVariable UUID projectId,
            @RequestParam(name = "q") String query,
            @RequestParam(required = false, defaultValue = "keyword") String mode,
            @RequestParam(required = false, defaultValue = "10") Integer topK
    ) {
        RagMode ragMode = determineRagMode(mode, query);
        RagSearchStrategy strategy = strategyMap.get(ragMode);

        List<SearchResult> results = strategy.search(projectId, query, topK);

        // ... 응답 변환
    }

    private RagMode determineRagMode(String modeParam, String query) {
        return switch (modeParam.toLowerCase()) {
            case "semantic" -> RagMode.PGVECTOR;
            case "graph" -> RagMode.NEO4J;
            case "hybrid" -> RagMode.HYBRID;
            case "auto" -> queryRouter.analyzeAndRoute(query);
            default -> RagMode.PGVECTOR;  // keyword도 pgvector로
        };
    }
}
```

---

### Phase 4-C: Mode 2 - Neo4j 통합 (7-10일)

#### 1. 의존성 추가

**build.gradle.kts** (수정)
```kotlin
dependencies {
    // 기존 의존성...

    // Spring Data Neo4j
    implementation("org.springframework.boot:spring-boot-starter-data-neo4j")
    implementation("org.neo4j.driver:neo4j-java-driver")
}
```

#### 2. Docker Compose 변경

**docker-compose.yml** (수정)
```yaml
services:
  postgres:
    # ... 기존 설정

  neo4j:
    image: neo4j:5.15-community
    container_name: docst-neo4j
    ports:
      - "7474:7474"  # HTTP Browser
      - "7687:7687"  # Bolt
    environment:
      NEO4J_AUTH: neo4j/${NEO4J_PASSWORD:-password}
      NEO4J_PLUGINS: '["apoc"]'
      NEO4J_dbms_memory_heap_max__size: 1G
    volumes:
      - neo4j_data:/data
      - neo4j_logs:/logs
    healthcheck:
      test: ["CMD-SHELL", "cypher-shell -u neo4j -p ${NEO4J_PASSWORD:-password} 'RETURN 1'"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s

  backend:
    # ... 기존 설정
    environment:
      # ... 기존 환경 변수
      DOCST_RAG_MODE: ${DOCST_RAG_MODE:-pgvector}
      NEO4J_URI: bolt://neo4j:7687
      NEO4J_USERNAME: neo4j
      NEO4J_PASSWORD: ${NEO4J_PASSWORD:-password}
    depends_on:
      postgres:
        condition: service_healthy
      neo4j:
        condition: service_healthy

volumes:
  postgres_data:
  neo4j_data:
  neo4j_logs:
```

#### 3. 설정 파일 수정

**application.yml** (수정)
```yaml
docst:
  rag:
    mode: ${DOCST_RAG_MODE:pgvector}

    # PgVector 설정
    pgvector:
      enabled: ${DOCST_RAG_PGVECTOR_ENABLED:true}

    # Neo4j 설정
    neo4j:
      enabled: ${DOCST_RAG_NEO4J_ENABLED:false}
      uri: ${NEO4J_URI:bolt://localhost:7687}
      username: ${NEO4J_USERNAME:neo4j}
      password: ${NEO4J_PASSWORD:password}
      entity-extraction:
        enabled: true
        model: gpt-4o-mini
      search:
        max-hop: 2
        text-to-cypher: true

    # Hybrid 설정
    hybrid:
      fusion-strategy: rrf  # rrf, weighted_sum
      vector-weight: 0.6
      graph-weight: 0.4
      rrf-k: 60

spring:
  neo4j:
    uri: ${NEO4J_URI:bolt://localhost:7687}
    authentication:
      username: ${NEO4J_USERNAME:neo4j}
      password: ${NEO4J_PASSWORD:password}
```

**.env.example** (수정)
```bash
# ... 기존 설정

# Neo4j (Graph RAG)
NEO4J_URI=bolt://localhost:7687
NEO4J_USERNAME=neo4j
NEO4J_PASSWORD=password
DOCST_RAG_NEO4J_ENABLED=false

# RAG Mode
DOCST_RAG_MODE=pgvector  # pgvector, neo4j, hybrid

# Hybrid 설정
DOCST_RAG_HYBRID_FUSION=rrf
DOCST_RAG_HYBRID_VECTOR_WEIGHT=0.6
DOCST_RAG_HYBRID_GRAPH_WEIGHT=0.4
```

#### 4. Neo4j 설정 클래스

**Neo4jConfig.java** (신규)
```java
package com.docst.rag.neo4j;

import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.annotation.PostConstruct;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "docst.rag.neo4j.enabled", havingValue = "true")
public class Neo4jConfig {

    @Autowired
    private Driver driver;

    @PostConstruct
    public void initializeSchema() {
        try (Session session = driver.session()) {
            log.info("Initializing Neo4j schema...");

            // 벡터 인덱스 생성
            session.run("""
                CREATE VECTOR INDEX chunk_embedding IF NOT EXISTS
                FOR (c:Chunk)
                ON (c.embedding)
                OPTIONS {
                    indexConfig: {
                        `vector.dimensions`: 1536,
                        `vector.similarity_function`: 'cosine'
                    }
                }
                """);

            // 전문 인덱스 생성
            session.run("""
                CREATE FULLTEXT INDEX chunk_fulltext IF NOT EXISTS
                FOR (c:Chunk)
                ON EACH [c.text, c.summary]
                """);

            // 엔티티 인덱스
            session.run("""
                CREATE INDEX entity_name IF NOT EXISTS
                FOR (e:Entity) ON (e.name)
                """);

            log.info("Neo4j schema initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize Neo4j schema", e);
        }
    }
}
```

#### 5. 엔티티 추출 서비스

**EntityExtractionService.java** (신규)
```java
package com.docst.rag.neo4j;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "docst.rag.neo4j.entity-extraction.enabled", havingValue = "true")
public class EntityExtractionService {

    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;

    public ExtractionResult extractEntities(String chunkText) {
        String prompt = """
            다음 텍스트에서 엔티티와 관계를 추출하세요:

            텍스트: %s

            추출할 엔티티 유형:
            - Concept: 개념, 용어
            - API: API 엔드포인트, 함수
            - Component: 시스템 컴포넌트
            - Technology: 기술, 프레임워크

            추출할 관계 유형:
            - RELATED_TO: 관련됨
            - DEPENDS_ON: 의존함
            - USES: 사용함
            - PART_OF: 일부임

            JSON 형식으로 반환:
            {
              "entities": [
                {"name": "Spring Boot", "type": "Technology", "description": "Java 프레임워크"}
              ],
              "relationships": [
                {"source": "Spring Boot", "target": "Spring Framework", "type": "DEPENDS_ON", "description": "의존 관계"}
              ]
            }
            """.formatted(chunkText);

        String response = chatClientBuilder.build()
            .prompt()
            .user(prompt)
            .call()
            .content();

        return parseJson(response);
    }

    private ExtractionResult parseJson(String jsonResponse) {
        try {
            // JSON 파싱 로직
            return objectMapper.readValue(jsonResponse, ExtractionResult.class);
        } catch (Exception e) {
            log.error("Failed to parse extraction result", e);
            return new ExtractionResult(List.of(), List.of());
        }
    }

    public record ExtractionResult(
        List<ExtractedEntity> entities,
        List<ExtractedRelationship> relationships
    ) {}

    public record ExtractedEntity(
        String name,
        String type,
        String description
    ) {}

    public record ExtractedRelationship(
        String source,
        String target,
        String type,
        String description
    ) {}
}
```

#### 6. Text-to-Cypher 서비스

**Text2CypherService.java** (신규)
```java
package com.docst.rag.neo4j;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "docst.rag.neo4j.search.text-to-cypher", havingValue = "true")
public class Text2CypherService {

    private final ChatClient.Builder chatClientBuilder;
    private final Driver neo4jDriver;

    public List<Map<String, Object>> textToCypher(String naturalQuery) {
        String cypher = generateCypher(naturalQuery);

        try (var session = neo4jDriver.session()) {
            Result result = session.run(cypher);
            return result.list(record -> record.asMap());
        } catch (Exception e) {
            log.error("Cypher query failed: {}", cypher, e);
            // Self-healing: 오류 시 재생성
            return retryWithError(naturalQuery, cypher, e.getMessage());
        }
    }

    private String generateCypher(String naturalQuery) {
        return chatClientBuilder.build()
            .prompt()
            .system("""
                You are a Neo4j Cypher expert.
                Generate a Cypher query for the following schema:

                Nodes:
                - Chunk (id, text, summary, projectId)
                - Entity (name, type, description)
                - Concept, API, Component, Technology (subtypes of Entity)

                Relationships:
                - (Chunk)-[:MENTIONS]->(Entity)
                - (Entity)-[:RELATED_TO]->(Entity)
                - (Entity)-[:DEPENDS_ON]->(Entity)
                - (Entity)-[:USES]->(Entity)
                - (Entity)-[:PART_OF]->(Entity)

                Return ONLY the Cypher query without explanation.
                """)
            .user(naturalQuery)
            .call()
            .content()
            .trim();
    }

    private List<Map<String, Object>> retryWithError(String query, String failedCypher, String error) {
        String improvedCypher = chatClientBuilder.build()
            .prompt()
            .system("Fix the following Cypher query error:")
            .user("Query: %s\nFailed Cypher: %s\nError: %s".formatted(query, failedCypher, error))
            .call()
            .content()
            .trim();

        try (var session = neo4jDriver.session()) {
            return session.run(improvedCypher).list(r -> r.asMap());
        } catch (Exception e) {
            log.error("Retry failed", e);
            return List.of();
        }
    }
}
```

#### 7. Neo4j 검색 전략

**Neo4jSearchStrategy.java** (신규)
```java
package com.docst.rag.neo4j;

import com.docst.domain.DocumentVersion;
import com.docst.rag.RagMode;
import com.docst.rag.RagSearchStrategy;
import com.docst.service.SearchService.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "docst.rag.neo4j.enabled", havingValue = "true")
public class Neo4jSearchStrategy implements RagSearchStrategy {

    private final Driver neo4jDriver;
    private final EntityExtractionService entityExtractionService;

    @Override
    public List<SearchResult> search(UUID projectId, String query, int topK) {
        try (Session session = neo4jDriver.session()) {
            String cypher = """
                CALL db.index.fulltext.queryNodes('chunk_fulltext', $query)
                YIELD node, score
                WHERE node.projectId = $projectId
                OPTIONAL MATCH (node)-[:MENTIONS]->(e:Entity)
                OPTIONAL MATCH (e)-[:RELATED_TO]-(related:Entity)
                RETURN node, score,
                       collect(DISTINCT e.name) AS entities,
                       collect(DISTINCT related.name) AS relatedEntities
                ORDER BY score DESC
                LIMIT $topK
                """;

            var result = session.run(cypher, Map.of(
                "query", query,
                "projectId", projectId.toString(),
                "topK", topK
            ));

            return parseResults(result.list());
        }
    }

    @Override
    public void indexDocument(DocumentVersion documentVersion) {
        // 청크별 처리
        documentVersion.getChunks().forEach(chunk -> {
            // 1. 엔티티 추출
            var extraction = entityExtractionService.extractEntities(chunk.getContent());

            try (Session session = neo4jDriver.session()) {
                // 2. Chunk 노드 생성
                session.run("""
                    MERGE (c:Chunk {id: $id})
                    SET c.text = $text,
                        c.projectId = $projectId,
                        c.headingPath = $headingPath
                    """, Map.of(
                    "id", chunk.getId().toString(),
                    "text", chunk.getContent(),
                    "projectId", documentVersion.getDocument().getRepository().getProject().getId().toString(),
                    "headingPath", chunk.getHeadingPath()
                ));

                // 3. 엔티티 및 관계 생성
                extraction.entities().forEach(entity -> {
                    session.run("""
                        MERGE (e:Entity {name: $name})
                        SET e.type = $type,
                            e.description = $description
                        WITH e
                        MATCH (c:Chunk {id: $chunkId})
                        MERGE (c)-[:MENTIONS]->(e)
                        """, Map.of(
                        "name", entity.name(),
                        "type", entity.type(),
                        "description", entity.description(),
                        "chunkId", chunk.getId().toString()
                    ));
                });

                extraction.relationships().forEach(rel -> {
                    session.run("""
                        MATCH (source:Entity {name: $source})
                        MATCH (target:Entity {name: $target})
                        MERGE (source)-[r:%s]->(target)
                        SET r.description = $description
                        """.formatted(rel.type()), Map.of(
                        "source", rel.source(),
                        "target", rel.target(),
                        "description", rel.description()
                    ));
                });
            }
        });
    }

    @Override
    public RagMode getSupportedMode() {
        return RagMode.NEO4J;
    }

    private List<SearchResult> parseResults(List<org.neo4j.driver.Record> records) {
        // Neo4j 결과를 SearchResult로 변환
        // ... 구현
        return List.of();
    }
}
```

#### 8. 엔티티 도메인 모델

**DocEntity.java** (신규)
```java
package com.docst.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dm_entity")
@Getter
@NoArgsConstructor
public class DocEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String type;  // Concept, API, Component, Technology

    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_chunk_id")
    private DocChunk sourceChunk;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
```

**DocEntityRelation.java** (신규)
```java
package com.docst.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dm_entity_relation")
@Getter
@NoArgsConstructor
public class DocEntityRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_entity_id", nullable = false)
    private DocEntity sourceEntity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_entity_id", nullable = false)
    private DocEntity targetEntity;

    @Column(name = "relation_type", nullable = false)
    private String relationType;  // RELATED_TO, DEPENDS_ON, USES, PART_OF

    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
```

---

### Phase 4-D: Mode 3 - Hybrid 전략 (5-7일)

#### 1. 융합 전략 인터페이스

**FusionStrategy.java** (신규)
```java
package com.docst.rag.hybrid;

import com.docst.service.SearchService.SearchResult;

import java.util.List;

public interface FusionStrategy {
    /**
     * 벡터 검색 결과와 그래프 검색 결과를 융합
     */
    List<SearchResult> fuse(
        List<SearchResult> vectorResults,
        List<SearchResult> graphResults
    );
}
```

#### 2. RRF 융합 전략

**RrfFusionStrategy.java** (신규)
```java
package com.docst.rag.hybrid;

import com.docst.service.SearchService.SearchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "docst.rag.hybrid.fusion-strategy", havingValue = "rrf", matchIfMissing = true)
public class RrfFusionStrategy implements FusionStrategy {

    private static final int RRF_K = 60;

    @Override
    public List<SearchResult> fuse(List<SearchResult> vectorResults, List<SearchResult> graphResults) {
        Map<UUID, Double> rrfScores = new HashMap<>();

        // 벡터 검색 결과에 RRF 점수 부여
        for (int i = 0; i < vectorResults.size(); i++) {
            UUID id = getResultId(vectorResults.get(i));
            double rrfScore = 1.0 / (RRF_K + i + 1);
            rrfScores.merge(id, rrfScore, Double::sum);
        }

        // 그래프 검색 결과에 RRF 점수 부여
        for (int i = 0; i < graphResults.size(); i++) {
            UUID id = getResultId(graphResults.get(i));
            double rrfScore = 1.0 / (RRF_K + i + 1);
            rrfScores.merge(id, rrfScore, Double::sum);
        }

        // 점수 기준 정렬
        return rrfScores.entrySet().stream()
            .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
            .map(entry -> buildResult(entry.getKey(), entry.getValue(), vectorResults, graphResults))
            .collect(Collectors.toList());
    }

    private UUID getResultId(SearchResult result) {
        return result.chunkId() != null ? result.chunkId() : result.documentId();
    }

    private SearchResult buildResult(UUID id, Double score,
                                     List<SearchResult> vectorResults,
                                     List<SearchResult> graphResults) {
        // 원본 결과 찾기
        SearchResult original = vectorResults.stream()
            .filter(r -> getResultId(r).equals(id))
            .findFirst()
            .orElseGet(() -> graphResults.stream()
                .filter(r -> getResultId(r).equals(id))
                .findFirst()
                .orElseThrow());

        // RRF 점수로 업데이트
        return new SearchResult(
            original.documentId(),
            original.repositoryId(),
            original.path(),
            original.commitSha(),
            original.chunkId(),
            original.headingPath(),
            score,  // RRF 융합 점수
            original.snippet(),
            original.highlightedSnippet()
        );
    }
}
```

#### 3. 가중 합산 전략

**WeightedSumFusionStrategy.java** (신규)
```java
package com.docst.rag.hybrid;

import com.docst.service.SearchService.SearchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "docst.rag.hybrid.fusion-strategy", havingValue = "weighted_sum")
public class WeightedSumFusionStrategy implements FusionStrategy {

    @Value("${docst.rag.hybrid.vector-weight:0.6}")
    private double vectorWeight;

    @Value("${docst.rag.hybrid.graph-weight:0.4}")
    private double graphWeight;

    @Override
    public List<SearchResult> fuse(List<SearchResult> vectorResults, List<SearchResult> graphResults) {
        Map<UUID, Double> fusedScores = new HashMap<>();

        // 벡터 점수 정규화 및 가중
        double maxVectorScore = vectorResults.stream()
            .mapToDouble(SearchResult::score)
            .max().orElse(1.0);

        for (SearchResult result : vectorResults) {
            UUID id = getResultId(result);
            double normalizedScore = result.score() / maxVectorScore;
            fusedScores.merge(id, normalizedScore * vectorWeight, Double::sum);
        }

        // 그래프 점수 정규화 및 가중
        double maxGraphScore = graphResults.stream()
            .mapToDouble(SearchResult::score)
            .max().orElse(1.0);

        for (SearchResult result : graphResults) {
            UUID id = getResultId(result);
            double normalizedScore = result.score() / maxGraphScore;
            fusedScores.merge(id, normalizedScore * graphWeight, Double::sum);
        }

        return fusedScores.entrySet().stream()
            .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
            .map(entry -> buildResult(entry.getKey(), entry.getValue(), vectorResults, graphResults))
            .collect(Collectors.toList());
    }

    // ... getResultId(), buildResult() 구현 (RRF와 동일)
}
```

#### 4. 하이브리드 검색 전략

**HybridSearchStrategy.java** (신규)
```java
package com.docst.rag.hybrid;

import com.docst.domain.DocumentVersion;
import com.docst.rag.RagMode;
import com.docst.rag.RagSearchStrategy;
import com.docst.rag.pgvector.PgVectorSearchStrategy;
import com.docst.rag.neo4j.Neo4jSearchStrategy;
import com.docst.service.SearchService.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "docst.rag.mode", havingValue = "hybrid")
public class HybridSearchStrategy implements RagSearchStrategy {

    private final PgVectorSearchStrategy pgVectorStrategy;
    private final Neo4jSearchStrategy neo4jStrategy;
    private final FusionStrategy fusionStrategy;

    @Override
    public List<SearchResult> search(UUID projectId, String query, int topK) {
        // 병렬 검색 실행
        CompletableFuture<List<SearchResult>> vectorFuture =
            CompletableFuture.supplyAsync(() ->
                pgVectorStrategy.search(projectId, query, topK * 2));

        CompletableFuture<List<SearchResult>> graphFuture =
            CompletableFuture.supplyAsync(() ->
                neo4jStrategy.search(projectId, query, topK * 2));

        try {
            // 결과 대기 (타임아웃: 5초)
            List<SearchResult> vectorResults = vectorFuture.get(5, TimeUnit.SECONDS);
            List<SearchResult> graphResults = graphFuture.get(5, TimeUnit.SECONDS);

            // 결과 융합
            List<SearchResult> fusedResults = fusionStrategy.fuse(vectorResults, graphResults);

            // Top-K 반환
            return fusedResults.stream()
                .limit(topK)
                .toList();

        } catch (Exception e) {
            log.error("Hybrid search failed", e);
            // 폴백: 벡터 검색만 반환
            try {
                return vectorFuture.get(1, TimeUnit.SECONDS).stream().limit(topK).toList();
            } catch (Exception ex) {
                return List.of();
            }
        }
    }

    @Override
    public void indexDocument(DocumentVersion documentVersion) {
        // 양쪽 저장소에 인덱싱
        pgVectorStrategy.indexDocument(documentVersion);
        neo4jStrategy.indexDocument(documentVersion);
    }

    @Override
    public RagMode getSupportedMode() {
        return RagMode.HYBRID;
    }
}
```

---

### Phase 4-E: 동적 전략 선택 (3-4일)

#### 1. QueryRouter 구현

**QueryRouter.java** (신규)
```java
package com.docst.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueryRouter {

    private final ChatClient.Builder chatClientBuilder;

    /**
     * 질문 분석 후 최적 검색 전략 추천
     */
    public RagMode analyzeAndRoute(String query) {
        String analysis = chatClientBuilder.build()
            .prompt()
            .system("""
                질문을 분석하고 최적의 검색 전략을 선택하세요:

                - PGVECTOR: 시맨틱 유사도 검색이 적합 (개념 설명, 유사한 내용 찾기)
                - NEO4J: 관계 기반 검색이 적합 (X의 Y는?, A와 B의 관계는?, 의존성 분석)
                - HYBRID: 둘 다 필요 (복잡한 질문, 여러 정보 종합, 정확도 중요)

                응답 형식: PGVECTOR | NEO4J | HYBRID (한 단어만)
                """)
            .user(query)
            .call()
            .content()
            .trim();

        RagMode mode = switch (analysis.toUpperCase()) {
            case "PGVECTOR" -> RagMode.PGVECTOR;
            case "NEO4J" -> RagMode.NEO4J;
            case "HYBRID" -> RagMode.HYBRID;
            default -> {
                log.warn("Unknown mode '{}', defaulting to HYBRID", analysis);
                yield RagMode.HYBRID;
            }
        };

        log.info("Query '{}' routed to {}", query, mode);
        return mode;
    }
}
```

#### 2. SearchController에 auto 모드 추가

**SearchController.java** (수정)
```java
@Operation(
    summary = "문서 검색",
    description = "프로젝트 내 문서를 검색합니다. 검색 모드: keyword, semantic, graph, hybrid, auto"
)
@GetMapping
public ResponseEntity<List<SearchResultResponse>> search(
        @PathVariable UUID projectId,
        @RequestParam(name = "q") String query,
        @RequestParam(required = false, defaultValue = "keyword") String mode,
        @RequestParam(required = false, defaultValue = "10") Integer topK
) {
    RagMode ragMode = determineRagMode(mode, query);

    RagSearchStrategy strategy = strategyMap.get(ragMode);
    if (strategy == null) {
        // 폴백: PgVector
        strategy = strategyMap.get(RagMode.PGVECTOR);
    }

    List<SearchResult> results = strategy.search(projectId, query, topK);

    // ... 응답 변환
}

private RagMode determineRagMode(String modeParam, String query) {
    return switch (modeParam.toLowerCase()) {
        case "keyword", "semantic" -> RagMode.PGVECTOR;
        case "graph" -> RagMode.NEO4J;
        case "hybrid" -> RagMode.HYBRID;
        case "auto" -> queryRouter.analyzeAndRoute(query);
        default -> RagMode.PGVECTOR;
    };
}
```

---

## 핵심 파일 목록

### 신규 파일 (17개)

```
backend/src/main/java/com/docst/
├── rag/
│   ├── RagMode.java                              # Enum
│   ├── RagSearchStrategy.java                    # 인터페이스
│   ├── QueryRouter.java                          # 동적 라우팅
│   ├── pgvector/
│   │   └── PgVectorSearchStrategy.java           # Mode 1 전략
│   ├── neo4j/
│   │   ├── Neo4jConfig.java                      # Neo4j 설정
│   │   ├── Neo4jSearchStrategy.java              # Mode 2 전략
│   │   ├── EntityExtractionService.java          # 엔티티 추출
│   │   └── Text2CypherService.java               # Text-to-Cypher
│   └── hybrid/
│       ├── FusionStrategy.java                   # 융합 인터페이스
│       ├── RrfFusionStrategy.java                # RRF 구현
│       ├── WeightedSumFusionStrategy.java        # 가중 합산
│       └── HybridSearchStrategy.java             # Mode 3 전략
├── domain/
│   ├── DocEntity.java                            # 엔티티 엔티티
│   └── DocEntityRelation.java                    # 관계 엔티티
└── repository/
    ├── DocEntityRepository.java                  # 엔티티 레포
    └── DocEntityRelationRepository.java          # 관계 레포

backend/src/main/resources/
└── db/migration/
    └── V9__add_rag_config.sql                    # 마이그레이션
```

### 수정 파일 (5개)

```
backend/build.gradle.kts                          # Neo4j 의존성
backend/src/main/resources/application.yml        # Neo4j 설정
backend/src/main/java/com/docst/domain/Project.java  # ragMode 추가
backend/src/main/java/com/docst/api/SearchController.java  # 전략 패턴
docker-compose.yml                                # Neo4j 서비스
```

---

## 주요 변경사항

### 1. 전략 패턴 도입

기존 SearchController의 switch 문 → 전략 패턴으로 변경

```java
// Before
List<SearchResult> results = switch (mode.toLowerCase()) {
    case "semantic" -> semanticSearchService.searchSemantic(...);
    case "hybrid" -> hybridSearchService.hybridSearch(...);
    default -> searchService.searchByKeyword(...);
};

// After
RagMode ragMode = determineRagMode(mode, query);
RagSearchStrategy strategy = strategyMap.get(ragMode);
List<SearchResult> results = strategy.search(projectId, query, topK);
```

### 2. 기존 HybridSearchService 보존

현재 HybridSearchService (키워드 + 벡터)는 유지하고, 새로운 HybridSearchStrategy (벡터 + 그래프)와 분리

### 3. **UI 중심 동적 모드 선택** (설계 변경)

**기존 계획**: `.env`의 `DOCST_RAG_MODE`로 애플리케이션 전역 모드 설정
**변경 계획**: 화면(UI)에서 검색마다 동적으로 모드 선택

#### RAG 모드 선택 우선순위

```
1. 검색 요청의 mode 파라미터 (최우선)
   → GET /api/projects/{id}/search?q=query&mode=graph

2. 프로젝트별 기본 모드 (Project.ragMode)
   → 프로젝트 설정 화면에서 설정 가능

3. 전역 기본값 (application.yml의 docst.rag.default-mode)
   → 기본값: "auto" (QueryRouter가 자동 선택)
```

#### SearchController 로직 수정

```java
private RagMode determineRagMode(UUID projectId, String modeParam, String query) {
    // 1. URL 파라미터 우선
    if (modeParam != null && !modeParam.equals("default")) {
        return parseMode(modeParam, query);
    }

    // 2. 프로젝트 설정 확인
    Project project = projectRepository.findById(projectId)
        .orElseThrow(() -> new NotFoundException("Project not found"));

    if (project.getRagMode() != null) {
        return project.getRagMode();
    }

    // 3. 전역 기본값 (auto → QueryRouter)
    return queryRouter.analyzeAndRoute(query);
}
```

#### 프론트엔드 UI 구성

**검색 화면**:
```typescript
// 검색 바에 모드 선택 드롭다운 추가
<Select value={searchMode} onChange={setSearchMode}>
  <option value="auto">자동 선택 (추천)</option>
  <option value="keyword">키워드 검색</option>
  <option value="semantic">의미 검색</option>
  <option value="graph">그래프 검색</option>
  <option value="hybrid">하이브리드 검색</option>
</Select>
```

**프로젝트 설정 화면**:
```typescript
// 프로젝트별 기본 RAG 모드 설정
<RadioGroup value={project.ragMode} onChange={updateRagMode}>
  <Radio value="auto">자동 선택 (기본값)</Radio>
  <Radio value="pgvector">벡터 검색</Radio>
  <Radio value="neo4j">그래프 검색</Radio>
  <Radio value="hybrid">하이브리드 검색</Radio>
</RadioGroup>
```

---

## 설정 관리

### application.yml 주요 추가 사항

```yaml
docst:
  rag:
    # 전역 기본 모드 (프로젝트 설정이 없을 때)
    default-mode: auto  # auto | pgvector | neo4j | hybrid

    # 각 전략 활성화 여부
    pgvector:
      enabled: true
    neo4j:
      enabled: ${DOCST_RAG_NEO4J_ENABLED:false}
      entity-extraction-model: gpt-4o-mini
      max-hop: 2
    hybrid:
      enabled: true
      fusion-strategy: rrf  # rrf | weighted_sum
      vector-weight: 0.6
      graph-weight: 0.4
```

### 환경 변수 (.env)

```bash
# Neo4j 연결 설정만 필요 (모드는 UI에서 선택)
NEO4J_URI=bolt://localhost:7687
NEO4J_USERNAME=neo4j
NEO4J_PASSWORD=password

# Neo4j 전략 활성화 여부
DOCST_RAG_NEO4J_ENABLED=false

# 전역 기본 모드 (선택사항, 기본값: auto)
DOCST_RAG_DEFAULT_MODE=auto
```

### Project 엔티티 활용

```java
@Entity
public class Project {
    // ... 기존 필드

    /**
     * 프로젝트별 기본 RAG 모드.
     * null이면 전역 기본값 사용.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "rag_mode")
    private RagMode ragMode;  // nullable

    /**
     * RAG 모드별 상세 설정 (선택사항).
     * 예: {"neo4j": {"maxHop": 3}, "hybrid": {"vectorWeight": 0.7}}
     */
    @Column(name = "rag_config", columnDefinition = "jsonb")
    private String ragConfig;
}
```

---

## 위험 요소 및 해결 방안

### 1. Neo4j 연결 실패

**위험**: Neo4j가 없을 때 애플리케이션 실패
**해결**:
- `@ConditionalOnProperty(name = "docst.rag.neo4j.enabled")`로 비활성화 가능
- Health check 및 Retry 로직
- Graceful degradation (PgVector 폴백)

### 2. LLM API 비용

**위험**: 엔티티 추출 시 OpenAI API 비용 증가
**해결**:
- gpt-4o-mini 사용 (저비용)
- 배치 처리로 효율성 증가
- 캐싱 전략 (동일 청크 재처리 방지)

### 3. 병렬 검색 타임아웃

**위험**: Neo4j 응답 지연 시 전체 검색 느려짐
**해결**:
- CompletableFuture 타임아웃 5초
- 부분 결과 반환 (벡터만 성공 시)
- 비동기 인덱싱 (검색과 분리)

### 4. 기존 기능 호환성

**위험**: SearchController 변경으로 기존 API 동작 변경
**해결**:
- 기본 모드 유지 (mode="keyword")
- 기존 테스트 케이스 통과 확인
- API 문서 명확히 업데이트

---

## 테스트 전략

### 1. 단위 테스트

- RrfFusionStrategy 점수 계산 검증
- QueryRouter 라우팅 로직 검증

### 2. 통합 테스트

- Neo4j Testcontainers 사용
- 엔티티 추출 → 저장 → 검색 E2E

### 3. 성능 테스트

- 병렬 검색 응답 시간 < 2초
- RRF 융합 오버헤드 측정

---

## 구현 순서 요약

1. **Phase 4-A** (3-4일): 기반 구조
   - RagMode, RagSearchStrategy
   - V9 마이그레이션
   - Project 엔티티 수정

2. **Phase 4-B** (2-3일): Mode 1 리팩토링
   - PgVectorSearchStrategy
   - SearchController 전략 패턴

3. **Phase 4-C** (7-10일): Neo4j 통합
   - Docker Compose, 의존성
   - EntityExtractionService
   - Neo4jSearchStrategy

4. **Phase 4-D** (5-7일): Hybrid 전략
   - FusionStrategy 인터페이스
   - RRF/WeightedSum 구현
   - HybridSearchStrategy

5. **Phase 4-E** (3-4일): 동적 라우팅
   - QueryRouter
   - SearchController auto 모드

**총 예상 기간**: 20-28일

---

## 완료 기준

- [ ] 3가지 RAG 모드 모두 동작 (pgvector, neo4j, hybrid)
- [ ] SearchController에서 mode="auto" 지원
- [ ] Neo4j 엔티티 추출 및 그래프 생성 확인
- [ ] 하이브리드 검색 RRF 융합 정상 동작
- [ ] 기존 API 호환성 유지 (mode="keyword", "semantic")
- [ ] Docker Compose로 전체 스택 실행 가능
- [ ] 프로젝트별 RAG 모드 설정 (DB 컬럼 추가)

---

## 참고 문서

- [phase-4-flexible-rag-architecture.md](phase-4-flexible-rag-architecture.md): 원본 계획서
- [rag_pipeline_research.md](../../research/rag_pipeline_research.md): RAG 파이프라인 연구
- [Spring AI Neo4j 공식 문서](https://docs.spring.io/spring-ai/reference/api/vectordbs/neo4j.html)
- [Neo4j GraphRAG Python](https://neo4j.com/docs/neo4j-graphrag-python/current/)

---

이 계획은 Phase 4 계획서의 섹션 2-6을 현재 코드베이스에 맞게 구체화한 것입니다.
기존 PgVector 기반 검색을 유지하면서 Neo4j Graph RAG와 Hybrid RAG를 점진적으로 추가하는 전략입니다.
