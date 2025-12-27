package com.docst.service;

import com.docst.domain.Repository;
import com.docst.git.GitCommitWalker;
import com.docst.git.GitFileScanner;
import com.docst.git.GitService;
import com.docst.repository.RepositoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * 커밋 서비스.
 * Git 커밋 히스토리 조회 및 변경 파일 분석을 담당한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CommitService {

    private final RepositoryRepository repositoryRepository;
    private final GitService gitService;
    private final GitCommitWalker gitCommitWalker;
    private final GitFileScanner gitFileScanner;

    /**
     * 커밋 목록을 조회한다.
     *
     * @param repositoryId 레포지토리 ID
     * @param branch 브랜치명 (null이면 기본 브랜치 사용)
     * @param page 페이지 번호 (0부터 시작)
     * @param size 페이지 크기
     * @return 커밋 정보 목록
     * @throws IllegalArgumentException 레포지토리가 존재하지 않을 경우
     * @throws RuntimeException Git 작업 실패 시
     */
    public List<GitCommitWalker.CommitInfo> listCommits(UUID repositoryId, String branch,
                                                          int page, int size) {
        Repository repo = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repositoryId));

        String targetBranch = branch != null ? branch : repo.getDefaultBranch();
        int skip = page * size;

        try (Git git = gitService.cloneOrOpen(repo)) {
            List<GitCommitWalker.CommitInfo> commits = gitCommitWalker.listCommits(
                    git, targetBranch, skip, size);

            log.info("Listed {} commits from repository {} (branch={}, page={}, size={})",
                    commits.size(), repo.getFullName(), targetBranch, page, size);
            return commits;

        } catch (IOException e) {
            log.error("Failed to list commits for repository {}: {}",
                    repo.getFullName(), e.getMessage());
            throw new RuntimeException("Failed to list commits: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error listing commits for repository {}: {}",
                    repo.getFullName(), e.getMessage());
            throw new RuntimeException("Failed to list commits: " + e.getMessage(), e);
        }
    }

    /**
     * 특정 커밋의 변경된 파일 목록을 조회한다.
     *
     * @param repositoryId 레포지토리 ID
     * @param commitSha 커밋 SHA
     * @return 변경된 파일 목록
     * @throws IllegalArgumentException 레포지토리가 존재하지 않을 경우
     * @throws RuntimeException Git 작업 실패 시
     */
    public List<GitCommitWalker.ChangedFile> getChangedFiles(UUID repositoryId, String commitSha) {
        Repository repo = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repositoryId));

        try (Git git = gitService.cloneOrOpen(repo)) {
            List<GitCommitWalker.ChangedFile> changedFiles = gitCommitWalker.getChangedFiles(git, commitSha);

            log.info("Found {} changed files in commit {} of repository {}",
                    changedFiles.size(), commitSha.substring(0, 7), repo.getFullName());
            return changedFiles;

        } catch (IOException e) {
            log.error("Failed to get changed files for commit {} in repository {}: {}",
                    commitSha, repo.getFullName(), e.getMessage());
            throw new RuntimeException("Failed to get changed files: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error getting changed files for commit {} in repository {}: {}",
                    commitSha, repo.getFullName(), e.getMessage());
            throw new RuntimeException("Failed to get changed files: " + e.getMessage(), e);
        }
    }

    /**
     * 두 커밋 간 변경된 파일 목록을 조회한다.
     *
     * @param repositoryId 레포지토리 ID
     * @param fromSha 시작 커밋 SHA
     * @param toSha 종료 커밋 SHA
     * @return 변경된 파일 목록
     * @throws IllegalArgumentException 레포지토리가 존재하지 않을 경우
     * @throws RuntimeException Git 작업 실패 시
     */
    public List<GitCommitWalker.ChangedFile> getChangedFilesBetween(UUID repositoryId,
                                                                      String fromSha,
                                                                      String toSha) {
        Repository repo = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repositoryId));

        try (Git git = gitService.cloneOrOpen(repo)) {
            List<GitCommitWalker.ChangedFile> changedFiles = gitCommitWalker.getChangedFilesBetween(
                    git, fromSha, toSha);

            log.info("Found {} changed files between {} and {} in repository {}",
                    changedFiles.size(), fromSha.substring(0, 7), toSha.substring(0, 7), repo.getFullName());
            return changedFiles;

        } catch (IOException e) {
            log.error("Failed to get changed files between {} and {} in repository {}: {}",
                    fromSha, toSha, repo.getFullName(), e.getMessage());
            throw new RuntimeException("Failed to get changed files: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error getting changed files between {} and {} in repository {}: {}",
                    fromSha, toSha, repo.getFullName(), e.getMessage());
            throw new RuntimeException("Failed to get changed files: " + e.getMessage(), e);
        }
    }

    /**
     * 변경된 파일이 문서 파일인지 확인한다.
     *
     * @param path 파일 경로
     * @return 문서 파일이면 true
     */
    public boolean isDocumentFile(String path) {
        return gitFileScanner.isDocumentFile(path);
    }
}
