package com.docst.rag.hybrid;

/**
 * 융합 전략 파라미터.
 *
 * @param rrfK RRF 상수 (기본값 60)
 * @param vectorWeight 벡터 검색 가중치 (WeightedSum용)
 * @param graphWeight 그래프 검색 가중치 (WeightedSum용)
 * @param topK 반환할 최대 결과 수
 */
public record FusionParams(
    int rrfK,
    double vectorWeight,
    double graphWeight,
    int topK
) {
    public static final int DEFAULT_RRF_K = 60;
    public static final double DEFAULT_VECTOR_WEIGHT = 0.6;
    public static final double DEFAULT_GRAPH_WEIGHT = 0.4;
    public static final int DEFAULT_TOP_K = 10;

    /**
     * 기본 파라미터 생성.
     */
    public static FusionParams defaults() {
        return new FusionParams(
            DEFAULT_RRF_K,
            DEFAULT_VECTOR_WEIGHT,
            DEFAULT_GRAPH_WEIGHT,
            DEFAULT_TOP_K
        );
    }

    /**
     * RRF 전용 파라미터 생성.
     */
    public static FusionParams forRrf(int rrfK, int topK) {
        return new FusionParams(rrfK, DEFAULT_VECTOR_WEIGHT, DEFAULT_GRAPH_WEIGHT, topK);
    }

    /**
     * WeightedSum 전용 파라미터 생성.
     */
    public static FusionParams forWeightedSum(double vectorWeight, double graphWeight, int topK) {
        return new FusionParams(DEFAULT_RRF_K, vectorWeight, graphWeight, topK);
    }
}
