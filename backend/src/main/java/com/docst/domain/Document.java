package com.docst.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 문서 엔티티.
 * 레포지토리 내의 문서 파일 메타데이터를 저장한다.
 */
@Entity
@Table(name = "dm_document", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"repository_id", "path"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** 소속 레포지토리 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false)
    private Repository repository;

    /** 레포지토리 내 파일 경로 */
    @Column(nullable = false)
    private String path;

    /** 문서 제목 (첫 번째 H1 헤딩 또는 파일명) */
    @Setter
    @Column(nullable = false)
    private String title;

    /** 문서 타입 */
    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false)
    private DocType docType;

    /** 최신 버전의 커밋 SHA */
    @Setter
    @Column(name = "latest_commit_sha")
    private String latestCommitSha;

    /** 삭제 여부 (소프트 삭제) */
    @Setter
    @Column(nullable = false)
    private boolean deleted = false;

    /** 생성 시각 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 버전 목록 (최신순 정렬) */
    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("committedAt DESC")
    private List<DocumentVersion> versions = new ArrayList<>();

    /**
     * 문서 생성자.
     *
     * @param repository 소속 레포지토리
     * @param path 파일 경로
     * @param title 문서 제목
     * @param docType 문서 타입
     */
    public Document(Repository repository, String path, String title, DocType docType) {
        this.repository = repository;
        this.path = path;
        this.title = title;
        this.docType = docType;
        this.createdAt = Instant.now();
    }

    /**
     * 문서에 새 버전을 추가한다.
     *
     * @param version 추가할 버전
     */
    public void addVersion(DocumentVersion version) {
        versions.add(version);
        version.setDocument(this);
        this.latestCommitSha = version.getCommitSha();
    }

    /** 문서 타입 */
    public enum DocType {
        /** Markdown */
        MD,
        /** AsciiDoc */
        ADOC,
        /** OpenAPI/Swagger 스펙 */
        OPENAPI,
        /** Architecture Decision Record */
        ADR,
        /** 기타 */
        OTHER
    }
}
