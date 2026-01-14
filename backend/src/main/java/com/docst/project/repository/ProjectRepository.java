package com.docst.project.repository;

import com.docst.project.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 프로젝트 레포지토리.
 * 프로젝트 엔티티에 대한 데이터 접근을 제공한다.
 */
@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    /**
     * 특정 사용자가 멤버로 속한 프로젝트 목록을 조회한다.
     *
     * @param userId 사용자 ID
     * @return 프로젝트 목록 (생성일순)
     */
    @Query("SELECT p FROM Project p JOIN p.members m WHERE m.user.id = :userId ORDER BY p.createdAt")
    List<Project> findByMemberUserId(@Param("userId") UUID userId);

    /**
     * 활성화된 모든 프로젝트를 조회한다.
     *
     * @return 활성 프로젝트 목록 (생성일순)
     */
    @Query("SELECT p FROM Project p WHERE p.active = true ORDER BY p.createdAt")
    List<Project> findAllActive();

    /**
     * 모든 프로젝트를 생성일순으로 조회한다.
     *
     * @return 프로젝트 목록
     */
    List<Project> findAllByOrderByCreatedAt();

    /**
     * 이름으로 프로젝트를 조회한다.
     *
     * @param name 프로젝트 이름
     * @return 프로젝트 (존재하지 않으면 empty)
     */
    Optional<Project> findByName(String name);
}
