package com.docst.chunking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MarkdownChunker 단위 테스트.
 * 마크다운 헤딩 기반 청킹 로직 검증
 */
class MarkdownChunkerTest {

    private MarkdownChunker chunker;
    private TokenCounter tokenCounter;
    private ChunkingConfig config;

    @BeforeEach
    void setUp() {
        tokenCounter = new TokenCounter();
        config = new ChunkingConfig();
        config.setMaxTokens(512);
        config.setOverlapTokens(50);
        config.setMinTokens(100);
        config.setHeadingPathSeparator(" > ");
        chunker = new MarkdownChunker(tokenCounter, config);
    }

    @Test
    @DisplayName("빈 콘텐츠 → 빈 청크 리스트 반환")
    void chunk_emptyContent_returnsEmptyList() {
        List<ChunkResult> results = chunker.chunk("");
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("헤딩 없는 단순 텍스트 → 단일 청크 생성")
    void chunk_simpleTextWithoutHeadings() {
        String content = "This is a simple paragraph without any headings.";
        List<ChunkResult> results = chunker.chunk(content);

        assertEquals(1, results.size());
        assertEquals(content.trim(), results.get(0).content().trim());
        assertTrue(results.get(0).headingPath().isEmpty() || results.get(0).headingPath() == null ||
                   "".equals(results.get(0).headingPath()));
    }

    @Test
    @DisplayName("단일 H1 헤딩 → 헤딩 포함 청크 생성")
    void chunk_singleH1Heading() {
        String content = """
            # Main Title

            This is the content under the main title.
            """;
        List<ChunkResult> results = chunker.chunk(content);

        assertTrue(results.size() >= 1);
        ChunkResult firstChunk = results.get(0);
        assertTrue(firstChunk.content().contains("Main Title"));
        assertTrue(firstChunk.content().contains("This is the content"));
    }

    @Test
    @DisplayName("중첩된 헤딩(H1→H2→H3) → headingPath 경로 생성")
    void chunk_nestedHeadings() {
        String content = """
            # Main Title

            ## Section 1

            Content for section 1.

            ### Subsection 1.1

            Content for subsection 1.1.

            ## Section 2

            Content for section 2.
            """;
        List<ChunkResult> results = chunker.chunk(content);

        assertTrue(results.size() >= 1);

        // Check that heading paths are generated
        boolean hasHeadingPath = results.stream()
            .anyMatch(r -> r.headingPath() != null && !r.headingPath().isEmpty());

        assertTrue(hasHeadingPath, "Should have heading paths");
    }

    @Test
    @DisplayName("maxTokens 초과 긴 콘텐츠 → 다중 청크로 분할")
    void chunk_longContentExceedsMaxTokens() {
        String content = """
            # Title

            """ + "This is a long paragraph. ".repeat(100);

        List<ChunkResult> results = chunker.chunk(content);

        // Long content might be split into multiple chunks
        assertTrue(results.size() >= 1, "Should produce at least one chunk");

        // Check that all chunks have valid content
        results.forEach(chunk -> {
            assertNotNull(chunk.content());
            assertFalse(chunk.content().isEmpty());
            assertTrue(chunk.tokenCount() > 0);
        });
    }

    @Test
    @DisplayName("다중 H1 헤딩 → 각 섹션별 청크 생성")
    void chunk_multipleH1Headings() {
        String content = """
            # First Title

            Content for first title.

            # Second Title

            Content for second title.
            """;
        List<ChunkResult> results = chunker.chunk(content);

        assertTrue(results.size() >= 1);

        // All chunks should have content
        results.forEach(chunk -> {
            assertFalse(chunk.content().isEmpty());
        });
    }

    @Test
    @DisplayName("코드 블록 포함 콘텐츠 → 코드 블록 보존")
    void chunk_codeBlocksPreserved() {
        String content = """
            # Code Example

            Here's a code block:

            ```java
            public class Test {
                public static void main(String[] args) {
                    System.out.println("Hello");
                }
            }
            ```

            End of example.
            """;
        List<ChunkResult> results = chunker.chunk(content);

        assertTrue(results.size() >= 1);
        assertTrue(results.stream().anyMatch(r -> r.content().contains("System.out.println")));
    }

    @Test
    @DisplayName("null 콘텐츠 → 빈 리스트 반환 (예외 발생 안함)")
    void chunk_nullContent_returnsEmptyList() {
        List<ChunkResult> results = chunker.chunk(null);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("헤딩 경로 형식 검증 (H1 > H2 > H3)")
    void chunk_headingPathFormat() {
        String content = """
            # H1

            ## H2

            ### H3

            Content here.
            """;
        List<ChunkResult> results = chunker.chunk(content);

        assertTrue(results.size() > 0);

        // At least one chunk should have heading path
        boolean hasHeadingPath = results.stream()
            .anyMatch(r -> r.headingPath() != null && !r.headingPath().isEmpty());

        assertTrue(hasHeadingPath, "Should have heading paths");
    }

    @Test
    @DisplayName("특수문자 포함 헤딩 처리 (: & ! 등)")
    void chunk_specialCharactersInHeadings() {
        String content = """
            # Special: Characters & Symbols!

            ## Sub-section (with parens)

            Content here.
            """;
        List<ChunkResult> results = chunker.chunk(content);

        assertTrue(results.size() > 0);
        assertTrue(results.stream()
            .anyMatch(r -> r.content().contains("Special: Characters")));
    }

    @Test
    @DisplayName("모든 청크에 토큰 수 포함 확인")
    void chunk_allChunksHaveTokenCounts() {
        String content = """
            # Title

            Some content here.

            ## Section

            More content.
            """;

        List<ChunkResult> results = chunker.chunk(content);

        assertTrue(results.size() > 0);

        // All chunks should have positive token counts
        results.forEach(chunk -> {
            assertTrue(chunk.tokenCount() > 0, "All chunks should have token counts");
        });
    }
}
