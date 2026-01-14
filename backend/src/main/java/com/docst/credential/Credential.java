package com.docst.credential;

import com.docst.project.Project;
import com.docst.user.User;
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

    /** 소유자 (사용자) - SYSTEM 스코프일 때는 null */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /** 자격증명 이름 (사용자가 식별하기 위한 이름) */
    @Column(nullable = false)
    private String name;

    /** 자격증명 스코프 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CredentialScope scope = CredentialScope.USER;

    /** 프로젝트 (PROJECT 스코프일 때만 사용) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

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
     * 자격증명 생성자 (USER 스코프).
     *
     * @param user 소유자
     * @param name 자격증명 이름
     * @param type 자격증명 타입
     * @param encryptedSecret 암호화된 비밀
     */
    public Credential(User user, String name, CredentialType type, String encryptedSecret) {
        this.user = user;
        this.scope = CredentialScope.USER;
        this.name = name;
        this.type = type;
        this.encryptedSecret = encryptedSecret;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * 시스템 크리덴셜 생성 (SYSTEM 스코프).
     *
     * @param name 자격증명 이름
     * @param type 자격증명 타입
     * @param encryptedSecret 암호화된 비밀
     * @return 시스템 크리덴셜
     */
    public static Credential createSystemCredential(String name, CredentialType type, String encryptedSecret) {
        Credential credential = new Credential();
        credential.scope = CredentialScope.SYSTEM;
        credential.user = null;
        credential.project = null;
        credential.name = name;
        credential.type = type;
        credential.encryptedSecret = encryptedSecret;
        credential.createdAt = Instant.now();
        credential.updatedAt = Instant.now();
        return credential;
    }

    /**
     * 프로젝트 크리덴셜 생성 (PROJECT 스코프).
     *
     * @param project 프로젝트
     * @param name 자격증명 이름
     * @param type 자격증명 타입
     * @param encryptedSecret 암호화된 비밀
     * @return 프로젝트 크리덴셜
     */
    public static Credential createProjectCredential(Project project, String name, CredentialType type, String encryptedSecret) {
        Credential credential = new Credential();
        credential.scope = CredentialScope.PROJECT;
        credential.project = project;
        credential.user = null;  // 프로젝트 크리덴셜은 user_id 없음
        credential.name = name;
        credential.type = type;
        credential.encryptedSecret = encryptedSecret;
        credential.createdAt = Instant.now();
        credential.updatedAt = Instant.now();
        return credential;
    }

    /**
     * 자격증명 타입.
     */
    public enum CredentialType {
        // 기존 타입
        /** GitHub Personal Access Token */
        GITHUB_PAT,
        /** 기본 사용자명/비밀번호 */
        BASIC_AUTH,
        /** SSH 키 (향후 지원) */
        SSH_KEY,

        // 신규 타입 (외부 서비스 API 키)
        /** OpenAI API Key */
        OPENAI_API_KEY,
        /** Neo4j 인증정보 (JSON: {"username":"...", "password":"..."}) */
        NEO4J_AUTH,
        /** PgVector 인증정보 (JSON: {"username":"...", "password":"..."}) */
        PGVECTOR_AUTH,
        /** Anthropic API Key */
        ANTHROPIC_API_KEY,
        /** 커스텀 API Key (확장용) */
        CUSTOM_API_KEY
    }
}
