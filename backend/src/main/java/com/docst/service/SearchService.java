package com.docst.service;

import com.docst.domain.Document;
import com.docst.domain.DocumentVersion;
import com.docst.repository.DocumentRepository;
import com.docst.repository.DocumentVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SearchService {

    private final DocumentVersionRepository documentVersionRepository;
    private final DocumentRepository documentRepository;

    public SearchService(DocumentVersionRepository documentVersionRepository,
                          DocumentRepository documentRepository) {
        this.documentVersionRepository = documentVersionRepository;
        this.documentRepository = documentRepository;
    }

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

    private String highlightSnippet(String snippet, String query) {
        return snippet.replaceAll("(?i)(" + java.util.regex.Pattern.quote(query) + ")", "**$1**");
    }

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
