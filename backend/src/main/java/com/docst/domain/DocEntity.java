package com.docst.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * 문서에서 추출된 엔티티.
 * Phase 4: Graph RAG를 위한 엔티티 저장소 (PostgreSQL 백업용)
 *
 * 엔티티 유형:
 * - Concept: 개념, 용어
 * - API: API 엔드포인트, 함수
 * - Component: 시스템 컴포넌트
 * - Technology: 기술, 프레임워크
 */
@Entity
@Table(name = "dm_entity")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** 소속 프로젝트 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    /** 엔티티 이름 */
    @Setter
    @Column(nullable = false)
    private String name;

    /** 엔티티 유형 (Concept, API, Component, Technology) */
    @Setter
    @Column(nullable = false)
    private String type;

    /** 엔티티 설명 */
    @Setter
    private String description;

    /** 엔티티가 처음 발견된 청크 */
    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_chunk_id")
    private DocChunk sourceChunk;

    /** 생성 시각 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * 엔티티 생성자.
     *
     * @param project 소속 프로젝트
     * @param name 엔티티 이름
     * @param type 엔티티 유형
     * @param description 엔티티 설명
     */
    public DocEntity(Project project, String name, String type, String description) {
        this.project = project;
        this.name = name;
        this.type = type;
        this.description = description;
        this.createdAt = Instant.now();
    }
}
