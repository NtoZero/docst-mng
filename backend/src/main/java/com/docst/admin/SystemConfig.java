package com.docst.admin;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * 시스템 설정 엔티티.
 * 외부 서비스 URL, 기본값 등 시스템 전역 설정을 저장한다.
 */
@Entity
@Table(name = "dm_system_config")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SystemConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** 설정 키 (고유) */
    @Column(name = "config_key", nullable = false, unique = true, length = 100)
    private String configKey;

    /** 설정 값 */
    @Setter
    @Column(name = "config_value", columnDefinition = "TEXT")
    private String configValue;

    /** 설정 타입 (STRING, INTEGER, BOOLEAN, JSON) */
    @Setter
    @Column(name = "config_type", nullable = false, length = 50)
    private String configType = "STRING";

    /** 설명 */
    @Setter
    @Column(length = 500)
    private String description;

    /** 생성 시각 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 수정 시각 */
    @Setter
    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * 시스템 설정 생성자.
     *
     * @param configKey 설정 키
     */
    public SystemConfig(String configKey) {
        this.configKey = configKey;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * 시스템 설정 생성자 (전체 필드).
     *
     * @param configKey 설정 키
     * @param configValue 설정 값
     * @param configType 설정 타입
     * @param description 설명
     */
    public SystemConfig(String configKey, String configValue, String configType, String description) {
        this.configKey = configKey;
        this.configValue = configValue;
        this.configType = configType;
        this.description = description;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
