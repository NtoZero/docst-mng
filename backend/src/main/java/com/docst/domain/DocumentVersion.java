package com.docst.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * 문서 버전 엔티티.
 * 특정 Git 커밋 시점의 문서 내용을 저장한다.
 */
@Entity
@Table(name = "dm_document_version", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"document_id", "commit_sha"})
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** 소속 문서 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    /** Git 커밋 SHA */
    @Column(name = "commit_sha", nullable = false)
    private String commitSha;

    /** 커밋 작성자 이름 */
    @Column(name = "author_name")
    private String authorName;

    /** 커밋 작성자 이메일 */
    @Column(name = "author_email")
    private String authorEmail;

    /** 커밋 시각 */
    @Column(name = "committed_at")
    private Instant committedAt;

    /** 커밋 메시지 */
    @Column(columnDefinition = "TEXT")
    private String message;

    /** 내용 해시 (중복 체크용) */
    @Column(name = "content_hash")
    private String contentHash;

    /** 문서 내용 */
    @Column(columnDefinition = "TEXT")
    private String content;

    /** 레코드 생성 시각 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * 문서 버전 생성자.
     *
     * @param document 소속 문서
     * @param commitSha Git 커밋 SHA
     */
    public DocumentVersion(Document document, String commitSha) {
        this.document = document;
        this.commitSha = commitSha;
        this.createdAt = Instant.now();
    }
}
