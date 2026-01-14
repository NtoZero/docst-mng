package com.docst.project.repository;

import com.docst.project.ProjectMember;
import com.docst.project.ProjectRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 프로젝트 멤버 레포지토리.
 * 프로젝트 멤버십에 대한 데이터 접근을 제공한다.
 */
@Repository
public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {

    /**
     * 프로젝트와 사용자 ID로 멤버십을 조회한다.
     *
     * @param projectId 프로젝트 ID
     * @param userId 사용자 ID
     * @return 멤버십 (존재하지 않으면 empty)
     */
    Optional<ProjectMember> findByProjectIdAndUserId(UUID projectId, UUID userId);

    /**
     * 프로젝트의 모든 멤버를 조회한다.
     *
     * @param projectId 프로젝트 ID
     * @return 멤버 목록
     */
    List<ProjectMember> findByProjectId(UUID projectId);

    /**
     * 사용자의 모든 멤버십을 조회한다.
     *
     * @param userId 사용자 ID
     * @return 멤버십 목록
     */
    List<ProjectMember> findByUserId(UUID userId);

    /**
     * 특정 프로젝트에 사용자가 멤버로 존재하는지 확인한다.
     *
     * @param projectId 프로젝트 ID
     * @param userId 사용자 ID
     * @return 존재 여부
     */
    boolean existsByProjectIdAndUserId(UUID projectId, UUID userId);

    /**
     * 프로젝트에서 사용자를 제거한다.
     *
     * @param projectId 프로젝트 ID
     * @param userId 사용자 ID
     */
    void deleteByProjectIdAndUserId(UUID projectId, UUID userId);
}
