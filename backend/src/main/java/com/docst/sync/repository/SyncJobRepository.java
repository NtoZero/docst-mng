package com.docst.sync.repository;

import com.docst.sync.SyncJob;
import com.docst.sync.SyncJob.SyncStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 동기화 작업 레포지토리.
 * 동기화 작업 엔티티에 대한 데이터 접근을 제공한다.
 */
@Repository
public interface SyncJobRepository extends JpaRepository<SyncJob, UUID> {

    /**
     * 레포지토리의 모든 동기화 작업을 최신순으로 조회한다.
     *
     * @param repositoryId 레포지토리 ID
     * @return 동기화 작업 목록 (최신순)
     */
    List<SyncJob> findByRepositoryIdOrderByCreatedAtDesc(UUID repositoryId);

    /**
     * 레포지토리의 가장 최근 동기화 작업을 조회한다.
     *
     * @param repositoryId 레포지토리 ID
     * @return 최근 작업 (존재하지 않으면 empty)
     */
    Optional<SyncJob> findFirstByRepositoryIdOrderByCreatedAtDesc(UUID repositoryId);

    /**
     * 레포지토리의 특정 상태인 가장 최근 작업을 조회한다.
     *
     * @param repositoryId 레포지토리 ID
     * @param status 작업 상태
     * @return 최근 작업 (존재하지 않으면 empty)
     */
    Optional<SyncJob> findFirstByRepositoryIdAndStatusOrderByCreatedAtDesc(
            UUID repositoryId, SyncStatus status);

    /**
     * 특정 상태인 모든 동기화 작업을 조회한다.
     *
     * @param status 작업 상태
     * @return 동기화 작업 목록
     */
    List<SyncJob> findByStatus(SyncStatus status);

    /**
     * 레포지토리에 특정 상태인 동기화 작업이 존재하는지 확인한다.
     *
     * @param repositoryId 레포지토리 ID
     * @param status 작업 상태
     * @return 존재 여부
     */
    boolean existsByRepositoryIdAndStatus(UUID repositoryId, SyncStatus status);

    /**
     * ID로 동기화 작업을 Repository와 함께 조회한다.
     * 비동기 컨텍스트에서 LazyInitializationException을 방지하기 위해 사용.
     *
     * @param jobId 작업 ID
     * @return 동기화 작업 (존재하지 않으면 empty)
     */
    @Query("SELECT j FROM SyncJob j JOIN FETCH j.repository WHERE j.id = :jobId")
    Optional<SyncJob> findByIdWithRepository(@Param("jobId") UUID jobId);
}
