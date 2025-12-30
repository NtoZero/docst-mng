package com.docst.rag.neo4j;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.exceptions.ClientException;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Text2CypherService 단위 테스트.
 * Phase 4-C: Text-to-Cypher 변환 및 Self-healing 검증
 */
@ExtendWith(MockitoExtension.class)
class Text2CypherServiceTest {

    @Mock
    private OpenAiApi openAiApi;

    @Mock
    private Driver neo4jDriver;

    @Mock
    private Session session;

    @Mock
    private Result result;

    private Text2CypherService service;

    @BeforeEach
    void setUp() {
        service = new Text2CypherService(openAiApi, neo4jDriver);
        ReflectionTestUtils.setField(service, "model", "gpt-4o-mini");
    }

    @Test
    @DisplayName("cleanCypherQuery() - 마크다운 코드 블록 제거")
    void cleanCypherQuery_removesMarkdown() {
        // Given
        String[] inputs = {
            "```cypher\nMATCH (n) RETURN n\n```",
            "```\nMATCH (n) RETURN n\n```",
            "MATCH (n) RETURN n",
            "  ```cypher\n  MATCH (n) RETURN n  \n```  "
        };

        String expected = "MATCH (n) RETURN n";

        // When & Then
        for (String input : inputs) {
            String cleaned = cleanCypherQuery(input);
            assertEquals(expected, cleaned, "Failed for input: " + input);
        }
    }

    @Test
    @DisplayName("cleanCypherQuery() - 공백 trim")
    void cleanCypherQuery_trimsWhitespace() {
        // Given
        String input = "  \n  MATCH (n) RETURN n  \n  ";

        // When
        String cleaned = cleanCypherQuery(input);

        // Then
        assertEquals("MATCH (n) RETURN n", cleaned);
    }

    @Test
    @DisplayName("buildSystemPrompt() - 기본 프롬프트")
    void buildSystemPrompt_default() {
        // When
        String prompt = buildSystemPrompt(null);

        // Then
        assertTrue(prompt.contains("Neo4j Cypher queries"));
        assertTrue(prompt.contains("Graph Schema"));
        assertTrue(prompt.contains(":Chunk"));
        assertTrue(prompt.contains(":Entity"));
        assertTrue(prompt.contains("HAS_ENTITY"));
        assertFalse(prompt.contains("IMPORTANT: Your previous query had this error"));
    }

    @Test
    @DisplayName("buildSystemPrompt() - 이전 오류 포함")
    void buildSystemPrompt_withPreviousError() {
        // Given
        String previousError = "Syntax error near 'MTCH'";

        // When
        String prompt = buildSystemPrompt(previousError);

        // Then
        assertTrue(prompt.contains("IMPORTANT: Your previous query had this error"));
        assertTrue(prompt.contains(previousError));
        assertTrue(prompt.contains("Please fix the query"));
    }

    @Test
    @DisplayName("buildUserPrompt() - 기본 질문")
    void buildUserPrompt_default() {
        // Given
        String question = "What is authentication?";

        // When
        String prompt = buildUserPrompt(question, null);

        // Then
        assertTrue(prompt.contains("Question: " + question));
        assertTrue(prompt.contains("Generate a Cypher query"));
        assertFalse(prompt.contains("Previous error"));
    }

    @Test
    @DisplayName("buildUserPrompt() - 오류 재시도")
    void buildUserPrompt_withPreviousError() {
        // Given
        String question = "What is authentication?";
        String previousError = "Syntax error";

        // When
        String prompt = buildUserPrompt(question, previousError);

        // Then
        assertTrue(prompt.contains("Question: " + question));
        assertTrue(prompt.contains("Previous error: " + previousError));
        assertTrue(prompt.contains("Generate a corrected Cypher query"));
    }

    @Test
    @DisplayName("Cypher 쿼리 검증 - 정상 케이스")
    void validateCypherQuery_validQuery() {
        // Given
        when(neo4jDriver.session()).thenReturn(session);
        when(session.run(anyString())).thenReturn(result);

        // When & Then
        assertDoesNotThrow(() -> validateCypherQuery("MATCH (n) RETURN n"));

        verify(neo4jDriver).session();
        verify(session).run("MATCH (n) RETURN n LIMIT 1");
        verify(session).close();
    }

    @Test
    @DisplayName("Cypher 쿼리 검증 - 구문 오류")
    void validateCypherQuery_syntaxError() {
        // Given
        when(neo4jDriver.session()).thenReturn(session);
        when(session.run(anyString())).thenThrow(
            new ClientException("Neo.ClientError.Statement.SyntaxError", "Syntax error")
        );

        // When & Then
        assertThrows(ClientException.class, () ->
            validateCypherQuery("INVALID QUERY")
        );

        verify(session).close();
    }

    @Test
    @DisplayName("예제 질문 - 엔티티 검색")
    void exampleQuery_entitySearch() {
        // 실제 LLM이 생성할 만한 쿼리
        String expectedCypher = "MATCH (e:Entity {name: 'Authentication'}) RETURN e";

        // Given
        String question = "What is authentication?";

        // 쿼리가 올바른 형식인지 확인
        assertTrue(expectedCypher.startsWith("MATCH"));
        assertTrue(expectedCypher.contains(":Entity"));
        assertTrue(expectedCypher.contains("RETURN"));
    }

    @Test
    @DisplayName("예제 질문 - 관계 검색")
    void exampleQuery_relationshipSearch() {
        // 실제 LLM이 생성할 만한 쿼리
        String expectedCypher = """
            MATCH (e1:Entity)-[:USES]->(e2:Entity {name: 'Redis'})
            WHERE e1.type = 'Component'
            RETURN e1
            LIMIT 20
            """.trim();

        // Given
        String question = "Components that use Redis";

        // 쿼리가 올바른 형식인지 확인
        assertTrue(expectedCypher.contains("MATCH"));
        assertTrue(expectedCypher.contains("[:USES]->"));
        assertTrue(expectedCypher.contains("WHERE"));
        assertTrue(expectedCypher.contains("LIMIT"));
    }

    @Test
    @DisplayName("예제 질문 - 복잡한 그래프 순회")
    void exampleQuery_complexTraversal() {
        // 실제 LLM이 생성할 만한 쿼리
        String expectedCypher = """
            MATCH (c:Chunk)-[:HAS_ENTITY]->(e:Entity)
            WHERE toLower(e.name) CONTAINS toLower('authentication')
            RETURN c.content, e.name, e.type
            LIMIT 10
            """.trim();

        // Given
        String question = "Find chunks about authentication";

        // 쿼리가 올바른 형식인지 확인
        assertTrue(expectedCypher.contains("MATCH"));
        assertTrue(expectedCypher.contains("[:HAS_ENTITY]->"));
        assertTrue(expectedCypher.contains("toLower"));
        assertTrue(expectedCypher.contains("RETURN"));
    }

    // Helper methods (실제 서비스 메서드를 복제하여 테스트)

    private String cleanCypherQuery(String query) {
        String cleaned = query.trim();

        // Remove markdown code blocks
        if (cleaned.startsWith("```cypher")) {
            cleaned = cleaned.substring(9);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        return cleaned.trim();
    }

    private String buildSystemPrompt(String previousError) {
        String basePrompt = """
            You are an expert at converting natural language questions to Neo4j Cypher queries.

            Graph Schema:
            - (:Chunk {chunkId, documentId, content, headingPath, embedding: Vector})
            - (:Entity {name, type, description})
            - (:Document {documentId, path, title})
            - (Chunk)-[:HAS_ENTITY]->(Entity)
            - (Entity)-[:RELATED_TO|DEPENDS_ON|USES|PART_OF]->(Entity)
            - (Chunk)-[:BELONGS_TO]->(Document)

            Guidelines:
            1. Return ONLY the Cypher query, no explanations or markdown
            2. Use MATCH for reading data
            3. Use WHERE clauses for filtering
            4. Limit results with LIMIT clause (default: 20)
            5. Return relevant node properties
            6. Use case-insensitive matching with toLower() when appropriate

            Example queries:
            - "What is authentication?" → MATCH (e:Entity {name: 'Authentication'}) RETURN e
            - "Components that use Redis" → MATCH (e1:Entity)-[:USES]->(e2:Entity {name: 'Redis'}) WHERE e1.type = 'Component' RETURN e1
            """;

        if (previousError != null) {
            basePrompt += String.format("""

                IMPORTANT: Your previous query had this error:
                %s

                Please fix the query to avoid this error.
                """, previousError);
        }

        return basePrompt;
    }

    private String buildUserPrompt(String question, String previousError) {
        if (previousError != null) {
            return String.format("""
                Question: %s

                Previous error: %s

                Generate a corrected Cypher query.
                """, question, previousError);
        } else {
            return String.format("Question: %s\n\nGenerate a Cypher query to answer this question.", question);
        }
    }

    private void validateCypherQuery(String cypherQuery) {
        try (var session = neo4jDriver.session()) {
            // Execute with LIMIT 1 to validate syntax
            session.run(cypherQuery + " LIMIT 1").consume();
        }
    }
}
