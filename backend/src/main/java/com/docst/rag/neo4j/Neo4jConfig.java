package com.docst.rag.neo4j;

import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

/**
 * Neo4j 설정 클래스.
 * Phase 4: Graph RAG를 위한 Neo4j 초기화
 *
 * 초기화 작업: //todo: 애플리케이션 시작 시 벡터 인덱스 생성하는 것의 적절성 검토 필요
 * - Chunk 노드에 대한 벡터 인덱스 생성 (유사도 검색용)
 * - Chunk 노드에 대한 전문 검색 인덱스 생성 (키워드 검색용)
 * - Entity 노드 인덱스 생성
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "docst.rag.neo4j.enabled", havingValue = "true")
public class Neo4jConfig {

    private final Driver driver;

    public Neo4jConfig(Driver driver) {
        this.driver = driver;
    }

    /**
     * 애플리케이션 시작 시 Neo4j 인덱스 초기화.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeNeo4jIndexes() {
        log.info("Initializing Neo4j indexes for Graph RAG...");

        try (var session = driver.session()) {
            // 1. Chunk 노드 전문 검색 인덱스 (Fulltext)
            session.run("""
                CREATE FULLTEXT INDEX chunk_content_fulltext IF NOT EXISTS
                FOR (c:Chunk)
                ON EACH [c.content, c.headingPath]
                """);
            log.info("Created fulltext index: chunk_content_fulltext");

            // 2. Entity 노드 인덱스
            session.run("""
                CREATE INDEX entity_name_index IF NOT EXISTS
                FOR (e:Entity)
                ON (e.name)
                """);
            log.info("Created index: entity_name_index");

            // 3. Chunk 노드 ID 인덱스
            session.run("""
                CREATE INDEX chunk_id_index IF NOT EXISTS
                FOR (c:Chunk)
                ON (c.chunkId)
                """);
            log.info("Created index: chunk_id_index");

            // 4. Document 노드 ID 인덱스
            session.run("""
                CREATE INDEX document_id_index IF NOT EXISTS
                FOR (d:Document)
                ON (d.documentId)
                """);
            log.info("Created index: document_id_index");

            log.info("Neo4j indexes initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize Neo4j indexes", e);
            throw new RuntimeException("Neo4j initialization failed", e);
        }
    }
}
