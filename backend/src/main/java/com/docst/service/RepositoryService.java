package com.docst.service;

import com.docst.domain.Project;
import com.docst.domain.Repository;
import com.docst.domain.Repository.RepoProvider;
import com.docst.repository.ProjectRepository;
import com.docst.repository.RepositoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class RepositoryService {

    private final RepositoryRepository repositoryRepository;
    private final ProjectRepository projectRepository;

    public RepositoryService(RepositoryRepository repositoryRepository, ProjectRepository projectRepository) {
        this.repositoryRepository = repositoryRepository;
        this.projectRepository = projectRepository;
    }

    public List<Repository> findByProjectId(UUID projectId) {
        return repositoryRepository.findByProjectIdOrderByCreatedAt(projectId);
    }

    public Optional<Repository> findById(UUID id) {
        return repositoryRepository.findById(id);
    }

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

    @Transactional
    public Optional<Repository> update(UUID id, Boolean active, String defaultBranch) {
        return repositoryRepository.findById(id)
                .map(repo -> {
                    if (active != null) repo.setActive(active);
                    if (defaultBranch != null) repo.setDefaultBranch(defaultBranch);
                    return repositoryRepository.save(repo);
                });
    }

    @Transactional
    public void delete(UUID id) {
        repositoryRepository.deleteById(id);
    }

    @Transactional
    public void updateLocalMirrorPath(UUID id, String path) {
        repositoryRepository.findById(id).ifPresent(repo -> {
            repo.setLocalMirrorPath(path);
            repositoryRepository.save(repo);
        });
    }
}
