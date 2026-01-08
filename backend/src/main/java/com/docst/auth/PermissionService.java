package com.docst.auth;

import com.docst.domain.Document;
import com.docst.domain.ProjectMember;
import com.docst.domain.ProjectRole;
import com.docst.domain.Repository;
import com.docst.repository.DocumentRepository;
import com.docst.repository.ProjectMemberRepository;
import com.docst.repository.RepositoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * 권한 서비스.
 * 프로젝트 및 레포지토리에 대한 사용자 권한을 검증한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final ProjectMemberRepository projectMemberRepository;
    private final RepositoryRepository repositoryRepository;
    private final DocumentRepository documentRepository;

    /**
     * 사용자가 프로젝트에 대해 요구되는 권한을 가지고 있는지 확인한다.
     *
     * @param userId    사용자 ID
     * @param projectId 프로젝트 ID
     * @param required  요구되는 역할
     * @return 권한이 있으면 true
     */
    @Transactional(readOnly = true)
    public boolean hasProjectPermission(UUID userId, UUID projectId, ProjectRole required) {
        Optional<ProjectMember> memberOpt = projectMemberRepository.findByProjectIdAndUserId(projectId, userId);
        if (memberOpt.isEmpty()) {
            log.debug("User {} is not a member of project {}", userId, projectId);
            return false;
        }

        ProjectMember member = memberOpt.get();
        boolean hasPermission = member.getRole().hasPermission(required);

        log.debug("User {} has role {} in project {} (required: {}): {}",
                userId, member.getRole(), projectId, required, hasPermission);

        return hasPermission;
    }

    /**
     * 사용자가 레포지토리에 대해 요구되는 권한을 가지고 있는지 확인한다.
     * 레포지토리의 소속 프로젝트에 대한 권한을 검사한다.
     *
     * @param userId       사용자 ID
     * @param repositoryId 레포지토리 ID
     * @param required     요구되는 역할
     * @return 권한이 있으면 true
     */
    @Transactional(readOnly = true)
    public boolean hasRepositoryPermission(UUID userId, UUID repositoryId, ProjectRole required) {
        Optional<Repository> repoOpt = repositoryRepository.findById(repositoryId);
        if (repoOpt.isEmpty()) {
            log.warn("Repository {} not found", repositoryId);
            return false;
        }

        UUID projectId = repoOpt.get().getProject().getId();
        return hasProjectPermission(userId, projectId, required);
    }

    /**
     * 사용자가 프로젝트에 대해 요구되는 권한을 가지고 있는지 확인하고, 없으면 예외를 발생시킨다.
     *
     * @param userId    사용자 ID
     * @param projectId 프로젝트 ID
     * @param required  요구되는 역할
     * @throws PermissionDeniedException 권한이 없을 경우
     */
    public void requireProjectPermission(UUID userId, UUID projectId, ProjectRole required) {
        if (!hasProjectPermission(userId, projectId, required)) {
            throw new PermissionDeniedException(
                    String.format("User %s does not have %s permission for project %s",
                            userId, required, projectId)
            );
        }
    }

    /**
     * 사용자가 레포지토리에 대해 요구되는 권한을 가지고 있는지 확인하고, 없으면 예외를 발생시킨다.
     *
     * @param userId       사용자 ID
     * @param repositoryId 레포지토리 ID
     * @param required     요구되는 역할
     * @throws PermissionDeniedException 권한이 없을 경우
     */
    public void requireRepositoryPermission(UUID userId, UUID repositoryId, ProjectRole required) {
        if (!hasRepositoryPermission(userId, repositoryId, required)) {
            throw new PermissionDeniedException(
                    String.format("User %s does not have %s permission for repository %s",
                            userId, required, repositoryId)
            );
        }
    }

    /**
     * 현재 사용자가 프로젝트에 대해 요구되는 권한을 가지고 있는지 확인한다.
     *
     * @param projectId 프로젝트 ID
     * @param required  요구되는 역할
     * @return 권한이 있으면 true
     */
    public boolean hasProjectPermission(UUID projectId, ProjectRole required) {
        UUID userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return false;
        }
        return hasProjectPermission(userId, projectId, required);
    }

    /**
     * 현재 사용자가 레포지토리에 대해 요구되는 권한을 가지고 있는지 확인한다.
     *
     * @param repositoryId 레포지토리 ID
     * @param required     요구되는 역할
     * @return 권한이 있으면 true
     */
    public boolean hasRepositoryPermission(UUID repositoryId, ProjectRole required) {
        UUID userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return false;
        }
        return hasRepositoryPermission(userId, repositoryId, required);
    }

    /**
     * 현재 사용자가 프로젝트에 대해 요구되는 권한을 가지고 있는지 확인하고, 없으면 예외를 발생시킨다.
     *
     * @param projectId 프로젝트 ID
     * @param required  요구되는 역할
     * @throws PermissionDeniedException 권한이 없을 경우
     */
    public void requireProjectPermission(UUID projectId, ProjectRole required) {
        UUID userId = SecurityUtils.requireCurrentUserId();
        requireProjectPermission(userId, projectId, required);
    }

    /**
     * 현재 사용자가 레포지토리에 대해 요구되는 권한을 가지고 있는지 확인하고, 없으면 예외를 발생시킨다.
     *
     * @param repositoryId 레포지토리 ID
     * @param required     요구되는 역할
     * @throws PermissionDeniedException 권한이 없을 경우
     */
    public void requireRepositoryPermission(UUID repositoryId, ProjectRole required) {
        UUID userId = SecurityUtils.requireCurrentUserId();
        requireRepositoryPermission(userId, repositoryId, required);
    }

    /**
     * 사용자가 문서에 대해 요구되는 권한을 가지고 있는지 확인한다.
     * 문서가 속한 레포지토리의 프로젝트에 대한 권한을 검사한다.
     *
     * @param userId     사용자 ID
     * @param documentId 문서 ID
     * @param required   요구되는 역할
     * @return 권한이 있으면 true
     */
    @Transactional(readOnly = true)
    public boolean hasDocumentPermission(UUID userId, UUID documentId, ProjectRole required) {
        Optional<Document> docOpt = documentRepository.findById(documentId);
        if (docOpt.isEmpty()) {
            log.warn("Document {} not found", documentId);
            return false;
        }

        UUID repositoryId = docOpt.get().getRepository().getId();
        return hasRepositoryPermission(userId, repositoryId, required);
    }

    /**
     * 사용자가 문서에 대해 요구되는 권한을 가지고 있는지 확인하고, 없으면 예외를 발생시킨다.
     *
     * @param userId     사용자 ID
     * @param documentId 문서 ID
     * @param required   요구되는 역할
     * @throws PermissionDeniedException 권한이 없을 경우
     */
    public void requireDocumentPermission(UUID userId, UUID documentId, ProjectRole required) {
        if (!hasDocumentPermission(userId, documentId, required)) {
            throw new PermissionDeniedException(
                    String.format("User %s does not have %s permission for document %s",
                            userId, required, documentId)
            );
        }
    }

    /**
     * 현재 사용자가 문서에 대해 요구되는 권한을 가지고 있는지 확인한다.
     *
     * @param documentId 문서 ID
     * @param required   요구되는 역할
     * @return 권한이 있으면 true
     */
    public boolean hasDocumentPermission(UUID documentId, ProjectRole required) {
        UUID userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return false;
        }
        return hasDocumentPermission(userId, documentId, required);
    }

    /**
     * 현재 사용자가 문서에 대해 요구되는 권한을 가지고 있는지 확인하고, 없으면 예외를 발생시킨다.
     *
     * @param documentId 문서 ID
     * @param required   요구되는 역할
     * @throws PermissionDeniedException 권한이 없을 경우
     */
    public void requireDocumentPermission(UUID documentId, ProjectRole required) {
        UUID userId = SecurityUtils.requireCurrentUserId();
        requireDocumentPermission(userId, documentId, required);
    }
}
