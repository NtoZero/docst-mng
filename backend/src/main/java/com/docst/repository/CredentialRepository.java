package com.docst.repository;

import com.docst.domain.Credential;
import com.docst.domain.Credential.CredentialType;
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
}
