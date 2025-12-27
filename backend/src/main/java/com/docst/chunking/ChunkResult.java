package com.docst.chunking;

/**
 * 청킹 결과.
 *
 * @param content 청크 내용
 * @param headingPath 헤딩 경로 (예: "# Title > ## Section")
 * @param tokenCount 토큰 수
 */
public record ChunkResult(
        String content,
        String headingPath,
        int tokenCount
) {
}
