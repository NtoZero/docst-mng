package com.docst.admin.api;

import com.docst.admin.service.PgVectorDataSourceManager;
import com.docst.search.service.SemanticSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PgVector 관리 API (ADMIN 권한).
 * PgVector DataSource 연결 테스트 및 캐시 관리를 제공한다.
 */
@RestController
@RequestMapping("/api/admin/pgvector")
@RequiredArgsConstructor
@Slf4j
public class AdminPgVectorController {

    private final PgVectorDataSourceManager dataSourceManager;
    private final SemanticSearchService semanticSearchService;

    /**
     * PgVector 연결 테스트.
     *
     * @return 연결 테스트 결과
     */
    @PostMapping("/test-connection")
    public ResponseEntity<ConnectionTestResponse> testConnection() {
        log.info("Testing PgVector connection");

        PgVectorDataSourceManager.ConnectionTestResult result = dataSourceManager.testConnection();

        return ResponseEntity.ok(new ConnectionTestResponse(
            result.success(),
            result.message()
        ));
    }

    /**
     * PgVector 캐시 새로고침.
     * DataSource 및 모든 VectorStore 캐시를 무효화하고 재생성한다.
     *
     * @return 성공 여부
     */
    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh() {
        log.info("Refreshing PgVector connection and VectorStore caches");

        try {
            // DataSource 캐시 새로고침
            dataSourceManager.refreshConnection();

            // 모든 VectorStore 캐시 무효화
            semanticSearchService.invalidateAllVectorStores();

            return ResponseEntity.ok(new RefreshResponse(true, "PgVector connection and caches refreshed"));
        } catch (Exception e) {
            log.error("Failed to refresh PgVector connection", e);
            return ResponseEntity.ok(new RefreshResponse(false, "Refresh failed: " + e.getMessage()));
        }
    }

    // ============================================================
    // DTOs
    // ============================================================

    public record ConnectionTestResponse(
        boolean success,
        String message
    ) {}

    public record RefreshResponse(
        boolean success,
        String message
    ) {}
}
