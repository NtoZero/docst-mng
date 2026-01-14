package com.docst.rag.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * 엔티티 간 관계.
 * Phase 4: Graph RAG를 위한 엔티티 관계 저장소 (PostgreSQL 백업용)
 *
 * 관계 유형:
 * - RELATED_TO: 관련됨
 * - DEPENDS_ON: 의존함
 * - USES: 사용함
 * - PART_OF: 일부임
 */
@Entity
@Table(name = "dm_entity_relation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocEntityRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** 소스 엔티티 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_entity_id", nullable = false)
    private DocEntity sourceEntity;

    /** 타겟 엔티티 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_entity_id", nullable = false)
    private DocEntity targetEntity;

    /** 관계 유형 (RELATED_TO, DEPENDS_ON, USES, PART_OF) */
    @Setter
    @Column(name = "relation_type", nullable = false)
    private String relationType;

    /** 관계 설명 */
    @Setter
    private String description;

    /** 생성 시각 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * 엔티티 관계 생성자.
     *
     * @param sourceEntity 소스 엔티티
     * @param targetEntity 타겟 엔티티
     * @param relationType 관계 유형
     * @param description 관계 설명
     */
    public DocEntityRelation(DocEntity sourceEntity, DocEntity targetEntity,
                             String relationType, String description) {
        this.sourceEntity = sourceEntity;
        this.targetEntity = targetEntity;
        this.relationType = relationType;
        this.description = description;
        this.createdAt = Instant.now();
    }
}
