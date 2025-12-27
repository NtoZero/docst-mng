package com.docst.chunking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TokenCounter 단위 테스트.
 * tiktoken(jtokkit) 기반 토큰 계산 검증
 */
class TokenCounterTest {

    private TokenCounter tokenCounter;

    @BeforeEach
    void setUp() {
        tokenCounter = new TokenCounter();
    }

    @Test
    @DisplayName("빈 문자열 → 토큰 수 0 반환")
    void countTokens_emptyString_returnsZero() {
        assertEquals(0, tokenCounter.countTokens(""));
    }

    @Test
    @DisplayName("간단한 영어 텍스트 → 1~5 토큰 생성")
    void countTokens_simpleEnglishText() {
        String text = "Hello, world!";
        int count = tokenCounter.countTokens(text);
        assertTrue(count > 0 && count <= 5, "Expected 1-5 tokens for simple greeting");
    }

    @Test
    @DisplayName("한국어 텍스트 → 토큰 정상 생성")
    void countTokens_koreanText() {
        String text = "안녕하세요, 세계!";
        int count = tokenCounter.countTokens(text);
        assertTrue(count > 0, "Korean text should produce tokens");
    }

    @Test
    @DisplayName("영어+한국어 혼합 텍스트 → 다중 토큰 생성")
    void countTokens_mixedLanguage() {
        String text = "This is a test. 이것은 테스트입니다.";
        int count = tokenCounter.countTokens(text);
        assertTrue(count > 5, "Mixed language text should produce multiple tokens");
    }

    @Test
    @DisplayName("긴 텍스트(20회 반복) → 100개 이상 토큰 생성")
    void countTokens_longText() {
        String text = "The quick brown fox jumps over the lazy dog. ".repeat(20);
        int count = tokenCounter.countTokens(text);
        assertTrue(count > 100, "Long text should produce many tokens");
    }

    @Test
    @DisplayName("코드 스니펫 → 20개 이상 토큰 생성")
    void countTokens_codeSnippet() {
        String code = """
            public class HelloWorld {
                public static void main(String[] args) {
                    System.out.println("Hello, World!");
                }
            }
            """;
        int count = tokenCounter.countTokens(code);
        assertTrue(count > 20, "Code snippet should produce multiple tokens");
    }

    @Test
    @DisplayName("마크다운 헤딩 구조 → 10개 이상 토큰 생성")
    void countTokens_markdownHeadings() {
        String markdown = """
            # Main Title

            ## Section 1

            Content here.

            ### Subsection 1.1

            More content.
            """;
        int count = tokenCounter.countTokens(markdown);
        assertTrue(count > 10, "Markdown with headings should produce tokens");
    }

    @Test
    @DisplayName("null 입력 → 토큰 수 0 반환 (예외 발생 안함)")
    void countTokens_nullString_returnsZero() {
        // TokenCounter handles null gracefully and returns 0
        assertEquals(0, tokenCounter.countTokens(null));
    }

    @Test
    @DisplayName("특수문자 텍스트 → 토큰 정상 생성")
    void countTokens_specialCharacters() {
        String text = "Special chars: @#$%^&*()_+-={}[]|\\:;\"'<>,.?/~`";
        int count = tokenCounter.countTokens(text);
        assertTrue(count > 0, "Special characters should produce tokens");
    }

    @Test
    @DisplayName("공백만 있는 텍스트 → 0 또는 최소 토큰")
    void countTokens_whitespaceOnly() {
        String text = "   \n\t  \r\n  ";
        int count = tokenCounter.countTokens(text);
        // Whitespace may produce 0 or minimal tokens depending on encoding
        assertTrue(count >= 0, "Whitespace should produce zero or minimal tokens");
    }

    @Test
    @DisplayName("이모지 및 유니코드(중국어/일본어) → 다중 토큰 생성")
    void countTokens_emojiAndUnicode() {
        String text = "Hello 👋 World 🌍 测试 テスト";
        int count = tokenCounter.countTokens(text);
        assertTrue(count > 5, "Emoji and unicode should produce tokens");
    }

    @Test
    @DisplayName("동일 텍스트 반복 호출 → 일관된 토큰 수 반환")
    void countTokens_consistency() {
        String text = "This is a consistency test.";
        int count1 = tokenCounter.countTokens(text);
        int count2 = tokenCounter.countTokens(text);
        assertEquals(count1, count2, "Token count should be consistent");
    }
}
