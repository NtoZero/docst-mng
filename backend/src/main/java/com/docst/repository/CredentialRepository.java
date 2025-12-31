package com.docst.repository;

import com.docst.domain.Credential;
import com.docst.domain.Credential.CredentialType;
import com.docst.domain.CredentialScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 자격증명 레포지토리.
 * 자격증명 엔티티에 대한 데이터 접근을 제공한다.
 */
@Repository
public interface CredentialRepository extends JpaRepository<Credential, UUID> {

    /**
     * 사용자의 모든 자격증명을 조회한다.
     *
     * @param userId 사용자 ID
     * @return 자격증명 목록
     */
    List<Credential> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * 사용자의 활성화된 자격증명을 조회한다.
     *
     * @param userId 사용자 ID
     * @return 활성화된 자격증명 목록
     */
    List<Credential> findByUserIdAndActiveTrue(UUID userId);

    /**
     * 사용자의 특정 이름의 자격증명을 조회한다.
     *
     * @param userId 사용자 ID
     * @param name 자격증명 이름
     * @return 자격증명 (존재하지 않으면 empty)
     */
    Optional<Credential> findByUserIdAndName(UUID userId, String name);

    /**
     * 사용자의 특정 타입의 자격증명을 조회한다.
     *
     * @param userId 사용자 ID
     * @param type 자격증명 타입
     * @return 자격증명 목록
     */
    List<Credential> findByUserIdAndType(UUID userId, CredentialType type);

    /**
     * ID로 자격증명을 조회하며, 소유자 확인을 위해 사용자 ID도 함께 검증한다.
     *
     * @param id 자격증명 ID
     * @param userId 사용자 ID
     * @return 자격증명 (존재하지 않거나 소유자가 다르면 empty)
     */
    Optional<Credential> findByIdAndUserId(UUID id, UUID userId);

    // ============================================================
    // 스코프 기반 쿼리 (Phase 4-D2)
    // ============================================================

    /**
     * 스코프와 타입으로 활성화된 크리덴셜 조회 (SYSTEM 스코프용).
     *
     * @param scope 스코프
     * @param type 크리덴셜 타입
     * @return 크리덴셜
     */
    Optional<Credential> findByScopeAndTypeAndActiveTrue(CredentialScope scope, CredentialType type);

    /**
     * 프로젝트, 타입, 스코프로 활성화된 크리덴셜 조회 (PROJECT 스코프용).
     *
     * @param projectId 프로젝트 ID
     * @param type 크리덴셜 타입
     * @param scope 스코프
     * @return 크리덴셜
     */
    Optional<Credential> findByProjectIdAndTypeAndScopeAndActiveTrue(UUID projectId, CredentialType type, CredentialScope scope);

    /**
     * 스코프로 모든 크리덴셜 조회.
     *
     * @param scope 스코프
     * @return 크리덴셜 목록
     */
    List<Credential> findByScope(CredentialScope scope);

    /**
     * 스코프로 활성화된 크리덴셜 조회.
     *
     * @param scope 스코프
     * @return 크리덴셜 목록
     */
    List<Credential> findByScopeAndActiveTrue(CredentialScope scope);

    /**
     * 프로젝트 ID로 크리덴셜 조회.
     *
     * @param projectId 프로젝트 ID
     * @return 크리덴셜 목록
     */
    List<Credential> findByProjectId(UUID projectId);

    /**
     * 프로젝트 ID와 타입으로 크리덴셜 조회.
     *
     * @param projectId 프로젝트 ID
     * @param type 크리덴셜 타입
     * @return 크리덴셜 목록
     */
    List<Credential> findByProjectIdAndType(UUID projectId, CredentialType type);
}
