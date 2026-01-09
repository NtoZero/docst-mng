package com.docst.domain;

import com.docst.rag.RagMode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 프로젝트 엔티티.
 * 여러 레포지토리를 하나의 논리적 단위로 그룹화한다.
 */
@Entity
@Table(name = "dm_project")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** 프로젝트 이름 */
    @Setter
    @Column(nullable = false)
    private String name;

    /** 프로젝트 설명 */
    @Setter
    private String description;

    /** 활성화 상태 */
    @Setter
    @Column(nullable = false)
    private boolean active = true;

    /**
     * RAG 검색 모드 (Phase 4).
     * null이면 전역 기본값(auto) 또는 검색 요청의 mode 파라미터 사용.
     * 프로젝트 설정 화면에서 설정 가능.
     */
    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "rag_mode")
    private RagMode ragMode;  // nullable

    /** RAG 모드별 상세 설정 (Phase 4) */
    @Setter
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rag_config", columnDefinition = "jsonb")
    private String ragConfig;

    /** 생성 시각 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 프로젝트 멤버 목록 */
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProjectMember> members = new ArrayList<>();

    /** 연결된 레포지토리 목록 */
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Repository> repositories = new ArrayList<>();

    /**
     * 프로젝트 생성자.
     *
     * @param name 프로젝트 이름
     * @param description 프로젝트 설명
     */
    public Project(String name, String description) {
        this.name = name;
        this.description = description;
        this.createdAt = Instant.now();
    }

    /**
     * 프로젝트에 멤버를 추가한다.
     *
     * @param user 추가할 사용자
     * @param role 부여할 역할
     */
    public void addMember(User user, ProjectRole role) {
        ProjectMember member = new ProjectMember(this, user, role);
        members.add(member);
    }

    /**
     * 프로젝트에 레포지토리를 추가한다.
     *
     * @param repository 추가할 레포지토리
     */
    public void addRepository(Repository repository) {
        repositories.add(repository);
        repository.setProject(this);
    }
}
