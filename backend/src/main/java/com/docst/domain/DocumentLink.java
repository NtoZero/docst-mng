package com.docst.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * 문서 링크 엔티티.
 * 문서 간의 링크 관계를 나타낸다. (source → target)
 */
@Entity
@Table(name = "dm_document_link", indexes = {
        @Index(name = "idx_document_link_source", columnList = "source_document_id"),
        @Index(name = "idx_document_link_target", columnList = "target_document_id"),
        @Index(name = "idx_document_link_type", columnList = "link_type")
})
@Getter
@Setter
@NoArgsConstructor
public class DocumentLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * 링크의 시작점 문서 (source).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_document_id", nullable = false)
    private Document sourceDocument;

    /**
     * 링크의 목적지 문서 (target).
     * 목적지 문서가 존재하지 않으면 null (broken link).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_document_id")
    private Document targetDocument;

    /**
     * 원본 링크 텍스트.
     * 예: "docs/api.md", "../README.md", "https://example.com"
     */
    @Column(name = "link_text", nullable = false, length = 1000)
    private String linkText;

    /**
     * 링크 타입.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "link_type", nullable = false, length = 20)
    private LinkType linkType;

    /**
     * 링크가 깨졌는지 여부.
     * target_document_id가 null이면 true.
     */
    @Column(name = "is_broken", nullable = false)
    private boolean broken;

    /**
     * 링크가 발견된 위치 (라인 번호).
     */
    @Column(name = "line_number")
    private Integer lineNumber;

    /**
     * 링크의 앵커 텍스트 (표시 텍스트).
     * 예: [Getting Started](./docs/start.md) → "Getting Started"
     */
    @Column(name = "anchor_text", length = 500)
    private String anchorText;

    /**
     * 생성 일시.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * 링크 타입.
     */
    public enum LinkType {
        /**
         * 내부 문서 링크 (상대 경로).
         * 예: ./docs/api.md, ../README.md
         */
        INTERNAL,

        /**
         * Wiki 스타일 링크.
         * 예: [[API Documentation]], [[concepts/architecture]]
         */
        WIKI,

        /**
         * 외부 링크 (http/https).
         * 예: https://example.com/docs
         */
        EXTERNAL,

        /**
         * 앵커 링크 (같은 문서 내).
         * 예: #section-1
         */
        ANCHOR
    }

    /**
     * 생성자.
     *
     * @param sourceDocument 시작 문서
     * @param linkText       링크 텍스트
     * @param linkType       링크 타입
     * @param anchorText     앵커 텍스트
     * @param lineNumber     라인 번호
     */
    public DocumentLink(Document sourceDocument, String linkText, LinkType linkType,
                        String anchorText, Integer lineNumber) {
        this.sourceDocument = sourceDocument;
        this.linkText = linkText;
        this.linkType = linkType;
        this.anchorText = anchorText;
        this.lineNumber = lineNumber;
        this.broken = true; // 기본값: target이 설정될 때까지 broken
        this.createdAt = Instant.now();
    }

    /**
     * 목적지 문서를 설정하고 broken 상태를 업데이트한다.
     *
     * @param targetDocument 목적지 문서
     */
    public void setTargetDocument(Document targetDocument) {
        this.targetDocument = targetDocument;
        this.broken = (targetDocument == null);
    }

    /**
     * 링크가 내부 링크인지 확인한다.
     *
     * @return INTERNAL 또는 WIKI 타입이면 true
     */
    public boolean isInternal() {
        return linkType == LinkType.INTERNAL || linkType == LinkType.WIKI;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
