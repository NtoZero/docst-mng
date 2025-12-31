package com.docst.embedding;

import com.docst.domain.Credential.CredentialType;
import com.docst.rag.config.ResolvedRagConfig;
import com.docst.service.DynamicCredentialResolver;
import com.docst.service.SystemConfigService;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 동적 임베딩 클라이언트 팩토리.
 * 프로젝트별 설정과 크리덴셜을 기반으로 EmbeddingModel을 동적 생성한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DynamicEmbeddingClientFactory {

    private final DynamicCredentialResolver credentialResolver;
    private final SystemConfigService systemConfigService;

    /**
     * 프로젝트용 EmbeddingModel 생성.
     *
     * @param projectId 프로젝트 ID
     * @param config 해결된 RAG 설정
     * @return EmbeddingModel
     */
    public EmbeddingModel createEmbeddingModel(UUID projectId, ResolvedRagConfig config) {
        String provider = config.getEmbeddingProvider();

        log.debug("Creating embedding model for project {}: provider={}, model={}",
                projectId, provider, config.getEmbeddingModel());

        return switch (provider.toLowerCase()) {
            case "openai" -> createOpenAiModel(projectId, config);
            case "ollama" -> createOllamaModel(config);
            default -> throw new IllegalArgumentException("Unknown embedding provider: " + provider);
        };
    }

    /**
     * OpenAI 임베딩 모델 생성.
     *
     * @param projectId 프로젝트 ID
     * @param config RAG 설정
     * @return OpenAI EmbeddingModel
     */
    private EmbeddingModel createOpenAiModel(UUID projectId, ResolvedRagConfig config) {
        // 프로젝트 > 시스템 우선순위로 API 키 조회
        String apiKey = credentialResolver.resolveApiKey(projectId, CredentialType.OPENAI_API_KEY);

        OpenAiApi openAiApi = new OpenAiApi(apiKey);

        var options = org.springframework.ai.openai.OpenAiEmbeddingOptions.builder()
                .withModel(config.getEmbeddingModel())
                .withDimensions(config.getEmbeddingDimensions())
                .build();

        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, options);
    }

    /**
     * Ollama 임베딩 모델 생성.
     *
     * @param config RAG 설정
     * @return Ollama EmbeddingModel
     */
    private EmbeddingModel createOllamaModel(ResolvedRagConfig config) {
        String baseUrl = systemConfigService.getString(SystemConfigService.OLLAMA_BASE_URL);
        boolean enabled = systemConfigService.getBoolean(SystemConfigService.OLLAMA_ENABLED, false);

        if (!enabled) {
            throw new IllegalStateException("Ollama is not enabled in system configuration");
        }

        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("Ollama base URL is not configured");
        }

        OllamaApi ollamaApi = new OllamaApi(baseUrl);

        var options = org.springframework.ai.ollama.api.OllamaOptions.builder()
                .model(config.getEmbeddingModel())
                .build();

        // ObservationRegistry.NOOP: no observability
        // ModelManagementOptions.defaults(): default model management (no auto-pull)
        return new OllamaEmbeddingModel(
                ollamaApi,
                options,
                ObservationRegistry.NOOP,
                ModelManagementOptions.defaults()
        );
    }
}
