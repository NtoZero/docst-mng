package com.docst.service;

import com.docst.domain.Document;
import com.docst.domain.DocumentVersion;
import com.docst.repository.DocumentRepository;
import com.docst.repository.DocumentVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 검색 서비스.
 * 문서 검색 기능을 제공한다.
 * Phase 1에서는 키워드 검색을, Phase 2에서는 시맨틱 검색을 지원할 예정이다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SearchService {

    private final DocumentVersionRepository documentVersionRepository;
    private final DocumentRepository documentRepository;

    /**
     * 키워드로 문서를 검색한다.
     * ILIKE 쿼리를 사용하여 문서 내용에서 키워드를 검색한다.
     *
     * @param projectId 프로젝트 ID
     * @param query 검색 키워드
     * @param topK 결과 개수 제한
     * @return 검색 결과 목록
     */
    public List<SearchResult> searchByKeyword(UUID projectId, String query, int topK) {
        List<DocumentVersion> versions = documentVersionRepository.searchByKeyword(projectId, query, topK);

        return versions.stream()
                .map(version -> {
                    Document doc = documentRepository.findById(version.getDocument().getId()).orElse(null);
                    String snippet = buildSnippet(version.getContent(), query);
                    return new SearchResult(
                            version.getDocument().getId(),
                            doc != null ? doc.getRepository().getId() : null,
                            doc != null ? doc.getPath() : null,
                            version.getCommitSha(),
                            null, // chunkId (Phase 2)
                            0.9,  // score placeholder
                            snippet,
                            highlightSnippet(snippet, query)
                    );
                })
                .toList();
    }

    /**
     * 검색어 주변의 스니펫을 생성한다.
     *
     * @param content 문서 내용
     * @param query 검색어
     * @return 스니펫 문자열
     */
    private String buildSnippet(String content, String query) {
        if (content == null) {
            return "";
        }
        int index = content.toLowerCase().indexOf(query.toLowerCase());
        if (index < 0) {
            return content.substring(0, Math.min(content.length(), 200));
        }
        int start = Math.max(0, index - 50);
        int end = Math.min(content.length(), index + query.length() + 150);
        String snippet = content.substring(start, end);
        if (start > 0) snippet = "..." + snippet;
        if (end < content.length()) snippet = snippet + "...";
        return snippet;
    }

    /**
     * 스니펫에서 검색어를 하이라이트한다.
     *
     * @param snippet 스니펫 문자열
     * @param query 검색어
     * @return 하이라이트된 스니펫 (마크다운 볼드)
     */
    private String highlightSnippet(String snippet, String query) {
        return snippet.replaceAll("(?i)(" + java.util.regex.Pattern.quote(query) + ")", "**$1**");
    }

    /**
     * 검색 결과를 나타내는 레코드.
     *
     * @param documentId 문서 ID
     * @param repositoryId 레포지토리 ID
     * @param path 파일 경로
     * @param commitSha 커밋 SHA
     * @param chunkId 청크 ID (Phase 2에서 사용)
     * @param score 관련도 점수
     * @param snippet 스니펫
     * @param highlightedSnippet 하이라이트된 스니펫
     */
    public record SearchResult(
            UUID documentId,
            UUID repositoryId,
            String path,
            String commitSha,
            UUID chunkId,
            double score,
            String snippet,
            String highlightedSnippet
    ) {}
}
