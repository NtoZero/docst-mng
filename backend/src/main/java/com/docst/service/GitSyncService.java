package com.docst.service;

import com.docst.domain.Repository;
import com.docst.git.DocumentParser;
import com.docst.git.GitFileScanner;
import com.docst.git.GitService;
import com.docst.git.GitService.CommitInfo;
import com.docst.repository.RepositoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Git 동기화 서비스.
 * JGit을 사용하여 실제 Git 레포지토리 동기화를 수행한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GitSyncService {

    private final GitService gitService;
    private final GitFileScanner gitFileScanner;
    private final DocumentParser documentParser;
    private final DocumentService documentService;
    private final RepositoryRepository repositoryRepository;
    private final SyncProgressTracker progressTracker;

    /**
     * 레포지토리를 동기화한다.
     * Git clone/fetch를 수행하고 문서 파일을 스캔하여 DB에 저장한다.
     *
     * @param jobId 동기화 작업 ID
     * @param repositoryId 레포지토리 ID
     * @param branch 대상 브랜치
     * @return 최신 커밋 SHA
     * @throws IllegalArgumentException 레포지토리가 존재하지 않을 경우
     * @throws RuntimeException Git 작업 실패 시
     */
    @Transactional
    public String syncRepository(UUID jobId, UUID repositoryId, String branch) {
        Repository repo = repositoryRepository.findWithCredentialById(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repositoryId));

        try (Git git = gitService.cloneOrOpen(repo)) {
            // Update local mirror path
            Path localPath = gitService.getLocalPath(repositoryId);
            repo.setLocalMirrorPath(localPath.toString());
            repositoryRepository.save(repo);

            // Fetch and checkout
            gitService.fetch(git, repo, branch);
            gitService.checkout(git, branch);

            // Get latest commit
            String latestCommit = gitService.getLatestCommitSha(git, branch);
            CommitInfo commitInfo = gitService.getCommitInfo(git, latestCommit);

            log.info("Syncing repository {} at commit {}", repo.getFullName(), latestCommit.substring(0, 7));

            // Scan document files
            List<String> documentPaths = gitFileScanner.scanDocumentFiles(git, latestCommit);
            log.info("Found {} document files", documentPaths.size());

            // Update progress tracker
            progressTracker.setTotal(jobId, documentPaths.size());

            // Process each document
            for (int i = 0; i < documentPaths.size(); i++) {
                String path = documentPaths.get(i);
                processDocument(git, repo, path, latestCommit, commitInfo);
                progressTracker.update(jobId, i + 1, path);
            }

            progressTracker.complete(jobId, "Sync completed: " + documentPaths.size() + " documents");
            return latestCommit;

        } catch (GitAPIException | IOException e) {
            log.error("Failed to sync repository: {}", repo.getFullName(), e);
            throw new RuntimeException("Sync failed: " + e.getMessage(), e);
        }
    }

    /**
     * 개별 문서 파일을 처리한다.
     * Git에서 파일 내용을 읽어 파싱하고 DB에 저장한다.
     *
     * @param git Git 인스턴스
     * @param repo 레포지토리 엔티티
     * @param path 파일 경로
     * @param commitSha 커밋 SHA
     * @param commitInfo 커밋 정보
     */
    private void processDocument(Git git, Repository repo, String path,
                                   String commitSha, CommitInfo commitInfo) {
        try {
            Optional<String> contentOpt = gitService.getFileContent(git, commitSha, path);
            if (contentOpt.isEmpty()) {
                log.warn("Could not read file: {}", path);
                return;
            }

            String content = contentOpt.get();
            DocumentParser.ParsedDocument parsed = documentParser.parse(content);

            documentService.upsertDocument(
                    repo.getId(),
                    path,
                    commitSha,
                    content,
                    commitInfo.authorName(),
                    commitInfo.authorEmail(),
                    commitInfo.committedAt(),
                    commitInfo.message()
            );

            log.debug("Processed document: {} (title: {})", path, parsed.title());

        } catch (Exception e) {
            log.error("Failed to process document: {}", path, e);
        }
    }
}
