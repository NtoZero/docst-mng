package com.docst.chunking;

import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.ast.Paragraph;
import com.vladsch.flexmark.util.sequence.BasedSequence;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * Markdown 청킹 처리기.
 * Flexmark AST를 활용하여 Markdown 문서를 헤딩 기반으로 청크로 분할한다.
 */
@Component
@RequiredArgsConstructor
public class MarkdownChunker {

    private final TokenCounter tokenCounter;
    private final ChunkingConfig config;
    private final Parser markdownParser = Parser.builder().build();

    /**
     * Markdown 텍스트를 청크로 분할한다.
     *
     * @param markdown Markdown 텍스트
     * @return 청크 목록
     */
    public List<ChunkResult> chunk(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return List.of();
        }

        Document document = markdownParser.parse(markdown);
        List<ChunkResult> chunks = new ArrayList<>();
        Stack<String> headingStack = new Stack<>();
        StringBuilder currentChunk = new StringBuilder();
        String currentHeadingPath = "";

        for (Node node : document.getChildren()) {
            if (node instanceof Heading heading) {
                // 현재 청크가 있으면 저장
                if (currentChunk.length() > 0) {
                    processChunk(currentChunk.toString(), currentHeadingPath, chunks);
                    currentChunk.setLength(0);
                }

                // 헤딩 스택 업데이트
                updateHeadingStack(headingStack, heading);
                currentHeadingPath = buildHeadingPath(headingStack);

                // 헤딩 텍스트 추가
                currentChunk.append(node.getChars()).append("\n\n");

            } else {
                // 내용 추가
                currentChunk.append(node.getChars()).append("\n\n");

                // 최대 토큰 수 초과 시 청크 분할
                int tokenCount = tokenCounter.countTokens(currentChunk.toString());
                if (tokenCount >= config.getMaxTokens()) {
                    processChunk(currentChunk.toString(), currentHeadingPath, chunks);
                    currentChunk.setLength(0);

                    // 오버랩 적용: 마지막 청크의 끝부분을 다음 청크 시작에 포함
                    if (!chunks.isEmpty()) {
                        String lastChunk = chunks.get(chunks.size() - 1).content();
                        String overlap = getOverlap(lastChunk);
                        currentChunk.append(overlap);
                    }
                }
            }
        }

        // 마지막 청크 처리
        if (currentChunk.length() > 0) {
            processChunk(currentChunk.toString(), currentHeadingPath, chunks);
        }

        return chunks;
    }

    /**
     * 헤딩 스택을 업데이트한다.
     *
     * @param stack 헤딩 스택
     * @param heading 현재 헤딩
     */
    private void updateHeadingStack(Stack<String> stack, Heading heading) {
        int level = heading.getLevel();
        String headingText = heading.getText().toString();

        // 현재 레벨보다 같거나 높은 레벨의 헤딩 제거
        while (!stack.isEmpty() && getLevel(stack.peek()) >= level) {
            stack.pop();
        }

        // 새 헤딩 추가
        stack.push(formatHeading(level, headingText));
    }

    /**
     * 헤딩 경로를 구성한다.
     *
     * @param stack 헤딩 스택
     * @return 헤딩 경로 (예: "# Title > ## Section")
     */
    private String buildHeadingPath(Stack<String> stack) {
        if (stack.isEmpty()) {
            return "";
        }
        return String.join(config.getHeadingPathSeparator(), stack);
    }

    /**
     * 헤딩 레벨을 추출한다.
     *
     * @param heading 헤딩 문자열 (예: "# Title")
     * @return 레벨 (1-6)
     */
    private int getLevel(String heading) {
        int level = 0;
        for (char c : heading.toCharArray()) {
            if (c == '#') {
                level++;
            } else {
                break;
            }
        }
        return level;
    }

    /**
     * 헤딩을 포맷팅한다.
     *
     * @param level 레벨
     * @param text 헤딩 텍스트
     * @return 포맷팅된 헤딩 (예: "# Title")
     */
    private String formatHeading(int level, String text) {
        return "#".repeat(level) + " " + text;
    }

    /**
     * 청크를 처리하여 결과 리스트에 추가한다.
     *
     * @param content 청크 내용
     * @param headingPath 헤딩 경로
     * @param chunks 결과 리스트
     */
    private void processChunk(String content, String headingPath, List<ChunkResult> chunks) {
        String trimmedContent = content.trim();
        if (trimmedContent.isEmpty()) {
            return;
        }

        int tokenCount = tokenCounter.countTokens(trimmedContent);

        // 최소 토큰 수 미만이고 이전 청크가 있으면 병합 고려
        if (tokenCount < config.getMinTokens() && !chunks.isEmpty()) {
            ChunkResult lastChunk = chunks.get(chunks.size() - 1);
            String mergedContent = lastChunk.content() + "\n\n" + trimmedContent;
            int mergedTokenCount = tokenCounter.countTokens(mergedContent);

            // 병합해도 최대 토큰 수를 넘지 않으면 병합
            if (mergedTokenCount <= config.getMaxTokens()) {
                chunks.set(chunks.size() - 1, new ChunkResult(
                        mergedContent,
                        lastChunk.headingPath(),
                        mergedTokenCount
                ));
                return;
            }
        }

        // 새 청크로 추가
        chunks.add(new ChunkResult(trimmedContent, headingPath, tokenCount));
    }

    /**
     * 마지막 청크에서 오버랩할 텍스트를 추출한다.
     *
     * @param lastChunk 마지막 청크
     * @return 오버랩 텍스트
     */
    private String getOverlap(String lastChunk) {
        int overlapTokens = config.getOverlapTokens();
        if (overlapTokens <= 0) {
            return "";
        }

        // 마지막 문단들을 가져와서 오버랩 토큰 수에 맞춤
        String[] paragraphs = lastChunk.split("\n\n");
        StringBuilder overlap = new StringBuilder();

        for (int i = paragraphs.length - 1; i >= 0; i--) {
            String candidate = paragraphs[i] + "\n\n" + overlap;
            int tokenCount = tokenCounter.countTokens(candidate);

            if (tokenCount <= overlapTokens) {
                overlap.insert(0, paragraphs[i] + "\n\n");
            } else {
                break;
            }
        }

        return overlap.toString().trim();
    }
}
