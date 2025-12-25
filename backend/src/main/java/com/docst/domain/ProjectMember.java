package com.docst.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dm_project_member", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"project_id", "user_id"})
})
public class ProjectMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectRole role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ProjectMember() {}

    public ProjectMember(Project project, User user, ProjectRole role) {
        this.project = project;
        this.user = user;
        this.role = role;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Project getProject() { return project; }
    public User getUser() { return user; }
    public ProjectRole getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }

    public void setRole(ProjectRole role) { this.role = role; }
}
