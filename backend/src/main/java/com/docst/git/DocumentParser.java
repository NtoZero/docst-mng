package com.docst.git;

import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 문서 파서.
 * Flexmark를 사용하여 마크다운 문서를 파싱한다.
 */
@Component
public class DocumentParser {

    private final Parser parser;

    /**
     * DocumentParser 생성자.
     * Flexmark 파서를 초기화한다.
     */
    public DocumentParser() {
        MutableDataSet options = new MutableDataSet();
        this.parser = Parser.builder(options).build();
    }

    /**
     * 마크다운 문서를 파싱한다.
     *
     * @param content 마크다운 내용
     * @return 파싱된 문서 정보
     */
    public ParsedDocument parse(String content) {
        Node document = parser.parse(content);

        String title = extractTitle(document, content);
        List<String> headings = extractHeadings(document);
        List<Section> sections = extractSections(document);

        return new ParsedDocument(title, headings, sections, content);
    }

    /**
     * 문서에서 제목을 추출한다.
     * 첫 번째 H1 헤딩을 찾고, 없으면 첫 번째 비어있지 않은 줄을 반환한다.
     */
    private String extractTitle(Node document, String content) {
        // Find first H1 heading
        Node node = document.getFirstChild();
        while (node != null) {
            if (node instanceof Heading heading && heading.getLevel() == 1) {
                return heading.getText().toString();
            }
            node = node.getNext();
        }

        // Fallback: first non-empty line
        String[] lines = content.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                return trimmed.length() > 100 ? trimmed.substring(0, 100) + "..." : trimmed;
            }
        }

        return "Untitled";
    }

    /**
     * 문서의 모든 헤딩을 추출한다.
     */
    private List<String> extractHeadings(Node document) {
        List<String> headings = new ArrayList<>();
        Node node = document.getFirstChild();
        while (node != null) {
            if (node instanceof Heading heading) {
                String prefix = "#".repeat(heading.getLevel());
                headings.add(prefix + " " + heading.getText().toString());
            }
            node = node.getNext();
        }
        return headings;
    }

    /**
     * 문서를 섹션 단위로 분리한다.
     */
    private List<Section> extractSections(Node document) {
        List<Section> sections = new ArrayList<>();
        StringBuilder currentContent = new StringBuilder();
        String currentHeading = null;
        int currentLevel = 0;
        int startLine = 0;

        String[] lines = document.getChars().toString().split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.startsWith("#")) {
                // Save previous section
                if (currentContent.length() > 0 || currentHeading != null) {
                    sections.add(new Section(
                            currentHeading != null ? currentHeading : "",
                            currentLevel,
                            currentContent.toString().trim(),
                            startLine
                    ));
                }

                // Parse new heading
                int level = 0;
                while (level < line.length() && line.charAt(level) == '#') {
                    level++;
                }
                currentHeading = line.substring(level).trim();
                currentLevel = level;
                currentContent = new StringBuilder();
                startLine = i;
            } else {
                currentContent.append(line).append("\n");
            }
        }

        // Don't forget the last section
        if (currentContent.length() > 0 || currentHeading != null) {
            sections.add(new Section(
                    currentHeading != null ? currentHeading : "",
                    currentLevel,
                    currentContent.toString().trim(),
                    startLine
            ));
        }

        return sections;
    }

    /**
     * 파싱된 문서 정보를 나타내는 레코드.
     *
     * @param title 제목
     * @param headings 헤딩 목록
     * @param sections 섹션 목록
     * @param rawContent 원본 내용
     */
    public record ParsedDocument(
            String title,
            List<String> headings,
            List<Section> sections,
            String rawContent
    ) {}

    /**
     * 문서 섹션을 나타내는 레코드.
     *
     * @param heading 헤딩 텍스트
     * @param level 헤딩 레벨 (1-6)
     * @param content 섹션 내용
     * @param startLine 시작 줄 번호
     */
    public record Section(
            String heading,
            int level,
            String content,
            int startLine
    ) {}
}
