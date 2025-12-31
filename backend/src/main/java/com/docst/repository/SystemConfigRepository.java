package com.docst.repository;

import com.docst.domain.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * 시스템 설정 리포지토리.
 */
public interface SystemConfigRepository extends JpaRepository<SystemConfig, UUID> {

    /**
     * 설정 키로 조회.
     *
     * @param configKey 설정 키
     * @return 시스템 설정
     */
    Optional<SystemConfig> findByConfigKey(String configKey);

    /**
     * 설정 키 존재 여부 확인.
     *
     * @param configKey 설정 키
     * @return 존재 여부
     */
    boolean existsByConfigKey(String configKey);
}
