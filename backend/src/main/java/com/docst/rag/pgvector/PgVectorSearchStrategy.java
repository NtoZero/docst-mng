package com.docst.rag.pgvector;

import com.docst.domain.DocumentVersion;
import com.docst.embedding.DocstEmbeddingService;
import com.docst.rag.RagMode;
import com.docst.rag.RagSearchStrategy;
import com.docst.service.SearchService.SearchResult;
import com.docst.service.SemanticSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * PgVector 기반 벡터 검색 전략.
 * Phase 4: Mode 1 - 기존 SemanticSearchService를 전략 패턴으로 래핑
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "docst.rag.pgvector.enabled", havingValue = "true", matchIfMissing = true)
public class PgVectorSearchStrategy implements RagSearchStrategy {

    private final SemanticSearchService semanticSearchService;
    private final DocstEmbeddingService embeddingService;

    @Override
    public List<SearchResult> search(UUID projectId, String query, int topK) {
        log.debug("PgVector search: projectId={}, query='{}', topK={}", projectId, query, topK);
        return semanticSearchService.searchSemantic(projectId, query, topK);
    }

    @Override
    public void indexDocument(DocumentVersion documentVersion) {
        log.debug("PgVector indexing: documentId={}", documentVersion.getDocument().getId());
        int embeddedCount = embeddingService.embedDocumentVersion(documentVersion);
        log.info("PgVector indexed {} chunks for document '{}'",
            embeddedCount, documentVersion.getDocument().getPath());
    }

    @Override
    public RagMode getSupportedMode() {
        return RagMode.PGVECTOR;
    }
}
