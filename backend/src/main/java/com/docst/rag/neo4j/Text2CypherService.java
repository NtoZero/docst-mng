package com.docst.rag.neo4j;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.exceptions.Neo4jException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Text-to-Cypher 서비스.
 * Phase 4: 자연어 질문을 Cypher 쿼리로 변환
 *
 * Self-healing:
 * - Cypher 쿼리 실행 시 오류 발생하면 오류 메시지를 포함하여 재생성
 * - 최대 3회까지 재시도
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "docst.rag.neo4j.enabled", havingValue = "true")
public class Text2CypherService {

    private static final String DEFAULT_MODEL = "gpt-4o-mini";

    private final OpenAiApi openAiApi;
    private final Driver neo4jDriver;

    /**
     * 자연어 질문을 Cypher 쿼리로 변환 (기본 모델 사용).
     *
     * @param question 자연어 질문
     * @return Cypher 쿼리
     */
    public String generateCypher(String question) {
        return generateCypher(question, DEFAULT_MODEL);
    }

    /**
     * 자연어 질문을 Cypher 쿼리로 변환.
     *
     * @param question 자연어 질문
     * @param model 사용할 LLM 모델 (동적 설정)
     * @return Cypher 쿼리
     */
    public String generateCypher(String question, String model) {
        return generateCypherWithRetry(question, model, null, 0);
    }

    /**
     * Self-healing Cypher 생성 (재시도 포함).
     *
     * @param question 자연어 질문
     * @param model 사용할 LLM 모델
     * @param previousError 이전 오류 메시지 (재시도 시)
     * @param retryCount 재시도 횟수
     * @return Cypher 쿼리
     */
    private String generateCypherWithRetry(String question, String model, String previousError, int retryCount) {
        if (retryCount > 3) {
            throw new RuntimeException("Failed to generate valid Cypher query after 3 retries");
        }

        String modelToUse = model != null ? model : DEFAULT_MODEL;

        ChatClient chatClient = ChatClient.builder(new OpenAiChatModel(openAiApi,
            OpenAiChatOptions.builder()
                .model(modelToUse)
                .temperature(0.0)
                .build()
        )).build();

        String systemPrompt = buildSystemPrompt(previousError);
        String userPrompt = buildUserPrompt(question, previousError);

        try {
            String response = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();

            log.debug("Generated Cypher query: {}", response);

            String cypherQuery = cleanCypherQuery(response);

            // Validate query by executing it
            validateCypherQuery(cypherQuery);

            return cypherQuery;

        } catch (Neo4jException e) {
            log.warn("Cypher query validation failed (retry {}): {}", retryCount + 1, e.getMessage());
            return generateCypherWithRetry(question, model, e.getMessage(), retryCount + 1);
        } catch (Exception e) {
            log.error("Failed to generate Cypher query", e);
            throw new RuntimeException("Cypher generation failed", e);
        }
    }

    /**
     * System prompt 생성.
     */
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

    /**
     * User prompt 생성.
     */
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

    /**
     * Cypher 쿼리 정리 (마크다운 제거 등).
     */
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

    /**
     * Cypher 쿼리 유효성 검증 (실제 실행).
     */
    private void validateCypherQuery(String cypherQuery) {
        try (var session = neo4jDriver.session()) {
            // Execute with LIMIT 1 to validate syntax
            session.run(cypherQuery + " LIMIT 1").consume();
        }
    }
}
