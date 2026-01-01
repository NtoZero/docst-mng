package com.docst.llm;

/**
 * LLM Provider Enum.
 *
 * 지원되는 LLM Provider 목록.
 */
public enum LlmProvider {
    /**
     * OpenAI (GPT-4, GPT-3.5 등)
     */
    OPENAI("openai"),

    /**
     * Anthropic (Claude 3 등)
     */
    ANTHROPIC("anthropic"),

    /**
     * Ollama (로컬 LLM)
     */
    OLLAMA("ollama");

    private final String value;

    LlmProvider(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * 문자열 값으로 LlmProvider 조회.
     *
     * @param value Provider 문자열 (대소문자 무시)
     * @return LlmProvider enum
     * @throws IllegalArgumentException 알 수 없는 Provider인 경우
     */
    public static LlmProvider fromString(String value) {
        if (value == null || value.isBlank()) {
            return OPENAI;  // 기본값
        }

        for (LlmProvider provider : values()) {
            if (provider.value.equalsIgnoreCase(value)) {
                return provider;
            }
        }

        throw new IllegalArgumentException(
            "Unknown LLM provider: " + value + ". " +
            "Supported providers: openai, anthropic, ollama"
        );
    }

    @Override
    public String toString() {
        return value;
    }
}
