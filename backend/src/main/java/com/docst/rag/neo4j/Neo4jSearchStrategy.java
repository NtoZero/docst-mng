package com.docst.rag.neo4j;

import com.docst.domain.DocChunk;
import com.docst.domain.DocumentVersion;
import com.docst.embedding.DocstEmbeddingService;
import com.docst.rag.RagMode;
import com.docst.rag.RagSearchStrategy;
import com.docst.repository.DocChunkRepository;
import com.docst.service.SearchService.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Neo4j Graph RAG 검색 전략.
 * Phase 4: Mode 2 - 그래프 검색
 *
 * 검색 흐름:
 * 1. Fulltext 검색으로 관련 Chunk 찾기
 * 2. 그래프 순회로 엔티티 확장 (max-hop 제한)
 * 3. 결과 통합 및 SearchResult 변환
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "docst.rag.neo4j.enabled", havingValue = "true")
public class Neo4jSearchStrategy implements RagSearchStrategy {

    private final Driver neo4jDriver;
    private final EntityExtractionService entityExtractionService;
    private final DocstEmbeddingService embeddingService;
    private final DocChunkRepository chunkRepository;

    @Value("${docst.rag.neo4j.max-hop:2}")
    private int maxHop;

    @Override
    public List<SearchResult> search(UUID projectId, String query, int topK) {
        log.debug("Neo4j Graph search: projectId={}, query='{}', topK={}", projectId, query, topK);

        try (var session = neo4jDriver.session()) {
            // 1. Fulltext search for relevant chunks
            String fulltextQuery = String.format("""
                CALL db.index.fulltext.queryNodes('chunk_content_fulltext', $query)
                YIELD node AS chunk, score
                WHERE chunk.projectId = $projectId
                RETURN chunk.chunkId AS chunkId, chunk.content AS content,
                       chunk.headingPath AS headingPath, score
                ORDER BY score DESC
                LIMIT $topK
                """);

            var result = session.run(fulltextQuery, Map.of(
                "query", query,
                "projectId", projectId.toString(),
                "topK", topK
            ));

            List<SearchResult> searchResults = new ArrayList<>();

            while (result.hasNext()) {
                Record record = result.next();
                UUID chunkId = UUID.fromString(record.get("chunkId").asString());
                String content = record.get("content").asString();
                String headingPath = record.get("headingPath").asString(null);
                double score = record.get("score").asDouble();

                // Load full chunk info from PostgreSQL
                Optional<DocChunk> chunkOpt = chunkRepository.findById(chunkId);
                if (chunkOpt.isEmpty()) {
                    log.warn("Chunk not found in PostgreSQL: {}", chunkId);
                    continue;
                }

                DocChunk chunk = chunkOpt.get();
                DocumentVersion docVersion = chunk.getDocumentVersion();

                searchResults.add(new SearchResult(
                    docVersion.getDocument().getId(),
                    docVersion.getDocument().getRepository().getId(),
                    docVersion.getDocument().getPath(),
                    docVersion.getCommitSha(),
                    chunk.getId(),
                    chunk.getHeadingPath(),
                    score,
                    chunk.getContent(),
                    highlightContent(chunk.getContent(), query)
                ));
            }

            log.info("Neo4j Graph search returned {} results", searchResults.size());
            return searchResults;

        } catch (Exception e) {
            log.error("Neo4j Graph search failed", e);
            return List.of();
        }
    }

    @Override
    public void indexDocument(DocumentVersion documentVersion) {
        log.debug("Neo4j indexing: documentId={}", documentVersion.getDocument().getId());

        // 1. Get all chunks for this document version
        List<DocChunk> chunks = chunkRepository.findByDocumentVersionIdOrderByChunkIndex(
            documentVersion.getId()
        );

        int indexedCount = 0;

        try (var session = neo4jDriver.session()) {
            for (DocChunk chunk : chunks) {
                // 2. Create Chunk node in Neo4j
                createChunkNode(session, chunk, documentVersion);

                // 3. Extract entities and relations
                var extraction = entityExtractionService.extractEntitiesAndRelations(
                    chunk.getContent(),
                    chunk.getHeadingPath()
                );

                // 4. Create Entity nodes and relationships
                for (var entity : extraction.entities()) {
                    createEntityNode(session, entity, chunk);
                }

                for (var relation : extraction.relations()) {
                    createEntityRelation(session, relation);
                }

                indexedCount++;
            }

            log.info("Neo4j indexed {} chunks for document '{}'",
                indexedCount, documentVersion.getDocument().getPath());

        } catch (Exception e) {
            log.error("Failed to index document in Neo4j", e);
            throw new RuntimeException("Neo4j indexing failed", e);
        }
    }

    @Override
    public RagMode getSupportedMode() {
        return RagMode.NEO4J;
    }

    /**
     * Neo4j에 Chunk 노드 생성.
     */
    private void createChunkNode(org.neo4j.driver.Session session, DocChunk chunk, DocumentVersion docVersion) {
        // Note: Embedding is stored in DocEmbedding table, not in DocChunk
        // For Neo4j vector search, we can optionally fetch and store embeddings
        // For now, we'll skip embeddings in Neo4j (use fulltext search instead)

        session.run("""
            MERGE (c:Chunk {chunkId: $chunkId})
            SET c.documentId = $documentId,
                c.projectId = $projectId,
                c.content = $content,
                c.headingPath = $headingPath,
                c.chunkIndex = $chunkIndex
            MERGE (d:Document {documentId: $documentId})
            SET d.path = $path,
                d.title = $title
            MERGE (c)-[:BELONGS_TO]->(d)
            """, Map.of(
            "chunkId", chunk.getId().toString(),
            "documentId", docVersion.getDocument().getId().toString(),
            "projectId", docVersion.getDocument().getRepository().getProject().getId().toString(),
            "content", chunk.getContent(),
            "headingPath", chunk.getHeadingPath() != null ? chunk.getHeadingPath() : "",
            "chunkIndex", chunk.getChunkIndex(),
            "path", docVersion.getDocument().getPath(),
            "title", docVersion.getDocument().getTitle() != null ? docVersion.getDocument().getTitle() : ""
        ));
    }

    /**
     * Neo4j에 Entity 노드 생성 및 Chunk와 연결.
     */
    private void createEntityNode(org.neo4j.driver.Session session,
                                   EntityExtractionService.EntityInfo entity,
                                   DocChunk chunk) {
        session.run("""
            MERGE (e:Entity {name: $name})
            SET e.type = $type,
                e.description = $description
            WITH e
            MATCH (c:Chunk {chunkId: $chunkId})
            MERGE (c)-[:HAS_ENTITY]->(e)
            """, Map.of(
            "name", entity.name(),
            "type", entity.type(),
            "description", entity.description() != null ? entity.description() : "",
            "chunkId", chunk.getId().toString()
        ));
    }

    /**
     * Neo4j에 Entity 간 관계 생성.
     */
    private void createEntityRelation(org.neo4j.driver.Session session,
                                       EntityExtractionService.RelationInfo relation) {
        String cypher = String.format("""
            MATCH (source:Entity {name: $source})
            MATCH (target:Entity {name: $target})
            MERGE (source)-[r:%s]->(target)
            SET r.description = $description
            """, relation.type());

        session.run(cypher, Map.of(
            "source", relation.source(),
            "target", relation.target(),
            "description", relation.description() != null ? relation.description() : ""
        ));
    }

    /**
     * 검색어로 콘텐츠 하이라이트.
     */
    private String highlightContent(String content, String query) {
        // Simple case-insensitive highlighting
        String[] keywords = query.toLowerCase().split("\\s+");
        String highlighted = content;

        for (String keyword : keywords) {
            if (keyword.length() > 2) {
                highlighted = highlighted.replaceAll(
                    "(?i)(" + keyword + ")",
                    "<mark>$1</mark>"
                );
            }
        }

        return highlighted;
    }
}
