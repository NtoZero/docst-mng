package com.docst.git;

import com.docst.domain.Repository;
import com.docst.repository.RepositoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Git 브랜치 관리 서비스.
 * JGit을 사용하여 브랜치 생성, 전환, 목록 조회 기능을 제공한다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BranchService {

    private final GitService gitService;
    private final RepositoryRepository repositoryRepository;

    /**
     * 브랜치 목록 조회
     */
    public List<String> listBranches(UUID repositoryId) {
        Repository repo = repositoryRepository.findById(repositoryId)
            .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repositoryId));

        try (Git git = gitService.cloneOrOpen(repo)) {
            return git.branchList()
                .call()
                .stream()
                .map(Ref::getName)
                .map(name -> name.replace("refs/heads/", ""))
                .toList();
        } catch (Exception e) {
            log.error("Failed to list branches for repository: {}", repositoryId, e);
            throw new RuntimeException("Failed to list branches", e);
        }
    }

    /**
     * 브랜치 생성
     */
    public String createBranch(UUID repositoryId, String branchName, String fromBranch) {
        Repository repo = repositoryRepository.findById(repositoryId)
            .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repositoryId));

        try (Git git = gitService.cloneOrOpen(repo)) {
            Ref ref = git.branchCreate()
                .setName(branchName)
                .setStartPoint(fromBranch)
                .call();

            log.info("Created branch: {} from {} (ref: {})", branchName, fromBranch, ref.getName());
            return ref.getName();
        } catch (Exception e) {
            log.error("Failed to create branch: {} from {}", branchName, fromBranch, e);
            throw new RuntimeException("Failed to create branch: " + branchName, e);
        }
    }

    /**
     * 브랜치 전환
     */
    public void switchBranch(UUID repositoryId, String branchName) {
        Repository repo = repositoryRepository.findById(repositoryId)
            .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repositoryId));

        try (Git git = gitService.cloneOrOpen(repo)) {
            git.checkout()
                .setName(branchName)
                .call();

            log.info("Switched to branch: {}", branchName);
        } catch (Exception e) {
            log.error("Failed to switch to branch: {}", branchName, e);
            throw new RuntimeException("Failed to switch to branch: " + branchName, e);
        }
    }

    /**
     * 현재 브랜치 이름 조회
     */
    public String getCurrentBranch(UUID repositoryId) {
        Repository repo = repositoryRepository.findById(repositoryId)
            .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repositoryId));

        try (Git git = gitService.cloneOrOpen(repo)) {
            String fullBranch = git.getRepository().getFullBranch();
            if (fullBranch != null && fullBranch.startsWith("refs/heads/")) {
                return fullBranch.substring("refs/heads/".length());
            }
            return fullBranch;
        } catch (Exception e) {
            log.error("Failed to get current branch for repository: {}", repositoryId, e);
            throw new RuntimeException("Failed to get current branch", e);
        }
    }
}
