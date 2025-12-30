package com.docst.rag.neo4j;

import com.docst.domain.*;
import com.docst.embedding.DocstEmbeddingService;
import com.docst.rag.RagMode;
import com.docst.rag.config.RagConfigService;
import com.docst.rag.config.ResolvedRagConfig;
import com.docst.repository.DocChunkRepository;
import com.docst.repository.ProjectRepository;
import com.docst.service.SearchService.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Neo4jSearchStrategy 통합 테스트.
 * Phase 4-C: Mode 2 (그래프 검색) 전략 검증
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class Neo4jSearchStrategyTest {

    @Mock
    private Driver neo4jDriver;

    @Mock
    private EntityExtractionService entityExtractionService;

    @Mock
    private DocstEmbeddingService embeddingService;

    @Mock
    private DocChunkRepository chunkRepository;

    @Mock
    private RagConfigService ragConfigService;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private Session session;

    @Mock
    private Result queryResult;

    @Mock
    private org.neo4j.driver.Record record;

    @InjectMocks
    private Neo4jSearchStrategy strategy;

    private UUID testProjectId;
    private UUID testDocId;
    private UUID testChunkId;
    private UUID testRepoId;
    private Project testProject;

    @BeforeEach
    void setUp() {
        testProjectId = UUID.randomUUID();
        testDocId = UUID.randomUUID();
        testChunkId = UUID.randomUUID();
        testRepoId = UUID.randomUUID();

        // Mock project
        testProject = mock(Project.class);
        when(testProject.getId()).thenReturn(testProjectId);
        when(projectRepository.findById(testProjectId)).thenReturn(Optional.of(testProject));

        // Mock RagConfigService to return default config
        when(ragConfigService.resolve(any(Project.class))).thenReturn(ResolvedRagConfig.defaults());
    }

    @Test
    @DisplayName("getSupportedMode() → NEO4J 반환")
    void getSupportedMode_returnsNeo4j() {
        // When
        RagMode mode = strategy.getSupportedMode();

        // Then
        assertEquals(RagMode.NEO4J, mode);
    }

    @Test
    @DisplayName("search() - Fulltext 검색 성공")
    void search_fulltextSearch_success() {
        // Given
        String query = "authentication";
        int topK = 5;

        // Mock Neo4j session
        when(neo4jDriver.session()).thenReturn(session);
        when(session.run(anyString(), anyMap())).thenReturn(queryResult);

        // Mock query result
        when(queryResult.hasNext()).thenReturn(true, false);
        when(queryResult.next()).thenReturn(record);

        // Mock record values
        Value chunkIdValue = mock(Value.class);
        when(chunkIdValue.asString()).thenReturn(testChunkId.toString());

        Value contentValue = mock(Value.class);
        when(contentValue.asString()).thenReturn("Authentication is a security concept...");

        Value headingPathValue = mock(Value.class);
        when(headingPathValue.asString(null)).thenReturn("# Security");

        Value scoreValue = mock(Value.class);
        when(scoreValue.asDouble()).thenReturn(0.95);

        when(record.get("chunkId")).thenReturn(chunkIdValue);
        when(record.get("content")).thenReturn(contentValue);
        when(record.get("headingPath")).thenReturn(headingPathValue);
        when(record.get("score")).thenReturn(scoreValue);

        // Mock chunk from PostgreSQL
        DocChunk chunk = createTestChunk();
        when(chunkRepository.findById(testChunkId)).thenReturn(Optional.of(chunk));

        // When
        List<SearchResult> results = strategy.search(testProjectId, query, topK);

        // Then
        assertEquals(1, results.size());

        SearchResult result = results.get(0);
        assertEquals(testDocId, result.documentId());
        assertEquals("# Security", result.headingPath());
        assertEquals(0.95, result.score());

        // Verify Neo4j query
        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(session).run(queryCaptor.capture(), paramsCaptor.capture());

        String executedQuery = queryCaptor.getValue();
        assertTrue(executedQuery.contains("db.index.fulltext.queryNodes"));
        assertTrue(executedQuery.contains("chunk_content_fulltext"));

        Map<String, Object> params = paramsCaptor.getValue();
        assertEquals(query, params.get("query"));
        assertEquals(testProjectId.toString(), params.get("projectId"));
        assertEquals(topK, params.get("topK"));

        verify(session).close();
    }

    @Test
    @DisplayName("search() - 결과 없음")
    void search_noResults() {
        // Given
        when(neo4jDriver.session()).thenReturn(session);
        when(session.run(anyString(), anyMap())).thenReturn(queryResult);
        when(queryResult.hasNext()).thenReturn(false);

        // When
        List<SearchResult> results = strategy.search(testProjectId, "nonexistent", 10);

        // Then
        assertTrue(results.isEmpty());
        verify(session).close();
    }

    @Test
    @DisplayName("search() - PostgreSQL에서 청크를 찾을 수 없음")
    void search_chunkNotFoundInPostgresql() {
        // Given
        when(neo4jDriver.session()).thenReturn(session);
        when(session.run(anyString(), anyMap())).thenReturn(queryResult);

        when(queryResult.hasNext()).thenReturn(true, false);
        when(queryResult.next()).thenReturn(record);

        Value chunkIdValue = mock(Value.class);
        when(chunkIdValue.asString()).thenReturn(testChunkId.toString());
        when(record.get("chunkId")).thenReturn(chunkIdValue);

        Value contentValue = mock(Value.class);
        when(contentValue.asString()).thenReturn("content");
        when(record.get("content")).thenReturn(contentValue);

        Value headingPathValue = mock(Value.class);
        when(headingPathValue.asString(null)).thenReturn("path");
        when(record.get("headingPath")).thenReturn(headingPathValue);

        Value scoreValue = mock(Value.class);
        when(scoreValue.asDouble()).thenReturn(0.9);
        when(record.get("score")).thenReturn(scoreValue);

        when(chunkRepository.findById(testChunkId)).thenReturn(Optional.empty());

        // When
        List<SearchResult> results = strategy.search(testProjectId, "query", 5);

        // Then
        assertTrue(results.isEmpty());
        verify(session).close();
    }

    @Test
    @DisplayName("search() - Neo4j 오류 발생 시 빈 리스트 반환")
    void search_neo4jError_returnsEmptyList() {
        // Given
        when(neo4jDriver.session()).thenThrow(new RuntimeException("Neo4j connection failed"));

        // When
        List<SearchResult> results = strategy.search(testProjectId, "query", 5);

        // Then
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("indexDocument() - 청크 노드 및 엔티티 생성")
    void indexDocument_createsChunkAndEntities() {
        // Given
        DocumentVersion docVersion = createTestDocumentVersion();
        DocChunk chunk1 = createTestChunk();
        List<DocChunk> chunks = List.of(chunk1);

        when(chunkRepository.findByDocumentVersionIdOrderByChunkIndex(docVersion.getId()))
            .thenReturn(chunks);

        when(neo4jDriver.session()).thenReturn(session);

        // Mock entity extraction
        EntityExtractionService.EntityInfo entity1 = new EntityExtractionService.EntityInfo(
            "Spring Boot",
            "Technology",
            "Java framework"
        );
        EntityExtractionService.RelationInfo relation1 = new EntityExtractionService.RelationInfo(
            "Spring Boot",
            "Redis",
            "USES",
            "Uses Redis for caching"
        );
        EntityExtractionService.ExtractionResult extraction =
            new EntityExtractionService.ExtractionResult(
                List.of(entity1),
                List.of(relation1)
            );

        when(entityExtractionService.extractEntitiesAndRelations(anyString(), anyString(), anyString()))
            .thenReturn(extraction);

        // When
        strategy.indexDocument(docVersion);

        // Then
        // Verify chunk node creation
        verify(session, times(1)).run(
            contains("MERGE (c:Chunk"),
            anyMap()
        );

        // Verify entity node creation
        verify(session, times(1)).run(
            contains("MERGE (e:Entity"),
            anyMap()
        );

        // Verify relation creation
        verify(session, times(1)).run(
            contains("MATCH (source:Entity"),
            anyMap()
        );

        verify(session).close();
    }

    @Test
    @DisplayName("indexDocument() - 엔티티 없는 청크")
    void indexDocument_noEntities() {
        // Given
        DocumentVersion docVersion = createTestDocumentVersion();
        DocChunk chunk = createTestChunk();

        when(chunkRepository.findByDocumentVersionIdOrderByChunkIndex(docVersion.getId()))
            .thenReturn(List.of(chunk));

        when(neo4jDriver.session()).thenReturn(session);

        // Empty extraction result
        EntityExtractionService.ExtractionResult emptyExtraction =
            new EntityExtractionService.ExtractionResult(List.of(), List.of());

        when(entityExtractionService.extractEntitiesAndRelations(anyString(), anyString(), anyString()))
            .thenReturn(emptyExtraction);

        // When
        strategy.indexDocument(docVersion);

        // Then
        // Chunk node should still be created
        verify(session, times(1)).run(
            contains("MERGE (c:Chunk"),
            anyMap()
        );

        // No entity or relation creation
        verify(session, never()).run(
            contains("MERGE (e:Entity"),
            anyMap()
        );

        verify(session).close();
    }

    @Test
    @DisplayName("indexDocument() - 여러 청크 처리")
    void indexDocument_multipleChunks() {
        // Given
        DocumentVersion docVersion = createTestDocumentVersion();
        DocChunk chunk1 = createTestChunk();
        DocChunk chunk2 = createTestChunk();
        chunk2 = mock(DocChunk.class);
        when(chunk2.getId()).thenReturn(UUID.randomUUID());
        when(chunk2.getContent()).thenReturn("Second chunk content");
        when(chunk2.getHeadingPath()).thenReturn("# Section 2");
        when(chunk2.getChunkIndex()).thenReturn(1);

        when(chunkRepository.findByDocumentVersionIdOrderByChunkIndex(docVersion.getId()))
            .thenReturn(List.of(chunk1, chunk2));

        when(neo4jDriver.session()).thenReturn(session);

        EntityExtractionService.ExtractionResult emptyExtraction =
            new EntityExtractionService.ExtractionResult(List.of(), List.of());

        when(entityExtractionService.extractEntitiesAndRelations(anyString(), anyString(), anyString()))
            .thenReturn(emptyExtraction);

        // When
        strategy.indexDocument(docVersion);

        // Then
        // Two chunk nodes created
        verify(session, times(2)).run(
            contains("MERGE (c:Chunk"),
            anyMap()
        );

        verify(entityExtractionService, times(2))
            .extractEntitiesAndRelations(anyString(), anyString(), anyString());

        verify(session).close();
    }

    @Test
    @DisplayName("indexDocument() - Neo4j 오류 발생 시 예외 전파")
    void indexDocument_neo4jError_throwsException() {
        // Given
        DocumentVersion docVersion = createTestDocumentVersion();

        when(chunkRepository.findByDocumentVersionIdOrderByChunkIndex(docVersion.getId()))
            .thenThrow(new RuntimeException("Database error"));

        // When & Then
        assertThrows(RuntimeException.class, () -> strategy.indexDocument(docVersion));
    }

    // Helper methods

    private DocChunk createTestChunk() {
        DocChunk chunk = mock(DocChunk.class);
        when(chunk.getId()).thenReturn(testChunkId);
        when(chunk.getContent()).thenReturn("Test content about authentication");
        when(chunk.getHeadingPath()).thenReturn("# Security");
        when(chunk.getChunkIndex()).thenReturn(0);

        DocumentVersion docVersion = createTestDocumentVersion();
        when(chunk.getDocumentVersion()).thenReturn(docVersion);

        return chunk;
    }

    private DocumentVersion createTestDocumentVersion() {
        DocumentVersion docVersion = mock(DocumentVersion.class);
        when(docVersion.getId()).thenReturn(UUID.randomUUID());
        when(docVersion.getCommitSha()).thenReturn("abc123");

        Document doc = mock(Document.class);
        when(doc.getId()).thenReturn(testDocId);
        when(doc.getPath()).thenReturn("docs/security.md");
        when(doc.getTitle()).thenReturn("Security Guide");

        Repository repo = mock(Repository.class);
        when(repo.getId()).thenReturn(testRepoId);

        Project project = mock(Project.class);
        when(project.getId()).thenReturn(testProjectId);

        when(repo.getProject()).thenReturn(project);
        when(doc.getRepository()).thenReturn(repo);
        when(docVersion.getDocument()).thenReturn(doc);

        return docVersion;
    }
}
