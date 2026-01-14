package com.docst.admin.api;

import com.docst.admin.service.Neo4jDriverManager;
import com.docst.admin.service.SystemConfigService;
import com.docst.api.ApiModels;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 시스템 헬스체크 API (ADMIN 권한).
 * 데이터베이스 및 외부 서비스 연결 상태를 확인한다.
 */
@RestController
@RequestMapping("/api/admin/health")
@RequiredArgsConstructor
@Slf4j
public class AdminHealthController {

    private final JdbcTemplate jdbcTemplate;
    private final SystemConfigService systemConfigService;
    private final Neo4jDriverManager neo4jDriverManager;

    /**
     * 전체 시스템 헬스 체크.
     *
     * @return 헬스 체크 결과
     */
    @GetMapping
    public ResponseEntity<ApiModels.HealthCheckResponse> healthCheck() {
        log.debug("Performing system health check");

        Map<String, ApiModels.ServiceHealth> services = new HashMap<>();

        // PostgreSQL 체크
        services.put("postgresql", checkPostgreSQL());

        // PgVector 체크
        services.put("pgvector", checkPgVector());

        // Neo4j 체크 (활성화된 경우에만)
        boolean neo4jEnabled = systemConfigService.getBoolean(SystemConfigService.NEO4J_ENABLED, false);
        if (neo4jEnabled) {
            services.put("neo4j", checkNeo4j());
        }

        // 전체 상태 결정 (모든 서비스가 UP이면 UP)
        boolean allUp = services.values().stream()
                .allMatch(s -> "UP".equals(s.status()));

        return ResponseEntity.ok(new ApiModels.HealthCheckResponse(
                allUp ? "UP" : "DEGRADED",
                LocalDateTime.now(),
                services
        ));
    }

    /**
     * PostgreSQL 데이터베이스 연결 체크.
     *
     * @return 서비스 헬스 상태
     */
    private ApiModels.ServiceHealth checkPostgreSQL() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);

            // 버전 정보 조회
            String version = jdbcTemplate.queryForObject("SELECT version()", String.class);

            Map<String, Object> details = new HashMap<>();
            details.put("version", version);
            details.put("host", systemConfigService.getString("postgresql.host", "localhost"));
            details.put("port", systemConfigService.getInt("postgresql.port", 5434));
            details.put("database", systemConfigService.getString("postgresql.database", "docst"));

            return new ApiModels.ServiceHealth("UP", "PostgreSQL connection is healthy", details);
        } catch (Exception e) {
            log.error("PostgreSQL health check failed", e);
            Map<String, Object> details = new HashMap<>();
            details.put("error", e.getMessage());
            return new ApiModels.ServiceHealth("DOWN", "PostgreSQL connection failed", details);
        }
    }

    /**
     * PgVector 확장 기능 체크.
     *
     * @return 서비스 헬스 상태
     */
    private ApiModels.ServiceHealth checkPgVector() {
        try {
            // pgvector 확장 설치 여부 확인
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_extension WHERE extname = 'vector'",
                    Integer.class
            );

            if (count != null && count > 0) {
                // vector_store 테이블 존재 여부 확인
                Integer tableCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'vector_store'",
                        Integer.class
                );

                Map<String, Object> details = new HashMap<>();
                details.put("extension_installed", true);
                details.put("vector_store_exists", tableCount != null && tableCount > 0);
                details.put("schema", systemConfigService.getString("postgresql.schema", "public"));

                return new ApiModels.ServiceHealth("UP", "PgVector extension is available", details);
            } else {
                Map<String, Object> details = new HashMap<>();
                details.put("extension_installed", false);
                return new ApiModels.ServiceHealth("DOWN", "PgVector extension is not installed", details);
            }
        } catch (Exception e) {
            log.error("PgVector health check failed", e);
            Map<String, Object> details = new HashMap<>();
            details.put("error", e.getMessage());
            return new ApiModels.ServiceHealth("DOWN", "PgVector check failed", details);
        }
    }

    /**
     * Neo4j 연결 체크.
     *
     * @return 서비스 헬스 상태
     */
    private ApiModels.ServiceHealth checkNeo4j() {
        Map<String, Object> details = new HashMap<>();
        String uri = systemConfigService.getString(SystemConfigService.NEO4J_URI, "");
        details.put("uri", uri);
        details.put("enabled", true);

        // 캐싱된 Driver 가져오기 (또는 새로 생성)
        Driver driver = neo4jDriverManager.getOrCreateDriver();

        if (driver == null) {
            return new ApiModels.ServiceHealth(
                    "DOWN",
                    "Neo4j driver not initialized (check credentials and URI)",
                    details
            );
        }

        try {
            // 간단한 쿼리로 연결 테스트
            try (Session session = driver.session()) {
                var result = session.run("RETURN 1 AS num");
                if (result.hasNext()) {
                    var record = result.next();
                    int num = record.get("num").asInt();
                    if (num == 1) {
                        // Neo4j 버전 정보 조회
                        var versionResult = session.run("CALL dbms.components() YIELD name, versions, edition");
                        if (versionResult.hasNext()) {
                            var versionRecord = versionResult.next();
                            String version = versionRecord.get("versions").asList().toString();
                            String edition = versionRecord.get("edition").asString();
                            details.put("version", version);
                            details.put("edition", edition);
                        }

                        return new ApiModels.ServiceHealth(
                                "UP",
                                "Neo4j connection is healthy",
                                details
                        );
                    }
                }
            }

            return new ApiModels.ServiceHealth(
                    "DOWN",
                    "Neo4j query returned unexpected result",
                    details
            );
        } catch (Exception e) {
            log.error("Neo4j health check failed", e);
            details.put("error", e.getMessage());
            return new ApiModels.ServiceHealth(
                    "DOWN",
                    "Neo4j connection failed: " + e.getMessage(),
                    details
            );
        }
    }
}
