package com.docst.git;

import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DocumentParser {

    private final Parser parser;

    public DocumentParser() {
        MutableDataSet options = new MutableDataSet();
        this.parser = Parser.builder(options).build();
    }

    public ParsedDocument parse(String content) {
        Node document = parser.parse(content);

        String title = extractTitle(document, content);
        List<String> headings = extractHeadings(document);
        List<Section> sections = extractSections(document);

        return new ParsedDocument(title, headings, sections, content);
    }

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

    public record ParsedDocument(
            String title,
            List<String> headings,
            List<Section> sections,
            String rawContent
    ) {}

    public record Section(
            String heading,
            int level,
            String content,
            int startLine
    ) {}
}
