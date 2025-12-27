package com.docst.chunking;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import org.springframework.stereotype.Component;

/**
 * 토큰 카운터.
 * tiktoken 호환 라이브러리인 jtokkit을 사용하여 텍스트의 토큰 수를 계산한다.
 *
 * <p><b>Spring AI 호환성</b>:
 * Spring AI의 TokenTextSplitter도 동일한 tiktoken(jtokkit) 라이브러리를 사용하므로,
 * 이 TokenCounter는 Spring AI와 완전히 호환됩니다.
 * cl100k_base 인코딩은 OpenAI의 GPT-3.5-turbo, GPT-4, text-embedding-ada-002와 호환됩니다.
 * </p>
 *
 * <p><b>옵션</b>:
 * 향후 Spring AI TokenTextSplitter로 전환 시에도 동일한 토큰 카운팅 결과를 보장합니다.
 * </p>
 */
@Component
public class TokenCounter {

    private final Encoding encoding;

    public TokenCounter() {
        EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
        // cl100k_base: GPT-3.5-turbo, GPT-4, text-embedding-ada-002 등에서 사용
        // Spring AI TokenTextSplitter와 동일한 인코딩
        this.encoding = registry.getEncoding(EncodingType.CL100K_BASE);
    }

    /**
     * 텍스트의 토큰 수를 계산한다.
     *
     * @param text 텍스트
     * @return 토큰 수
     */
    public int countTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return encoding.countTokens(text);
    }

    /**
     * 텍스트를 최대 토큰 수로 잘라낸다.
     *
     * @param text 텍스트
     * @param maxTokens 최대 토큰 수
     * @return 잘라낸 텍스트
     */
    public String truncate(String text, int maxTokens) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        int tokenCount = countTokens(text);
        if (tokenCount <= maxTokens) {
            return text;
        }

        // 이진 탐색으로 적절한 길이 찾기
        int left = 0;
        int right = text.length();
        String result = text;

        while (left < right) {
            int mid = (left + right + 1) / 2;
            String substring = text.substring(0, mid);
            int tokens = countTokens(substring);

            if (tokens <= maxTokens) {
                result = substring;
                left = mid;
            } else {
                right = mid - 1;
            }
        }

        return result;
    }
}
