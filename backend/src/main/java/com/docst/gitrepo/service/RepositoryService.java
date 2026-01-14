package com.docst.gitrepo.service;

import com.docst.project.Project;
import com.docst.gitrepo.Repository;
import com.docst.gitrepo.Repository.RepoProvider;
import com.docst.gitrepo.RepositorySyncConfig;
import com.docst.gitrepo.repository.RepositoryRepository;
import com.docst.git.GitWriteService;
import com.docst.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 레포지토리 서비스.
 * Git 레포지토리에 대한 비즈니스 로직을 담당한다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RepositoryService {

    private final RepositoryRepository repositoryRepository;
    private final ProjectRepository projectRepository;
    private final GitWriteService gitWriteService;

    /**
     * 프로젝트에 속한 모든 레포지토리를 조회한다.
     *
     * @param projectId 프로젝트 ID
     * @return 레포지토리 목록 (생성일순)
     */
    public List<Repository> findByProjectId(UUID projectId) {
        return repositoryRepository.findByProjectIdOrderByCreatedAt(projectId);
    }

    /**
     * ID로 레포지토리를 조회한다.
     *
     * @param id 레포지토리 ID
     * @return 레포지토리 (존재하지 않으면 empty)
     */
    public Optional<Repository> findById(UUID id) {
        return repositoryRepository.findById(id);
    }

    /**
     * 새 레포지토리를 생성한다.
     *
     * @param projectId 프로젝트 ID
     * @param provider 레포 제공자 (GITHUB, LOCAL)
     * @param owner 소유자 이름
     * @param name 레포 이름
     * @param defaultBranch 기본 브랜치 (null이면 기본값 사용)
     * @param localPath 로컬 경로 (LOCAL 제공자일 경우)
     * @return 생성된 레포지토리
     * @throws IllegalArgumentException 프로젝트가 존재하지 않을 경우
     * @throws IllegalStateException 동일한 레포지토리가 이미 존재할 경우
     */
    @Transactional
    public Repository create(UUID projectId, RepoProvider provider, String owner, String name,
                              String defaultBranch, String localPath) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        if (repositoryRepository.existsByProjectIdAndProviderAndOwnerAndName(projectId, provider, owner, name)) {
            throw new IllegalStateException("Repository already exists: " + owner + "/" + name);
        }

        Repository repo = new Repository(project, provider, owner, name);

        if (defaultBranch != null) {
            repo.setDefaultBranch(defaultBranch);
        }

        if (provider == RepoProvider.GITHUB) {
            repo.setCloneUrl("https://github.com/" + owner + "/" + name + ".git");
        } else if (provider == RepoProvider.LOCAL) {
            repo.setLocalMirrorPath(localPath);
        }

        return repositoryRepository.save(repo);
    }

    /**
     * 레포지토리 정보를 업데이트한다.
     *
     * @param id 레포지토리 ID
     * @param active 활성화 상태 (null이면 변경하지 않음)
     * @param defaultBranch 기본 브랜치 (null이면 변경하지 않음)
     * @return 업데이트된 레포지토리 (존재하지 않으면 empty)
     */
    @Transactional
    public Optional<Repository> update(UUID id, Boolean active, String defaultBranch) {
        return repositoryRepository.findById(id)
                .map(repo -> {
                    if (active != null) repo.setActive(active);
                    if (defaultBranch != null) repo.setDefaultBranch(defaultBranch);
                    return repositoryRepository.save(repo);
                });
    }

    /**
     * 레포지토리를 삭제한다.
     *
     * @param id 레포지토리 ID
     */
    @Transactional
    public void delete(UUID id) {
        repositoryRepository.deleteById(id);
    }

    /**
     * 레포지토리를 저장한다.
     *
     * @param repository 저장할 레포지토리
     * @return 저장된 레포지토리
     */
    @Transactional
    public Repository save(Repository repository) {
        return repositoryRepository.save(repository);
    }

    /**
     * 레포지토리의 로컬 미러 경로를 업데이트한다.
     *
     * @param id 레포지토리 ID
     * @param path 새 로컬 경로
     */
    @Transactional
    public void updateLocalMirrorPath(UUID id, String path) {
        repositoryRepository.findById(id).ifPresent(repo -> {
            repo.setLocalMirrorPath(path);
            repositoryRepository.save(repo);
        });
    }

    /**
     * 레포지토리를 다른 프로젝트로 이관한다.
     *
     * @param repositoryId 레포지토리 ID
     * @param targetProjectId 이관 대상 프로젝트 ID
     * @return 이관된 레포지토리
     * @throws IllegalArgumentException 레포지토리 또는 대상 프로젝트가 존재하지 않을 경우
     * @throws IllegalStateException 대상 프로젝트에 동일한 레포지토리가 이미 존재할 경우
     */
    @Transactional
    public Repository moveToProject(UUID repositoryId, UUID targetProjectId) {
        Repository repo = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repositoryId));

        Project targetProject = projectRepository.findById(targetProjectId)
                .orElseThrow(() -> new IllegalArgumentException("Target project not found: " + targetProjectId));

        // 같은 프로젝트로 이관하는 경우 무시
        if (repo.getProject().getId().equals(targetProjectId)) {
            return repo;
        }

        // 대상 프로젝트에 동일한 레포지토리가 존재하는지 확인
        if (repositoryRepository.existsByProjectIdAndProviderAndOwnerAndName(
                targetProjectId, repo.getProvider(), repo.getOwner(), repo.getName())) {
            throw new IllegalStateException(
                    "Repository already exists in target project: " + repo.getOwner() + "/" + repo.getName());
        }

        repo.setProject(targetProject);
        return repositoryRepository.save(repo);
    }

    /**
     * 로컬 커밋을 원격 레포지토리로 푸시한다.
     *
     * @param repositoryId 레포지토리 ID
     * @param branch 푸시할 브랜치 (null이면 기본 브랜치 사용)
     * @return 푸시 결과 (성공 여부, 메시지, 브랜치)
     * @throws IllegalArgumentException 레포지토리가 존재하지 않을 경우
     */
    @Transactional(readOnly = true)
    public PushResult pushToRemote(UUID repositoryId, String branch) {
        Repository repo = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repositoryId));

        String targetBranch = (branch != null) ? branch : repo.getDefaultBranch();

        try {
            gitWriteService.pushToRemote(repo, targetBranch);
            return new PushResult(true, "Successfully pushed to " + targetBranch, targetBranch);
        } catch (Exception e) {
            return new PushResult(false, "Push failed: " + e.getMessage(), null);
        }
    }

    // ===== Phase 12: 동기화 설정 관리 =====

    /**
     * 레포지토리의 동기화 설정을 업데이트한다.
     *
     * @param id 레포지토리 ID
     * @param config 새 동기화 설정
     * @return 업데이트된 레포지토리 (존재하지 않으면 empty)
     */
    @Transactional
    public Optional<Repository> updateSyncConfig(UUID id, RepositorySyncConfig config) {
        return repositoryRepository.findById(id)
                .map(repo -> {
                    repo.setSyncConfig(config);
                    return repositoryRepository.save(repo);
                });
    }

    /**
     * 푸시 결과를 나타내는 레코드.
     */
    public record PushResult(boolean success, String message, String branch) {}
}
