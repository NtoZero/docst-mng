package com.docst.service;

import com.docst.domain.SystemConfig;
import com.docst.repository.SystemConfigRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 시스템 설정 서비스.
 * 외부 서비스 URL, 기본값 등 시스템 전역 설정을 관리한다.
 * 5분 캐싱으로 DB 부하 감소.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SystemConfigService {

    private final SystemConfigRepository repository;
    private final ConcurrentHashMap<String, CachedValue> cache = new ConcurrentHashMap<>();
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    // ============================================================
    // 설정 키 상수
    // ============================================================

    // Neo4j
    public static final String NEO4J_URI = "neo4j.uri";
    public static final String NEO4J_ENABLED = "neo4j.enabled";
    public static final String NEO4J_MAX_HOP = "neo4j.max-hop";
    public static final String NEO4J_ENTITY_EXTRACTION_MODEL = "neo4j.entity-extraction-model";

    // Ollama
    public static final String OLLAMA_BASE_URL = "ollama.base-url";
    public static final String OLLAMA_ENABLED = "ollama.enabled";

    // Embedding Defaults
    public static final String EMBEDDING_DEFAULT_PROVIDER = "embedding.default-provider";
    public static final String EMBEDDING_DEFAULT_MODEL = "embedding.default-model";
    public static final String EMBEDDING_DEFAULT_DIMENSIONS = "embedding.default-dimensions";

    // RAG - PgVector
    public static final String RAG_PGVECTOR_ENABLED = "rag.pgvector.enabled";
    public static final String RAG_PGVECTOR_SIMILARITY_THRESHOLD = "rag.pgvector.similarity-threshold";

    // RAG - Hybrid
    public static final String RAG_HYBRID_FUSION_STRATEGY = "rag.hybrid.fusion-strategy";
    public static final String RAG_HYBRID_RRF_K = "rag.hybrid.rrf-k";
    public static final String RAG_HYBRID_VECTOR_WEIGHT = "rag.hybrid.vector-weight";
    public static final String RAG_HYBRID_GRAPH_WEIGHT = "rag.hybrid.graph-weight";

    /**
     * 애플리케이션 시작 시 캐시 워밍업.
     */
    @PostConstruct
    public void init() {
        log.info("SystemConfigService initialized");
    }

    // ============================================================
    // 조회 (캐싱)
    // ============================================================

    /**
     * 문자열 설정 조회.
     *
     * @param key 설정 키
     * @return 설정 값 (없으면 null)
     */
    public String getString(String key) {
        return getCached(key).orElse(null);
    }

    /**
     * 문자열 설정 조회 (기본값 포함).
     *
     * @param key 설정 키
     * @param defaultValue 기본값
     * @return 설정 값
     */
    public String getString(String key, String defaultValue) {
        return getCached(key).orElse(defaultValue);
    }

    /**
     * 불린 설정 조회.
     *
     * @param key 설정 키
     * @param defaultValue 기본값
     * @return 설정 값
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        return getCached(key)
                .map(val -> "true".equalsIgnoreCase(val) || "1".equals(val))
                .orElse(defaultValue);
    }

    /**
     * 정수 설정 조회.
     *
     * @param key 설정 키
     * @param defaultValue 기본값
     * @return 설정 값
     */
    public int getInt(String key, int defaultValue) {
        return getCached(key)
                .map(val -> {
                    try {
                        return Integer.parseInt(val);
                    } catch (NumberFormatException e) {
                        log.warn("Failed to parse integer for key {}: {}", key, val);
                        return defaultValue;
                    }
                })
                .orElse(defaultValue);
    }

    /**
     * 실수 설정 조회.
     *
     * @param key 설정 키
     * @param defaultValue 기본값
     * @return 설정 값
     */
    public double getDouble(String key, double defaultValue) {
        return getCached(key)
                .map(val -> {
                    try {
                        return Double.parseDouble(val);
                    } catch (NumberFormatException e) {
                        log.warn("Failed to parse double for key {}: {}", key, val);
                        return defaultValue;
                    }
                })
                .orElse(defaultValue);
    }

    // ============================================================
    // 수정
    // ============================================================

    /**
     * 설정 업데이트.
     *
     * @param key 설정 키
     * @param value 설정 값
     */
    @Transactional
    public void setConfig(String key, String value) {
        SystemConfig config = repository.findByConfigKey(key)
                .orElseGet(() -> new SystemConfig(key));
        config.setConfigValue(value);
        config.setUpdatedAt(Instant.now());
        repository.save(config);

        cache.remove(key);  // 캐시 무효화
        log.info("System config updated: {} = {}", key, value);
    }

    /**
     * 설정 업데이트 (타입 포함).
     *
     * @param key 설정 키
     * @param value 설정 값
     * @param type 설정 타입
     */
    @Transactional
    public void setConfig(String key, String value, String type) {
        SystemConfig config = repository.findByConfigKey(key)
                .orElseGet(() -> new SystemConfig(key));
        config.setConfigValue(value);
        config.setConfigType(type);
        config.setUpdatedAt(Instant.now());
        repository.save(config);

        cache.remove(key);
        log.info("System config updated: {} = {} (type: {})", key, value, type);
    }

    /**
     * 모든 설정 조회.
     *
     * @return 설정 목록
     */
    public List<SystemConfig> getAllConfigs() {
        return repository.findAll();
    }

    /**
     * 설정 조회 (엔티티).
     *
     * @param key 설정 키
     * @return 설정
     */
    public Optional<SystemConfig> getConfig(String key) {
        return repository.findByConfigKey(key);
    }

    /**
     * 캐시 전체 삭제.
     */
    public void refreshCache() {
        cache.clear();
        log.info("System config cache cleared");
    }

    // ============================================================
    // 내부 메서드
    // ============================================================

    private Optional<String> getCached(String key) {
        CachedValue cached = cache.get(key);
        if (cached != null && !cached.isExpired()) {
            return Optional.ofNullable(cached.value);
        }

        return repository.findByConfigKey(key)
                .map(config -> {
                    cache.put(key, new CachedValue(config.getConfigValue()));
                    return config.getConfigValue();
                });
    }

    /**
     * 캐시 엔트리.
     */
    private record CachedValue(String value, Instant cachedAt) {
        CachedValue(String value) {
            this(value, Instant.now());
        }

        boolean isExpired() {
            return Instant.now().isAfter(cachedAt.plus(CACHE_TTL));
        }
    }
}
