package com.docst.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 레포지토리 엔티티.
 * GitHub 또는 로컬 Git 레포지토리 정보를 저장한다.
 */
@Entity
@Table(name = "dm_repository", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"project_id", "provider", "owner", "name"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Repository {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** 소속 프로젝트 */
    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    /** 레포지토리 제공자 (GITHUB, LOCAL) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RepoProvider provider;

    /** 외부 시스템 ID (GitHub repo ID 등) */
    @Setter
    @Column(name = "external_id")
    private String externalId;

    /** 소유자/조직 이름 */
    @Column(nullable = false)
    private String owner;

    /** 레포지토리 이름 */
    @Column(nullable = false)
    private String name;

    /** Git clone URL */
    @Setter
    @Column(name = "clone_url")
    private String cloneUrl;

    /** 기본 브랜치 */
    @Setter
    @Column(name = "default_branch")
    private String defaultBranch = "main";

    /** 로컬 미러 경로 */
    @Setter
    @Column(name = "local_mirror_path")
    private String localMirrorPath;

    /** 활성화 상태 */
    @Setter
    @Column(nullable = false)
    private boolean active = true;

    /** 생성 시각 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 문서 목록 */
    @OneToMany(mappedBy = "repository", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Document> documents = new ArrayList<>();

    /** 동기화 작업 목록 */
    @OneToMany(mappedBy = "repository", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SyncJob> syncJobs = new ArrayList<>();

    /** 인증 자격증명 (선택) */
    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credential_id")
    private Credential credential;

    /** 동기화 설정 (JSONB) */
    @Setter
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sync_config", columnDefinition = "jsonb")
    private RepositorySyncConfig syncConfig;

    /**
     * 동기화 설정을 반환한다.
     * null이면 기본 설정을 반환한다.
     */
    public RepositorySyncConfig getSyncConfig() {
        return syncConfig != null ? syncConfig : RepositorySyncConfig.defaultConfig();
    }

    /**
     * 레포지토리 생성자.
     *
     * @param project 소속 프로젝트
     * @param provider 레포 제공자
     * @param owner 소유자 이름
     * @param name 레포 이름
     */
    public Repository(Project project, RepoProvider provider, String owner, String name) {
        this.project = project;
        this.provider = provider;
        this.owner = owner;
        this.name = name;
        this.createdAt = Instant.now();
    }

    /**
     * 레포지토리 전체 이름을 반환한다.
     *
     * @return "owner/name" 형식의 전체 이름
     */
    public String getFullName() {
        return owner + "/" + name;
    }

    /** 레포지토리 제공자 타입 */
    public enum RepoProvider {
        GITHUB, LOCAL
    }
}
