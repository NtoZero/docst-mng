package com.docst.llm;

import com.docst.domain.Credential.CredentialType;
import com.docst.service.DynamicCredentialResolver;
import com.docst.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 동적 ChatClient 팩토리.
 * 프로젝트별 크리덴셜을 기반으로 ChatClient를 동적 생성한다.
 *
 * Spring AI 1.1.0+ API 사용.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DynamicChatClientFactory {

    private final DynamicCredentialResolver credentialResolver;
    private final SystemConfigService systemConfigService;
    private final ChatMemory chatMemory;

    // 프로젝트별 ChatClient 캐시 (성능 최적화)
    private final Map<UUID, ChatClient> chatClientCache = new ConcurrentHashMap<>();

    /**
     * 프로젝트용 ChatClient 생성 (캐시됨).
     *
     * @param projectId 프로젝트 ID
     * @return ChatClient
     */
    public ChatClient getChatClient(UUID projectId) {
        return chatClientCache.computeIfAbsent(projectId, this::createChatClient);
    }

    /**
     * ChatClient 캐시 무효화 (크리덴셜 변경 시 호출).
     *
     * @param projectId 프로젝트 ID
     */
    public void invalidateCache(UUID projectId) {
        chatClientCache.remove(projectId);
        log.info("Invalidated ChatClient cache for project {}", projectId);
    }

    /**
     * 전체 캐시 초기화.
     */
    public void clearCache() {
        chatClientCache.clear();
        log.info("Cleared all ChatClient cache");
    }

    /**
     * ChatClient 생성 (내부 메서드).
     *
     * @param projectId 프로젝트 ID
     * @return ChatClient
     */
    private ChatClient createChatClient(UUID projectId) {
        log.debug("Creating ChatClient for project {}", projectId);

        // 1. ChatModel 생성 (프로젝트별 크리덴셜 사용)
        ChatModel chatModel = createChatModel(projectId);

        // 2. ChatClient 생성 (System Prompt + ChatMemory)
        // Note: Available tools는 Spring AI가 자동으로 LLM에 전달하므로 System Prompt에 명시 불필요
        return ChatClient.builder(chatModel)
            .defaultSystem("""
                You are a helpful documentation assistant for the Docst system.
                You can search and read documents using the provided tools.

                Always respond in the same language as the user's query.
                When using tools, explain what you're doing in a clear and concise manner.
                """)
            .defaultAdvisors(
                MessageChatMemoryAdvisor.builder(chatMemory).build()
            )
            .build();
    }

    /**
     * ChatModel 생성 (Provider별 분기).
     *
     * @param projectId 프로젝트 ID
     * @return ChatModel
     */
    private ChatModel createChatModel(UUID projectId) {
        // SystemConfig에서 LLM Provider 조회 (기본값: openai)
        String providerString = systemConfigService.getString("llm.provider", "openai");
        LlmProvider provider = LlmProvider.fromString(providerString);

        log.debug("Creating ChatModel for project {}: provider={}", projectId, provider);

        return switch (provider) {
            case OPENAI -> createOpenAiChatModel(projectId);
            case OLLAMA -> throw new UnsupportedOperationException(
                "Ollama ChatModel support is not yet implemented in Spring AI 1.1.0. " +
                "Please use OPENAI provider or wait for future updates."
            );
            case ANTHROPIC -> throw new UnsupportedOperationException(
                "Anthropic ChatModel support is planned for future releases. " +
                "Please use OPENAI provider for now."
            );
        };
    }

    /**
     * OpenAI ChatModel 생성.
     * Spring AI 1.1.0+ Builder API 사용.
     *
     * @param projectId 프로젝트 ID
     * @return OpenAI ChatModel
     */
    private ChatModel createOpenAiChatModel(UUID projectId) {
        // 프로젝트 > 시스템 우선순위로 API 키 조회
        String apiKey = credentialResolver.resolveApiKey(projectId, CredentialType.OPENAI_API_KEY);

        // OpenAI API 클라이언트
        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(apiKey)
                .build();

        // Chat Options
        String model = systemConfigService.getString("llm.openai.model", "gpt-4o");
        Double temperature = systemConfigService.getDouble("llm.openai.temperature", 0.7);
        Integer maxTokens = systemConfigService.getInt("llm.openai.max-tokens", 4096);

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();

        log.info("Created OpenAI ChatModel for project {}: model={}, temperature={}, maxTokens={}",
                projectId, model, temperature, maxTokens);

        // Spring AI 1.1.0+: OpenAiChatModel 생성 (2개 파라미터)
        // Tools는 ChatClient.prompt().tools()에서 등록됨
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();
    }

    /**
     * Ollama ChatModel 생성 (향후 구현 예정).
     *
     * Spring AI 1.1.0에서 Ollama Chat API 확인 후 구현 필요.
     * 현재는 UnsupportedOperationException을 throw.
     */
    // TODO: Implement after confirming Spring AI 1.1.0 Ollama Chat API
}
