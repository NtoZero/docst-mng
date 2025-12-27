package com.docst.chunking;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 청킹 설정.
 * 문서를 청크로 분할하는 규칙을 정의한다.
 */
@Configuration
@ConfigurationProperties(prefix = "docst.chunking")
@Getter
@Setter
public class ChunkingConfig {

    /** 청크 최대 토큰 수 */
    private int maxTokens = 512;

    /** 청크 간 중복 토큰 수 (오버랩) */
    private int overlapTokens = 50;

    /** 청크 최소 토큰 수 (미만 시 이전 청크와 병합 고려) */
    private int minTokens = 100;

    /** 헤딩 경로 구분자 */
    private String headingPathSeparator = " > ";
}
