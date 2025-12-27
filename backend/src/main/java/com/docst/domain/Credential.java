package com.docst.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * 자격증명 엔티티.
 * Git 레포지토리 접근을 위한 인증 정보를 암호화하여 저장한다.
 */
@Entity
@Table(name = "dm_credential", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "name"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Credential {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** 소유자 (사용자) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 자격증명 이름 (사용자가 식별하기 위한 이름) */
    @Column(nullable = false)
    private String name;

    /** 자격증명 타입 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CredentialType type;

    /** 사용자명 (GitHub username 등) */
    @Setter
    private String username;

    /** 암호화된 토큰/비밀번호 */
    @Setter
    @Column(name = "encrypted_secret", nullable = false, columnDefinition = "TEXT")
    private String encryptedSecret;

    /** 설명 */
    @Setter
    private String description;

    /** 활성화 상태 */
    @Setter
    @Column(nullable = false)
    private boolean active = true;

    /** 생성 시각 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 수정 시각 */
    @Setter
    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * 자격증명 생성자.
     *
     * @param user 소유자
     * @param name 자격증명 이름
     * @param type 자격증명 타입
     * @param encryptedSecret 암호화된 비밀
     */
    public Credential(User user, String name, CredentialType type, String encryptedSecret) {
        this.user = user;
        this.name = name;
        this.type = type;
        this.encryptedSecret = encryptedSecret;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * 자격증명 타입.
     */
    public enum CredentialType {
        /** GitHub Personal Access Token */
        GITHUB_PAT,
        /** 기본 사용자명/비밀번호 */
        BASIC_AUTH,
        /** SSH 키 (향후 지원) */
        SSH_KEY
    }
}
