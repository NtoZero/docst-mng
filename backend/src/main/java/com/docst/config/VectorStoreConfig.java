package com.docst.config;

import org.springframework.context.annotation.Configuration;

/**
 * VectorStore 설정.
 *
 * Phase 6 보완:
 * - 정적 VectorStore 빈 제거
 * - 동적 생성으로 전환 (SemanticSearchService에서 처리)
 * - Placeholder EmbeddingModel 제거
 *
 * VectorStore는 이제 SemanticSearchService.createVectorStore()에서
 * 프로젝트별로 동적 생성됩니다:
 * - PgVectorDataSourceManager: 동적 JdbcTemplate 제공
 * - DynamicEmbeddingClientFactory: 프로젝트별 EmbeddingModel 생성
 * - SystemConfigService: pgvector 설정 관리
 *
 * 스키마 초기화는 Flyway 마이그레이션으로 이동되었습니다.
 */
@Configuration
public class VectorStoreConfig {
    // 모든 정적 빈 제거 - 동적 생성으로 전환
}
