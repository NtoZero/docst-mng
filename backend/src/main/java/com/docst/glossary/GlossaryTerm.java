package com.docst.glossary;

import com.docst.project.Project;
import com.docst.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 용어 사전 엔티티.
 * 프로젝트별로 용어와 정의를 관리한다.
 */
@Entity
@Table(name = "dm_glossary_term", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"project_id", "name"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GlossaryTerm {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** 소속 프로젝트 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    /** 용어명 (프로젝트 내 유일) */
    @Setter
    @Column(nullable = false, length = 200)
    private String name;

    /** 용어 정의 */
    @Setter
    @Column(nullable = false, columnDefinition = "TEXT")
    private String definition;

    /** 동의어 목록 */
    @Setter
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> synonyms = new ArrayList<>();

    /** 분류 카테고리 */
    @Setter
    @Column(length = 100)
    private String category;

    /** 약어 */
    @Setter
    @Column(length = 50)
    private String abbreviation;

    /** 관련 용어 ID 목록 */
    @Setter
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "related_terms", columnDefinition = "jsonb")
    private List<UUID> relatedTerms = new ArrayList<>();

    /** 생성자 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    /** 생성 시각 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 수정 시각 */
    @Setter
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * 용어 생성자.
     *
     * @param project 소속 프로젝트
     * @param name 용어명
     * @param definition 정의
     * @param createdBy 생성자
     */
    public GlossaryTerm(Project project, String name, String definition, User createdBy) {
        this.project = project;
        this.name = name;
        this.definition = definition;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * 수정 시각을 현재 시각으로 갱신한다.
     */
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
