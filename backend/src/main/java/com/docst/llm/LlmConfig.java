package com.docst.llm;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LLM Configuration - ChatMemory Bean 설정
 *
 * Spring AI 1.1.0+ ChatClient API를 사용하여 Provider 독립적인 LLM 서비스 제공.
 * ChatClient는 DynamicChatClientFactory에서 프로젝트별 크리덴셜을 사용하여 동적 생성.
 */
@Configuration
@ConditionalOnProperty(prefix = "llm", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LlmConfig {

    /**
     * ChatMemory Bean - 대화 히스토리 관리
     *
     * MessageWindowChatMemory: 최근 N개 메시지를 메모리에 유지.
     * sessionId별로 대화 히스토리 분리 (MessageChatMemoryAdvisor가 처리).
     * 추후 Redis 또는 데이터베이스 기반으로 영속화 가능.
     */
    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
            .maxMessages(20)  // 최근 20개 메시지 유지
            .build();
    }
}
