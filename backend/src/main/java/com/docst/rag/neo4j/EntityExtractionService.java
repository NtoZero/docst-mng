package com.docst.rag.neo4j;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 엔티티 추출 서비스.
 * Phase 4: LLM을 사용하여 문서 청크에서 엔티티와 관계를 추출
 *
 * 엔티티 유형:
 * - Concept: 개념, 용어
 * - API: API 엔드포인트, 함수
 * - Component: 시스템 컴포넌트
 * - Technology: 기술, 프레임워크
 *
 * 관계 유형:
 * - RELATED_TO: 관련됨
 * - DEPENDS_ON: 의존함
 * - USES: 사용함
 * - PART_OF: 일부임
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "docst.rag.neo4j.enabled", havingValue = "true")
public class EntityExtractionService {

    private final OpenAiApi openAiApi;
    private final ObjectMapper objectMapper;

    @Value("${docst.rag.neo4j.entity-extraction-model:gpt-4o-mini}")
    private String extractionModel;

    /**
     * 청크 내용에서 엔티티와 관계 추출.
     *
     * @param content 청크 내용
     * @param headingPath 헤딩 경로 (문맥 정보)
     * @return 추출된 엔티티 및 관계
     */
    public ExtractionResult extractEntitiesAndRelations(String content, String headingPath) {
        log.debug("Extracting entities from chunk: headingPath={}", headingPath);

        // ChatClient 생성 (매번 새로 생성)
        ChatClient chatClient = ChatClient.builder(new OpenAiChatModel(openAiApi,
            OpenAiChatOptions.builder()
                .model(extractionModel)
                .temperature(0.0)
                .build()
        )).build();

        String systemPrompt = """
            You are an expert at extracting entities and relationships from technical documentation.

            Extract entities and relationships from the given documentation chunk.

            Entity Types:
            - Concept: Technical concepts, terms, definitions
            - API: API endpoints, functions, methods
            - Component: System components, modules, services
            - Technology: Technologies, frameworks, libraries

            Relationship Types:
            - RELATED_TO: General association
            - DEPENDS_ON: Dependency relationship
            - USES: Usage relationship
            - PART_OF: Hierarchical relationship

            Return ONLY a valid JSON object with this exact structure:
            {
              "entities": [
                {
                  "name": "Entity Name",
                  "type": "Concept|API|Component|Technology",
                  "description": "Brief description"
                }
              ],
              "relations": [
                {
                  "source": "Source Entity Name",
                  "target": "Target Entity Name",
                  "type": "RELATED_TO|DEPENDS_ON|USES|PART_OF",
                  "description": "Relationship description"
                }
              ]
            }

            If no entities or relations are found, return empty arrays.
            Do not include any markdown formatting, code blocks, or explanatory text - only the JSON object.
            """;

        String userPrompt = String.format("""
            Documentation Section: %s

            Content:
            %s

            Extract all relevant entities and their relationships.
            """, headingPath != null ? headingPath : "Unknown", content);

        try {
            String response = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();

            log.debug("LLM response: {}", response);

            // JSON 파싱
            return parseExtractionResult(response);

        } catch (Exception e) {
            log.error("Failed to extract entities", e);
            // 실패 시 빈 결과 반환
            return new ExtractionResult(List.of(), List.of());
        }
    }

    /**
     * LLM 응답을 파싱하여 ExtractionResult로 변환.
     */
    private ExtractionResult parseExtractionResult(String jsonResponse) {
        try {
            // Remove markdown code blocks if present
            String cleanedJson = jsonResponse.trim();
            if (cleanedJson.startsWith("```json")) {
                cleanedJson = cleanedJson.substring(7);
            } else if (cleanedJson.startsWith("```")) {
                cleanedJson = cleanedJson.substring(3);
            }
            if (cleanedJson.endsWith("```")) {
                cleanedJson = cleanedJson.substring(0, cleanedJson.length() - 3);
            }
            cleanedJson = cleanedJson.trim();

            return objectMapper.readValue(cleanedJson, ExtractionResult.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse extraction result: {}", jsonResponse, e);
            return new ExtractionResult(List.of(), List.of());
        }
    }

    /**
     * 엔티티 추출 결과.
     */
    public record ExtractionResult(
        List<EntityInfo> entities,
        List<RelationInfo> relations
    ) {}

    /**
     * 엔티티 정보.
     */
    public record EntityInfo(
        String name,
        String type,
        String description
    ) {}

    /**
     * 관계 정보.
     */
    public record RelationInfo(
        String source,
        String target,
        String type,
        String description
    ) {}
}
