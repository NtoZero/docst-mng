package com.docst.service;

import com.docst.domain.Credential;
import com.docst.domain.Credential.CredentialType;
import com.docst.domain.CredentialScope;
import com.docst.repository.CredentialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * 동적 크리덴셜 해결 서비스.
 * 프로젝트 > 시스템 우선순위로 크리덴셜을 조회하고 복호화한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DynamicCredentialResolver {

    private final CredentialRepository credentialRepository;
    private final EncryptionService encryptionService;

    // ============================================================
    // API 키 해결 (프로젝트 > 시스템 우선순위)
    // ============================================================

    /**
     * API 키 해결 (프로젝트 > 시스템 우선순위).
     * 크리덴셜을 찾을 수 없으면 예외 발생.
     *
     * @param projectId 프로젝트 ID (null 가능)
     * @param type 크리덴셜 타입
     * @return 복호화된 API 키
     * @throws IllegalStateException 크리덴셜을 찾을 수 없을 때
     */
    public String resolveApiKey(UUID projectId, CredentialType type) {
        // 1. 프로젝트 레벨 크리덴셜
        if (projectId != null) {
            Optional<Credential> projectCred = credentialRepository
                    .findByProjectIdAndTypeAndScopeAndActiveTrue(projectId, type, CredentialScope.PROJECT);
            if (projectCred.isPresent()) {
                log.debug("Using project credential for type {} in project {}", type, projectId);
                return decrypt(projectCred.get());
            }
        }

        // 2. 시스템 레벨 크리덴셜
        Optional<Credential> systemCred = credentialRepository
                .findByScopeAndTypeAndActiveTrue(CredentialScope.SYSTEM, type);
        if (systemCred.isPresent()) {
            log.debug("Using system credential for type {}", type);
            return decrypt(systemCred.get());
        }

        // 3. 없으면 예외 (yml 폴백 없음)
        throw new IllegalStateException(
                "No credential found for type " + type +
                        (projectId != null ? " in project " + projectId : " at system level")
        );
    }

    /**
     * API 키 해결 (Optional 반환, 예외 없음).
     *
     * @param projectId 프로젝트 ID (null 가능)
     * @param type 크리덴셜 타입
     * @return 복호화된 API 키
     */
    public Optional<String> resolveApiKeyOptional(UUID projectId, CredentialType type) {
        try {
            return Optional.of(resolveApiKey(projectId, type));
        } catch (IllegalStateException e) {
            log.debug("No credential found for type {} in project {}: {}", type, projectId, e.getMessage());
            return Optional.empty();
        }
    }

    // ============================================================
    // 시스템 크리덴셜만 조회
    // ============================================================

    /**
     * 시스템 크리덴셜만 조회.
     *
     * @param type 크리덴셜 타입
     * @return 복호화된 API 키
     */
    public Optional<String> resolveSystemApiKey(CredentialType type) {
        return credentialRepository
                .findByScopeAndTypeAndActiveTrue(CredentialScope.SYSTEM, type)
                .map(this::decrypt);
    }

    /**
     * 시스템 크리덴셜만 조회 (예외 발생).
     *
     * @param type 크리덴셜 타입
     * @return 복호화된 API 키
     * @throws IllegalStateException 크리덴셜을 찾을 수 없을 때
     */
    public String resolveSystemApiKeyOrThrow(CredentialType type) {
        return resolveSystemApiKey(type)
                .orElseThrow(() -> new IllegalStateException("No system credential found for type " + type));
    }

    // ============================================================
    // 프로젝트 크리덴셜만 조회
    // ============================================================

    /**
     * 프로젝트 크리덴셜만 조회.
     *
     * @param projectId 프로젝트 ID
     * @param type 크리덴셜 타입
     * @return 복호화된 API 키
     */
    public Optional<String> resolveProjectApiKey(UUID projectId, CredentialType type) {
        return credentialRepository
                .findByProjectIdAndTypeAndScopeAndActiveTrue(projectId, type, CredentialScope.PROJECT)
                .map(this::decrypt);
    }

    // ============================================================
    // 복호화
    // ============================================================

    /**
     * 크리덴셜 복호화.
     *
     * @param credential 크리덴셜
     * @return 복호화된 비밀
     */
    private String decrypt(Credential credential) {
        try {
            return encryptionService.decrypt(credential.getEncryptedSecret());
        } catch (Exception e) {
            log.error("Failed to decrypt credential {}: {}", credential.getId(), e.getMessage());
            throw new RuntimeException("Failed to decrypt credential", e);
        }
    }
}
