package com.docst.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "dm_project")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProjectMember> members = new ArrayList<>();

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Repository> repositories = new ArrayList<>();

    protected Project() {}

    public Project(String name, String description) {
        this.name = name;
        this.description = description;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public List<ProjectMember> getMembers() { return members; }
    public List<Repository> getRepositories() { return repositories; }

    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setActive(boolean active) { this.active = active; }

    public void addMember(User user, ProjectRole role) {
        ProjectMember member = new ProjectMember(this, user, role);
        members.add(member);
    }

    public void addRepository(Repository repository) {
        repositories.add(repository);
        repository.setProject(this);
    }
}
