package com.docst.service;

import com.docst.domain.Project;
import com.docst.domain.Repository;
import com.docst.domain.Repository.RepoProvider;
import com.docst.repository.ProjectRepository;
import com.docst.repository.RepositoryRepository;
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
}
