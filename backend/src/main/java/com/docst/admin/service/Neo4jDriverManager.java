package com.docst.admin.service;

import com.docst.credential.Credential;
import com.docst.credential.Credential.CredentialType;
import com.docst.credential.repository.CredentialRepository;
import com.docst.credential.CredentialScope;
import com.docst.credential.service.DynamicCredentialResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

/**
 * Neo4j Driver 관리 서비스.
 * Driver를 캐싱하고 크리덴셜 변경 시에만 재생성한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class Neo4jDriverManager {

    private final SystemConfigService systemConfigService;
    private final DynamicCredentialResolver credentialResolver;
    private final CredentialRepository credentialRepository;
    private final ObjectMapper objectMapper;

    // 캐싱된 Driver (thread-safe)
    private volatile Driver cachedDriver;
    private volatile String cachedCredentialId;

    /**
     * Neo4j Driver 가져오기 (캐싱).
     * 크리덴셜이 변경되지 않았으면 캐시된 Driver 반환, 변경되었으면 재생성.
     *
     * @return Neo4j Driver (설정 없으면 null)
     */
    public Driver getOrCreateDriver() {
        // Neo4j 활성화 여부 확인
        boolean enabled = systemConfigService.getBoolean(SystemConfigService.NEO4J_ENABLED, false);
        if (!enabled) {
            closeDriverIfExists();
            return null;
        }

        // URI 확인
        String uri = systemConfigService.getString(SystemConfigService.NEO4J_URI);
        if (uri == null || uri.isBlank()) {
            closeDriverIfExists();
            return null;
        }

        // 현재 활성 크리덴셜 ID 조회
        String currentCredentialId = getCurrentCredentialId();
        if (currentCredentialId == null) {
            closeDriverIfExists();
            return null;
        }

        // 캐시된 Driver가 있고 크리덴셜이 변경되지 않았으면 재사용
        if (cachedDriver != null && Objects.equals(cachedCredentialId, currentCredentialId)) {
            return cachedDriver;
        }

        // 크리덴셜이 변경되었으면 Driver 재생성
        synchronized (this) {
            // Double-check locking
            if (cachedDriver == null || !Objects.equals(cachedCredentialId, currentCredentialId)) {
                if (cachedDriver != null) {
                    log.info("Neo4j credential changed, closing old driver");
                    closeDriver(cachedDriver);
                }

                try {
                    cachedDriver = createDriver(uri, currentCredentialId);
                    cachedCredentialId = currentCredentialId;
                    log.info("Neo4j driver created/refreshed for credential ID: {}", currentCredentialId);
                } catch (Exception e) {
                    log.error("Failed to create Neo4j driver", e);
                    cachedDriver = null;
                    cachedCredentialId = null;
                    return null;
                }
            }
        }

        return cachedDriver;
    }

    /**
     * 캐시된 Driver 강제 새로고침.
     * 크리덴셜 업데이트 후 호출하여 즉시 재연결.
     */
    public void refreshDriver() {
        synchronized (this) {
            if (cachedDriver != null) {
                log.info("Force refreshing Neo4j driver");
                closeDriver(cachedDriver);
                cachedDriver = null;
                cachedCredentialId = null;
            }
        }
        // 다음 호출 시 재생성됨
        getOrCreateDriver();
    }

    /**
     * 현재 활성화된 NEO4J_AUTH 크리덴셜 ID 조회.
     *
     * @return 크리덴셜 ID (없으면 null)
     */
    private String getCurrentCredentialId() {
        Optional<Credential> credentialOpt = credentialRepository
                .findByScopeAndTypeAndActiveTrue(CredentialScope.SYSTEM, CredentialType.NEO4J_AUTH);

        return credentialOpt.map(c -> c.getId().toString()).orElse(null);
    }

    /**
     * Neo4j Driver 생성.
     *
     * @param uri Neo4j URI
     * @param credentialId 크리덴셜 ID
     * @return 생성된 Driver
     */
    private Driver createDriver(String uri, String credentialId) {
        Optional<String> authJsonOpt = credentialResolver.resolveSystemApiKey(CredentialType.NEO4J_AUTH);
        if (authJsonOpt.isEmpty()) {
            throw new IllegalStateException("NEO4J_AUTH credential not found");
        }

        Neo4jAuth auth = parseNeo4jAuth(authJsonOpt.get());
        return GraphDatabase.driver(uri, AuthTokens.basic(auth.username(), auth.password()));
    }

    /**
     * Neo4j 인증정보 파싱.
     */
    private Neo4jAuth parseNeo4jAuth(String json) {
        try {
            return objectMapper.readValue(json, Neo4jAuth.class);
        } catch (Exception e) {
            log.error("Failed to parse Neo4j auth credentials: {}", e.getMessage());
            throw new IllegalStateException("Failed to parse Neo4j auth credentials", e);
        }
    }

    /**
     * Driver 닫기 (예외 무시).
     */
    private void closeDriver(Driver driver) {
        try {
            driver.close();
        } catch (Exception e) {
            log.warn("Failed to close Neo4j driver", e);
        }
    }

    /**
     * 캐시된 Driver가 있으면 닫기.
     */
    private void closeDriverIfExists() {
        synchronized (this) {
            if (cachedDriver != null) {
                closeDriver(cachedDriver);
                cachedDriver = null;
                cachedCredentialId = null;
            }
        }
    }

    /**
     * Neo4j 인증정보 Record.
     */
    private record Neo4jAuth(String username, String password) {}
}
