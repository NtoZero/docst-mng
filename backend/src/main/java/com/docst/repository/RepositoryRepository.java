package com.docst.repository;

import com.docst.domain.Repository;
import com.docst.domain.Repository.RepoProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@org.springframework.stereotype.Repository
public interface RepositoryRepository extends JpaRepository<Repository, UUID> {

    List<Repository> findByProjectIdOrderByCreatedAt(UUID projectId);

    List<Repository> findByProjectIdAndActiveOrderByCreatedAt(UUID projectId, boolean active);

    Optional<Repository> findByProviderAndExternalId(RepoProvider provider, String externalId);

    Optional<Repository> findByProjectIdAndProviderAndOwnerAndName(
            UUID projectId, RepoProvider provider, String owner, String name);

    boolean existsByProjectIdAndProviderAndOwnerAndName(
            UUID projectId, RepoProvider provider, String owner, String name);
}
