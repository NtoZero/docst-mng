package com.docst.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "dm_document", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"repository_id", "path"})
})
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false)
    private Repository repository;

    @Column(nullable = false)
    private String path;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false)
    private DocType docType;

    @Column(name = "latest_commit_sha")
    private String latestCommitSha;

    @Column(nullable = false)
    private boolean deleted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("committedAt DESC")
    private List<DocumentVersion> versions = new ArrayList<>();

    protected Document() {}

    public Document(Repository repository, String path, String title, DocType docType) {
        this.repository = repository;
        this.path = path;
        this.title = title;
        this.docType = docType;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Repository getRepository() { return repository; }
    public String getPath() { return path; }
    public String getTitle() { return title; }
    public DocType getDocType() { return docType; }
    public String getLatestCommitSha() { return latestCommitSha; }
    public boolean isDeleted() { return deleted; }
    public Instant getCreatedAt() { return createdAt; }
    public List<DocumentVersion> getVersions() { return versions; }

    public void setTitle(String title) { this.title = title; }
    public void setLatestCommitSha(String latestCommitSha) { this.latestCommitSha = latestCommitSha; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }

    public void addVersion(DocumentVersion version) {
        versions.add(version);
        version.setDocument(this);
        this.latestCommitSha = version.getCommitSha();
    }

    public enum DocType {
        MD, ADOC, OPENAPI, ADR, OTHER
    }
}
