package com.docst.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * 문서 청크 엔티티.
 * DocumentVersion을 분석 가능한 작은 단위로 분할한 청크를 저장한다.
 */
@Entity
@Table(name = "dm_doc_chunk", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"document_version_id", "chunk_index"})
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** 소속 문서 버전 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_version_id", nullable = false)
    private DocumentVersion documentVersion;

    /** 청크 인덱스 (0부터 시작) */
    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    /** 헤딩 경로 (예: "# Title > ## Section > ### Subsection") */
    @Column(name = "heading_path")
    private String headingPath;

    /** 청크 내용 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 토큰 수 */
    @Column(name = "token_count", nullable = false)
    private int tokenCount;

    /** 레코드 생성 시각 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * 문서 청크 생성자.
     *
     * @param documentVersion 소속 문서 버전
     * @param chunkIndex 청크 인덱스
     * @param content 청크 내용
     * @param tokenCount 토큰 수
     */
    public DocChunk(DocumentVersion documentVersion, int chunkIndex, String content, int tokenCount) {
        this.documentVersion = documentVersion;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.tokenCount = tokenCount;
        this.createdAt = Instant.now();
    }

    /**
     * 헤딩 경로 설정.
     *
     * @param headingPath 헤딩 경로
     * @return this
     */
    public DocChunk withHeadingPath(String headingPath) {
        this.headingPath = headingPath;
        return this;
    }
}
