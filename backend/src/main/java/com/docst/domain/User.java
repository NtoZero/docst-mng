package com.docst.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * 사용자 엔티티.
 * GitHub OAuth 또는 로컬 인증을 통해 생성된 사용자 정보를 저장한다.
 */
@Entity
@Table(name = "dm_user", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"provider", "provider_user_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** 인증 제공자 (GITHUB, LOCAL) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthProvider provider;

    /** 인증 제공자에서의 사용자 ID */
    @Column(name = "provider_user_id", nullable = false)
    private String providerUserId;

    /** 이메일 주소 */
    @Setter
    private String email;

    /** 표시 이름 */
    @Setter
    @Column(name = "display_name")
    private String displayName;

    /** 생성 시각 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * 비밀번호 해시 (LOCAL 사용자만 사용)
     * Argon2id 형식: $argon2id$v=19$m=19456,t=2,p=1$...
     */
    @Column(name = "password_hash", length = 150)
    private String passwordHash;

    /**
     * 사용자 생성자.
     *
     * @param provider 인증 제공자
     * @param providerUserId 제공자 사용자 ID
     * @param email 이메일
     * @param displayName 표시 이름
     */
    public User(AuthProvider provider, String providerUserId, String email, String displayName) {
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.email = email;
        this.displayName = displayName;
        this.createdAt = Instant.now();
    }

    /**
     * 비밀번호가 설정되어 있는지 확인
     */
    public boolean hasPassword() {
        return passwordHash != null && !passwordHash.isEmpty();
    }

    /**
     * LOCAL 사용자인지 확인
     */
    public boolean isLocalUser() {
        return provider == AuthProvider.LOCAL;
    }

    /**
     * 비밀번호 해시 설정 (public for service layer access)
     */
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /**
     * 비밀번호 해시 조회 (public for service layer access)
     */
    public String getPasswordHash() {
        return passwordHash;
    }

    /** 인증 제공자 타입 */
    public enum AuthProvider {
        GITHUB, LOCAL
    }
}
