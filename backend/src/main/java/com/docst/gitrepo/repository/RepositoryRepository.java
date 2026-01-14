package com.docst.gitrepo.repository;

import com.docst.gitrepo.Repository;
import com.docst.gitrepo.Repository.RepoProvider;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 레포지토리 레포지토리.
 * Git 레포지토리 엔티티에 대한 데이터 접근을 제공한다.
 */
@org.springframework.stereotype.Repository
public interface RepositoryRepository extends JpaRepository<Repository, UUID> {

    /**
     * 프로젝트에 속한 모든 레포지토리를 조회한다.
     *
     * @param projectId 프로젝트 ID
     * @return 레포지토리 목록 (생성일순)
     */
    List<Repository> findByProjectIdOrderByCreatedAt(UUID projectId);

    /**
     * 프로젝트에 속한 활성/비활성 레포지토리를 조회한다.
     *
     * @param projectId 프로젝트 ID
     * @param active 활성화 상태
     * @return 레포지토리 목록 (생성일순)
     */
    List<Repository> findByProjectIdAndActiveOrderByCreatedAt(UUID projectId, boolean active);

    /**
     * 제공자와 외부 ID로 레포지토리를 조회한다.
     *
     * @param provider 레포 제공자
     * @param externalId 외부 시스템 ID
     * @return 레포지토리 (존재하지 않으면 empty)
     */
    Optional<Repository> findByProviderAndExternalId(RepoProvider provider, String externalId);

    /**
     * 프로젝트, 제공자, 소유자, 이름으로 레포지토리를 조회한다.
     *
     * @param projectId 프로젝트 ID
     * @param provider 레포 제공자
     * @param owner 소유자 이름
     * @param name 레포 이름
     * @return 레포지토리 (존재하지 않으면 empty)
     */
    Optional<Repository> findByProjectIdAndProviderAndOwnerAndName(
            UUID projectId, RepoProvider provider, String owner, String name);

    /**
     * 동일한 레포지토리가 프로젝트에 이미 존재하는지 확인한다.
     *
     * @param projectId 프로젝트 ID
     * @param provider 레포 제공자
     * @param owner 소유자 이름
     * @param name 레포 이름
     * @return 존재 여부
     */
    boolean existsByProjectIdAndProviderAndOwnerAndName(
            UUID projectId, RepoProvider provider, String owner, String name);

    /**
     * ID로 레포지토리를 조회하며 Credential도 함께 로드한다.
     *
     * @param id 레포지토리 ID
     * @return 레포지토리 (존재하지 않으면 empty)
     */
    @EntityGraph(attributePaths = {"credential"})
    Optional<Repository> findWithCredentialById(UUID id);

    /**
     * 소유자와 이름으로 레포지토리를 조회한다.
     * Webhook 이벤트 처리 시 사용한다.
     *
     * @param owner 소유자 이름
     * @param name 레포 이름
     * @return 레포지토리 (존재하지 않으면 empty)
     */
    Optional<Repository> findByOwnerAndName(String owner, String name);
}
