package com.docst.rag.hybrid;

import com.docst.service.SearchService.SearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * RRF (Reciprocal Rank Fusion) 융합 전략.
 * Phase 4-D: 동적 rrfK 파라미터 지원.
 *
 * RRF 알고리즘:
 * score(d) = Σ 1 / (k + rank(d, list))
 */
@Slf4j
@Component
public class RrfFusionStrategy implements FusionStrategy {

    @Override
    public List<SearchResult> fuse(
        List<SearchResult> vectorResults,
        List<SearchResult> graphResults,
        FusionParams params
    ) {
        int rrfK = params.rrfK();
        int topK = params.topK();

        log.debug("RRF fusion: vectorResults={}, graphResults={}, rrfK={}, topK={}",
            vectorResults.size(), graphResults.size(), rrfK, topK);

        Map<UUID, RRFResult> rrfScores = new HashMap<>();

        // 벡터/키워드 결과 점수 추가
        for (int i = 0; i < vectorResults.size(); i++) {
            SearchResult result = vectorResults.get(i);
            UUID id = getResultId(result);
            double rrfScore = 1.0 / (rrfK + i + 1);

            rrfScores.merge(id, new RRFResult(id, rrfScore, result),
                (existing, newVal) -> new RRFResult(id, existing.score + newVal.score, result));
        }

        // 그래프 결과 점수 추가
        for (int i = 0; i < graphResults.size(); i++) {
            SearchResult result = graphResults.get(i);
            UUID id = getResultId(result);
            double rrfScore = 1.0 / (rrfK + i + 1);

            rrfScores.merge(id, new RRFResult(id, rrfScore, result),
                (existing, newVal) -> new RRFResult(id, existing.score + newVal.score, existing.result));
        }

        // RRF 점수 기준 정렬 및 상위 topK 반환
        return rrfScores.values().stream()
            .sorted(Comparator.comparingDouble((RRFResult r) -> r.score).reversed())
            .limit(topK)
            .map(r -> new SearchResult(
                r.result.documentId(),
                r.result.repositoryId(),
                r.result.path(),
                r.result.commitSha(),
                r.result.chunkId(),
                r.result.headingPath(),
                r.score,  // RRF 융합 점수
                r.result.snippet(),
                r.result.highlightedSnippet()
            ))
            .toList();
    }

    @Override
    public String getName() {
        return "rrf";
    }

    /**
     * SearchResult에서 고유 ID를 추출한다.
     */
    private UUID getResultId(SearchResult result) {
        return result.chunkId() != null ? result.chunkId() : result.documentId();
    }

    /**
     * RRF 결과를 나타내는 내부 레코드.
     */
    private record RRFResult(
        UUID id,
        double score,
        SearchResult result
    ) {}
}
