package com.docst.glossary.repository;

import com.docst.glossary.GlossaryTerm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 용어 사전 레포지토리.
 * 용어 엔티티에 대한 데이터 접근을 제공한다.
 */
@Repository
public interface GlossaryTermRepository extends JpaRepository<GlossaryTerm, UUID> {

    /**
     * 프로젝트의 모든 용어를 이름순으로 조회한다.
     *
     * @param projectId 프로젝트 ID
     * @return 용어 목록
     */
    List<GlossaryTerm> findByProjectIdOrderByName(UUID projectId);

    /**
     * 프로젝트의 특정 카테고리 용어를 이름순으로 조회한다.
     *
     * @param projectId 프로젝트 ID
     * @param category 카테고리
     * @return 용어 목록
     */
    List<GlossaryTerm> findByProjectIdAndCategoryOrderByName(UUID projectId, String category);

    /**
     * 프로젝트와 용어명으로 용어를 조회한다.
     *
     * @param projectId 프로젝트 ID
     * @param name 용어명
     * @return 용어 (존재하지 않으면 empty)
     */
    Optional<GlossaryTerm> findByProjectIdAndName(UUID projectId, String name);

    /**
     * 프로젝트 내에서 용어명 또는 정의에 포함된 키워드를 검색한다.
     *
     * @param projectId 프로젝트 ID
     * @param query 검색 키워드 (소문자)
     * @return 용어 목록
     */
    @Query("SELECT t FROM GlossaryTerm t WHERE t.project.id = :projectId " +
           "AND (LOWER(t.name) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(t.definition) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<GlossaryTerm> searchByKeyword(@Param("projectId") UUID projectId, @Param("query") String query);

    /**
     * 프로젝트의 모든 카테고리 목록을 조회한다.
     *
     * @param projectId 프로젝트 ID
     * @return 카테고리 목록 (null 제외, 중복 제거)
     */
    @Query("SELECT DISTINCT t.category FROM GlossaryTerm t " +
           "WHERE t.project.id = :projectId AND t.category IS NOT NULL " +
           "ORDER BY t.category")
    List<String> findDistinctCategoriesByProjectId(@Param("projectId") UUID projectId);

    /**
     * 프로젝트 내 용어명 존재 여부를 확인한다.
     *
     * @param projectId 프로젝트 ID
     * @param name 용어명
     * @return 존재 여부
     */
    boolean existsByProjectIdAndName(UUID projectId, String name);

    /**
     * ID 목록으로 용어를 일괄 조회한다.
     *
     * @param ids 용어 ID 목록
     * @return 용어 목록
     */
    List<GlossaryTerm> findByIdIn(List<UUID> ids);

    /**
     * 프로젝트의 용어 개수를 조회한다.
     *
     * @param projectId 프로젝트 ID
     * @return 용어 개수
     */
    long countByProjectId(UUID projectId);
}
