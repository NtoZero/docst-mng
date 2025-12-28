package com.docst.git;

import com.docst.domain.DocumentLink.LinkType;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 링크 파서.
 * 마크다운 문서에서 링크를 추출한다.
 */
@Slf4j
@Component
public class LinkParser {

    // [text](url) 형식의 마크다운 링크
    private static final Pattern MARKDOWN_LINK_PATTERN =
            Pattern.compile("\\[([^\\]]+)\\]\\(([^)]+)\\)");

    // [[page]] 또는 [[page|text]] 형식의 Wiki 링크
    private static final Pattern WIKI_LINK_PATTERN =
            Pattern.compile("\\[\\[([^\\]|]+)(?:\\|([^\\]]+))?\\]\\]");

    // http:// 또는 https://로 시작하는 외부 링크
    private static final Pattern EXTERNAL_LINK_PATTERN =
            Pattern.compile("https?://[^\\s)]+");

    /**
     * 마크다운 문서에서 모든 링크를 추출한다.
     *
     * @param content 문서 내용
     * @return 추출된 링크 목록
     */
    public List<ParsedLink> extractLinks(String content) {
        if (content == null || content.isEmpty()) {
            return List.of();
        }

        List<ParsedLink> links = new ArrayList<>();
        String[] lines = content.split("\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNumber = i + 1;

            // Wiki 링크 추출: [[page]] 또는 [[page|text]]
            Matcher wikiMatcher = WIKI_LINK_PATTERN.matcher(line);
            while (wikiMatcher.find()) {
                String target = wikiMatcher.group(1).trim();
                String anchorText = wikiMatcher.group(2);
                if (anchorText == null) {
                    anchorText = target;
                }
                links.add(new ParsedLink(target, LinkType.WIKI, anchorText.trim(), lineNumber));
            }

            // 마크다운 링크 추출: [text](url)
            Matcher mdMatcher = MARKDOWN_LINK_PATTERN.matcher(line);
            while (mdMatcher.find()) {
                String anchorText = mdMatcher.group(1).trim();
                String url = mdMatcher.group(2).trim();

                // 링크 타입 결정
                LinkType linkType = determineLinkType(url);
                links.add(new ParsedLink(url, linkType, anchorText, lineNumber));
            }
        }

        log.debug("Extracted {} links from content", links.size());
        return links;
    }

    /**
     * URL을 보고 링크 타입을 결정한다.
     *
     * @param url 링크 URL
     * @return 링크 타입
     */
    private LinkType determineLinkType(String url) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return LinkType.EXTERNAL;
        } else if (url.startsWith("#")) {
            return LinkType.ANCHOR;
        } else {
            // 상대 경로는 내부 링크
            return LinkType.INTERNAL;
        }
    }

    /**
     * 파싱된 링크 정보.
     */
    @Getter
    public static class ParsedLink {
        /**
         * 링크 텍스트 (원본 URL 또는 페이지 이름).
         */
        private final String linkText;

        /**
         * 링크 타입.
         */
        private final LinkType linkType;

        /**
         * 앵커 텍스트 (표시 텍스트).
         */
        private final String anchorText;

        /**
         * 라인 번호.
         */
        private final int lineNumber;

        public ParsedLink(String linkText, LinkType linkType, String anchorText, int lineNumber) {
            this.linkText = linkText;
            this.linkType = linkType;
            this.anchorText = anchorText;
            this.lineNumber = lineNumber;
        }

        @Override
        public String toString() {
            return String.format("[%s](%s) at line %d", anchorText, linkText, lineNumber);
        }
    }
}
