package com.docst.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "dm_repository", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"project_id", "provider", "owner", "name"})
})
public class Repository {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RepoProvider provider;

    @Column(name = "external_id")
    private String externalId;

    @Column(nullable = false)
    private String owner;

    @Column(nullable = false)
    private String name;

    @Column(name = "clone_url")
    private String cloneUrl;

    @Column(name = "default_branch")
    private String defaultBranch = "main";

    @Column(name = "local_mirror_path")
    private String localMirrorPath;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "repository", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Document> documents = new ArrayList<>();

    @OneToMany(mappedBy = "repository", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SyncJob> syncJobs = new ArrayList<>();

    protected Repository() {}

    public Repository(Project project, RepoProvider provider, String owner, String name) {
        this.project = project;
        this.provider = provider;
        this.owner = owner;
        this.name = name;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Project getProject() { return project; }
    public RepoProvider getProvider() { return provider; }
    public String getExternalId() { return externalId; }
    public String getOwner() { return owner; }
    public String getName() { return name; }
    public String getCloneUrl() { return cloneUrl; }
    public String getDefaultBranch() { return defaultBranch; }
    public String getLocalMirrorPath() { return localMirrorPath; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public List<Document> getDocuments() { return documents; }
    public List<SyncJob> getSyncJobs() { return syncJobs; }

    public void setProject(Project project) { this.project = project; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public void setCloneUrl(String cloneUrl) { this.cloneUrl = cloneUrl; }
    public void setDefaultBranch(String defaultBranch) { this.defaultBranch = defaultBranch; }
    public void setLocalMirrorPath(String localMirrorPath) { this.localMirrorPath = localMirrorPath; }
    public void setActive(boolean active) { this.active = active; }

    public String getFullName() {
        return owner + "/" + name;
    }

    public enum RepoProvider {
        GITHUB, LOCAL
    }
}
