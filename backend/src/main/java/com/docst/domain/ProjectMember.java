package com.docst.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * 프로젝트 멤버 엔티티.
 * 사용자와 프로젝트 간의 다대다 관계를 역할과 함께 관리한다.
 */
@Entity
@Table(name = "dm_project_member", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"project_id", "user_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** 소속 프로젝트 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    /** 멤버 사용자 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 프로젝트 내 역할 */
    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectRole role;

    /** 멤버 추가 시각 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * 프로젝트 멤버 생성자.
     *
     * @param project 소속 프로젝트
     * @param user 멤버 사용자
     * @param role 부여할 역할
     */
    public ProjectMember(Project project, User user, ProjectRole role) {
        this.project = project;
        this.user = user;
        this.role = role;
        this.createdAt = Instant.now();
    }
}
