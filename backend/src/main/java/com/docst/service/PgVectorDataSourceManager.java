package com.docst.service;

import com.docst.domain.Credential.CredentialType;
import com.docst.domain.CredentialScope;
import com.docst.repository.CredentialRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * PgVector 동적 DataSource 관리.
 * Neo4jDriverManager 패턴과 동일한 구조로 구현.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PgVectorDataSourceManager {

    private final SystemConfigService systemConfigService;
    private final DynamicCredentialResolver credentialResolver;
    private final CredentialRepository credentialRepository;
    private final ObjectMapper objectMapper;

    // 캐싱된 DataSource 및 JdbcTemplate
    private volatile HikariDataSource cachedDataSource;
    private volatile JdbcTemplate cachedJdbcTemplate;
    private volatile String cachedCredentialId;
    private volatile String cachedConnectionString;

    // ============================================================
    // Public API
    // ============================================================

    /**
     * JdbcTemplate 가져오기 (캐싱).
     * 설정 또는 크리덴셜 변경 시 자동 재생성.
     *
     * @return JdbcTemplate (설정 없으면 null)
     */
    public JdbcTemplate getOrCreateJdbcTemplate() {
        if (!isEnabled()) {
            closeDataSourceIfExists();
            return null;
        }

        String connectionString = buildConnectionString();
        String currentCredentialId = getCurrentCredentialId();

        if (connectionString == null || currentCredentialId == null) {
            closeDataSourceIfExists();
            return null;
        }

        // 캐시 유효성 검사
        if (cachedJdbcTemplate != null
            && Objects.equals(cachedConnectionString, connectionString)
            && Objects.equals(cachedCredentialId, currentCredentialId)) {
            return cachedJdbcTemplate;
        }

        // 설정 변경 시 재생성
        synchronized (this) {
            if (cachedJdbcTemplate == null
                || !Objects.equals(cachedConnectionString, connectionString)
                || !Objects.equals(cachedCredentialId, currentCredentialId)) {

                closeDataSourceIfExists();

                try {
                    cachedDataSource = createDataSource(connectionString, currentCredentialId);
                    cachedJdbcTemplate = new JdbcTemplate(cachedDataSource);
                    cachedConnectionString = connectionString;
                    cachedCredentialId = currentCredentialId;

                    log.info("PgVector DataSource created: {}", connectionString);
                } catch (Exception e) {
                    log.error("Failed to create PgVector DataSource", e);
                    return null;
                }
            }
        }

        return cachedJdbcTemplate;
    }

    /**
     * 연결 테스트.
     *
     * @return 연결 성공 여부
     */
    public ConnectionTestResult testConnection() {
        try {
            JdbcTemplate jdbcTemplate = getOrCreateJdbcTemplate();
            if (jdbcTemplate == null) {
                return new ConnectionTestResult(false, "PgVector is not configured");
            }

            // 간단한 쿼리로 연결 테스트
            String version = jdbcTemplate.queryForObject(
                "SELECT version()", String.class);

            // pgvector 확장 확인
            Boolean hasVector = jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM pg_extension WHERE extname = 'vector')",
                Boolean.class);

            if (Boolean.TRUE.equals(hasVector)) {
                return new ConnectionTestResult(true, "Connected: " + version);
            } else {
                return new ConnectionTestResult(false, "pgvector extension not installed");
            }
        } catch (Exception e) {
            return new ConnectionTestResult(false, "Connection failed: " + e.getMessage());
        }
    }

    /**
     * 캐시 강제 새로고침.
     */
    public void refreshConnection() {
        synchronized (this) {
            closeDataSourceIfExists();
        }
        getOrCreateJdbcTemplate();
    }

    // ============================================================
    // Public Config Methods
    // ============================================================

    /**
     * PgVector 활성화 여부.
     */
    public boolean isEnabled() {
        return systemConfigService.getBoolean(SystemConfigService.PGVECTOR_ENABLED, false);
    }

    /**
     * 벡터 차원 수.
     */
    public int getDimensions() {
        return systemConfigService.getInt(SystemConfigService.PGVECTOR_DIMENSIONS, 1536);
    }

    /**
     * 스키마 이름.
     */
    public String getSchemaName() {
        return systemConfigService.getString(SystemConfigService.PGVECTOR_SCHEMA, "public");
    }

    /**
     * 테이블 이름.
     */
    public String getTableName() {
        return systemConfigService.getString(SystemConfigService.PGVECTOR_TABLE, "vector_store");
    }

    // ============================================================
    // Internal Methods
    // ============================================================

    private String buildConnectionString() {
        String host = systemConfigService.getString(SystemConfigService.PGVECTOR_HOST, "localhost");
        int port = systemConfigService.getInt(SystemConfigService.PGVECTOR_PORT, 5432);
        String database = systemConfigService.getString(SystemConfigService.PGVECTOR_DATABASE, "docst_vector");

        if (host == null || host.isBlank() || database == null || database.isBlank()) {
            return null;
        }

        return String.format("jdbc:postgresql://%s:%d/%s", host, port, database);
    }

    private String getCurrentCredentialId() {
        return credentialRepository
            .findByScopeAndTypeAndActiveTrue(CredentialScope.SYSTEM, CredentialType.PGVECTOR_AUTH)
            .map(c -> c.getId().toString())
            .orElse(null);
    }

    private HikariDataSource createDataSource(String connectionString, String credentialId) {
        PgVectorAuth auth = getCredentialAuth();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(connectionString);
        config.setUsername(auth.username());
        config.setPassword(auth.password());
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setPoolName("pgvector-pool");

        return new HikariDataSource(config);
    }

    private PgVectorAuth getCredentialAuth() {
        String authJson = credentialResolver
            .resolveSystemApiKey(CredentialType.PGVECTOR_AUTH)
            .orElseThrow(() -> new IllegalStateException("PGVECTOR_AUTH credential not found"));

        try {
            return objectMapper.readValue(authJson, PgVectorAuth.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse PGVECTOR_AUTH", e);
        }
    }

    private void closeDataSourceIfExists() {
        if (cachedDataSource != null) {
            try {
                cachedDataSource.close();
            } catch (Exception e) {
                log.warn("Failed to close PgVector DataSource", e);
            }
            cachedDataSource = null;
            cachedJdbcTemplate = null;
            cachedConnectionString = null;
            cachedCredentialId = null;
        }
    }

    // ============================================================
    // DTOs
    // ============================================================

    public record ConnectionTestResult(boolean success, String message) {}
    private record PgVectorAuth(String username, String password) {}
}
