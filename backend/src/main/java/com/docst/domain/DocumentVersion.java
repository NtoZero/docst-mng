package com.docst.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dm_document_version", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"document_id", "commit_sha"})
})
public class DocumentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "commit_sha", nullable = false)
    private String commitSha;

    @Column(name = "author_name")
    private String authorName;

    @Column(name = "author_email")
    private String authorEmail;

    @Column(name = "committed_at")
    private Instant committedAt;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "content_hash")
    private String contentHash;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DocumentVersion() {}

    public DocumentVersion(Document document, String commitSha) {
        this.document = document;
        this.commitSha = commitSha;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Document getDocument() { return document; }
    public String getCommitSha() { return commitSha; }
    public String getAuthorName() { return authorName; }
    public String getAuthorEmail() { return authorEmail; }
    public Instant getCommittedAt() { return committedAt; }
    public String getMessage() { return message; }
    public String getContentHash() { return contentHash; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }

    public void setDocument(Document document) { this.document = document; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }
    public void setAuthorEmail(String authorEmail) { this.authorEmail = authorEmail; }
    public void setCommittedAt(Instant committedAt) { this.committedAt = committedAt; }
    public void setMessage(String message) { this.message = message; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    public void setContent(String content) { this.content = content; }
}
