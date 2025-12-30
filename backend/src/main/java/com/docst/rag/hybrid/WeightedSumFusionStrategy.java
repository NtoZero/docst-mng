package com.docst.rag.hybrid;

import com.docst.service.SearchService.SearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 가중 합계 (Weighted Sum) 융합 전략.
 * Phase 4-D: 동적 vectorWeight, graphWeight 파라미터 지원.
 *
 * 알고리즘:
 * score(d) = vectorWeight * normalizedVectorScore(d) + graphWeight * normalizedGraphScore(d)
 */
@Slf4j
@Component
public class WeightedSumFusionStrategy implements FusionStrategy {

    @Override
    public List<SearchResult> fuse(
        List<SearchResult> vectorResults,
        List<SearchResult> graphResults,
        FusionParams params
    ) {
        double vectorWeight = params.vectorWeight();
        double graphWeight = params.graphWeight();
        int topK = params.topK();

        log.debug("WeightedSum fusion: vectorResults={}, graphResults={}, vectorWeight={}, graphWeight={}, topK={}",
            vectorResults.size(), graphResults.size(), vectorWeight, graphWeight, topK);

        // 점수 정규화를 위한 최댓값 계산
        double maxVectorScore = vectorResults.stream()
            .mapToDouble(SearchResult::score)
            .max()
            .orElse(1.0);

        double maxGraphScore = graphResults.stream()
            .mapToDouble(SearchResult::score)
            .max()
            .orElse(1.0);

        Map<UUID, WeightedResult> weightedScores = new HashMap<>();

        // 벡터 결과 점수 추가 (정규화)
        for (SearchResult result : vectorResults) {
            UUID id = getResultId(result);
            double normalizedScore = result.score() / maxVectorScore;
            double weightedScore = vectorWeight * normalizedScore;

            weightedScores.merge(id, new WeightedResult(id, weightedScore, 0.0, result),
                (existing, newVal) -> new WeightedResult(
                    id,
                    existing.vectorScore + newVal.vectorScore,
                    existing.graphScore,
                    result
                ));
        }

        // 그래프 결과 점수 추가 (정규화)
        for (SearchResult result : graphResults) {
            UUID id = getResultId(result);
            double normalizedScore = result.score() / maxGraphScore;
            double weightedScore = graphWeight * normalizedScore;

            weightedScores.merge(id, new WeightedResult(id, 0.0, weightedScore, result),
                (existing, newVal) -> new WeightedResult(
                    id,
                    existing.vectorScore,
                    existing.graphScore + newVal.graphScore,
                    existing.result != null ? existing.result : result
                ));
        }

        // 가중 합계 점수 기준 정렬 및 상위 topK 반환
        return weightedScores.values().stream()
            .sorted(Comparator.comparingDouble((WeightedResult r) -> r.totalScore()).reversed())
            .limit(topK)
            .map(r -> new SearchResult(
                r.result.documentId(),
                r.result.repositoryId(),
                r.result.path(),
                r.result.commitSha(),
                r.result.chunkId(),
                r.result.headingPath(),
                r.totalScore(),  // 가중 합계 융합 점수
                r.result.snippet(),
                r.result.highlightedSnippet()
            ))
            .toList();
    }

    @Override
    public String getName() {
        return "weighted_sum";
    }

    /**
     * SearchResult에서 고유 ID를 추출한다.
     */
    private UUID getResultId(SearchResult result) {
        return result.chunkId() != null ? result.chunkId() : result.documentId();
    }

    /**
     * 가중 합계 결과를 나타내는 내부 레코드.
     */
    private record WeightedResult(
        UUID id,
        double vectorScore,
        double graphScore,
        SearchResult result
    ) {
        double totalScore() {
            return vectorScore + graphScore;
        }
    }
}
