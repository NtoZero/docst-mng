package com.docst.repository;

import com.docst.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    @Query("SELECT p FROM Project p JOIN p.members m WHERE m.user.id = :userId ORDER BY p.createdAt")
    List<Project> findByMemberUserId(@Param("userId") UUID userId);

    @Query("SELECT p FROM Project p WHERE p.active = true ORDER BY p.createdAt")
    List<Project> findAllActive();

    List<Project> findAllByOrderByCreatedAt();
}
