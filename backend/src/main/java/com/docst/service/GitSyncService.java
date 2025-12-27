package com.docst.service;

import com.docst.api.ApiModels.SyncMode;
import com.docst.domain.Repository;
import com.docst.git.DocumentParser;
import com.docst.git.GitCommitWalker;
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
import java.util.ArrayList;
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
    private final GitCommitWalker gitCommitWalker;
    private final DocumentParser documentParser;
    private final DocumentService documentService;
    private final RepositoryRepository repositoryRepository;
    private final SyncProgressTracker progressTracker;
    private final com.docst.chunking.ChunkingService chunkingService;
    private final com.docst.embedding.DocstEmbeddingService embeddingService;

    /**
     * 레포지토리를 동기화한다.
     * Git clone/fetch를 수행하고 문서 파일을 스캔하여 DB에 저장한다.
     *
     * @param jobId 동기화 작업 ID
     * @param repositoryId 레포지토리 ID
     * @param branch 대상 브랜치
     * @param mode 동기화 모드
     * @param targetCommitSha 특정 커밋 SHA (SPECIFIC_COMMIT 모드에서 사용)
     * @param lastSyncedCommit 마지막 동기화된 커밋 (INCREMENTAL 모드에서 사용)
     * @return 최신 커밋 SHA
     * @throws IllegalArgumentException 레포지토리가 존재하지 않을 경우
     * @throws RuntimeException Git 작업 실패 시
     */
    @Transactional
    public String syncRepository(UUID jobId, UUID repositoryId, String branch,
                                  SyncMode mode, String targetCommitSha, String lastSyncedCommit) {
        Repository repo = repositoryRepository.findWithCredentialById(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repositoryId));

        // 모드 기본값 설정
        SyncMode syncMode = mode != null ? mode : SyncMode.FULL_SCAN;

        try (Git git = gitService.cloneOrOpen(repo)) {
            // Update local mirror path
            Path localPath = gitService.getLocalPath(repositoryId);
            repo.setLocalMirrorPath(localPath.toString());
            repositoryRepository.save(repo);

            // 모드에 따라 적절한 동기화 메서드 호출
            return switch (syncMode) {
                case FULL_SCAN -> syncFullScan(jobId, git, repo, branch);
                case INCREMENTAL -> syncIncremental(jobId, git, repo, branch, lastSyncedCommit);
                case SPECIFIC_COMMIT -> syncSpecificCommit(jobId, git, repo, targetCommitSha);
            };

        } catch (GitAPIException | IOException e) {
            log.error("Failed to sync repository: {}", repo.getFullName(), e);
            throw new RuntimeException("Sync failed: " + e.getMessage(), e);
        }
    }

    /**
     * 전체 스캔 동기화.
     * 최신 커밋의 모든 문서 파일을 스캔한다.
     */
    private String syncFullScan(UUID jobId, Git git, Repository repo, String branch)
            throws GitAPIException, IOException {

        // Fetch and checkout
        gitService.fetch(git, repo, branch);
        gitService.checkout(git, branch);

        // Get latest commit
        String latestCommit = gitService.getLatestCommitSha(git, branch);
        CommitInfo commitInfo = gitService.getCommitInfo(git, latestCommit);

        log.info("FULL_SCAN: Syncing repository {} at commit {}",
                repo.getFullName(), latestCommit.substring(0, 7));

        // Scan document files
        List<String> documentPaths = gitFileScanner.scanDocumentFiles(git, latestCommit);
        log.info("FULL_SCAN: Found {} document files", documentPaths.size());

        // Update progress tracker
        progressTracker.setTotal(jobId, documentPaths.size());

        // Process each document
        for (int i = 0; i < documentPaths.size(); i++) {
            String path = documentPaths.get(i);
            processDocument(git, repo, path, latestCommit, commitInfo);
            progressTracker.update(jobId, i + 1, path);
        }

        progressTracker.complete(jobId, "Full scan completed: " + documentPaths.size() + " documents");
        return latestCommit;
    }

    /**
     * 증분 동기화.
     * 마지막 동기화 커밋 이후 변경된 문서만 처리한다.
     */
    private String syncIncremental(UUID jobId, Git git, Repository repo, String branch, String lastSyncedCommit)
            throws GitAPIException, IOException {

        // Fetch and checkout
        gitService.fetch(git, repo, branch);
        gitService.checkout(git, branch);

        // Get latest commit
        String latestCommit = gitService.getLatestCommitSha(git, branch);

        // lastSyncedCommit이 없으면 FULL_SCAN으로 fallback
        if (lastSyncedCommit == null || lastSyncedCommit.isBlank()) {
            log.warn("INCREMENTAL: No lastSyncedCommit, falling back to FULL_SCAN");
            return syncFullScan(jobId, git, repo, branch);
        }

        // 이미 최신 상태인지 확인
        if (lastSyncedCommit.equals(latestCommit)) {
            log.info("INCREMENTAL: Already up to date at {}", latestCommit.substring(0, 7));
            progressTracker.complete(jobId, "Already up to date");
            return latestCommit;
        }

        log.info("INCREMENTAL: Syncing from {} to {}",
                lastSyncedCommit.substring(0, 7), latestCommit.substring(0, 7));

        // 변경된 커밋 순회
        List<GitCommitWalker.CommitInfo> commits = gitCommitWalker.walkCommits(git, lastSyncedCommit, latestCommit);
        log.info("INCREMENTAL: Found {} commits to process", commits.size());

        List<String> processedPaths = new ArrayList<>();

        // 각 커밋의 변경 파일 처리
        for (GitCommitWalker.CommitInfo commitInfo : commits) {
            List<GitCommitWalker.ChangedFile> changedFiles = gitCommitWalker.getChangedFiles(git, commitInfo.sha());
            List<GitCommitWalker.ChangedFile> docFiles = gitFileScanner.filterDocumentFiles(changedFiles);

            for (GitCommitWalker.ChangedFile changedFile : docFiles) {
                processChangedDocument(git, repo, changedFile, commitInfo);
                processedPaths.add(changedFile.path());
            }
        }

        progressTracker.complete(jobId, "Incremental sync completed: " + processedPaths.size() + " documents");
        return latestCommit;
    }

    /**
     * 특정 커밋 동기화.
     * 지정된 커밋에서 문서를 추출한다.
     */
    private String syncSpecificCommit(UUID jobId, Git git, Repository repo, String targetCommitSha)
            throws GitAPIException, IOException {

        if (targetCommitSha == null || targetCommitSha.isBlank()) {
            throw new IllegalArgumentException("targetCommitSha is required for SPECIFIC_COMMIT mode");
        }

        log.info("SPECIFIC_COMMIT: Syncing repository {} at commit {}",
                repo.getFullName(), targetCommitSha.substring(0, 7));

        CommitInfo commitInfo = gitService.getCommitInfo(git, targetCommitSha);
        if (commitInfo == null) {
            throw new IllegalArgumentException("Commit not found: " + targetCommitSha);
        }

        // Scan document files at specific commit
        List<String> documentPaths = gitFileScanner.scanDocumentFiles(git, targetCommitSha);
        log.info("SPECIFIC_COMMIT: Found {} document files", documentPaths.size());

        // Update progress tracker
        progressTracker.setTotal(jobId, documentPaths.size());

        // Process each document
        for (int i = 0; i < documentPaths.size(); i++) {
            String path = documentPaths.get(i);
            processDocument(git, repo, path, targetCommitSha, commitInfo);
            progressTracker.update(jobId, i + 1, path);
        }

        progressTracker.complete(jobId, "Specific commit sync completed: " + documentPaths.size() + " documents");
        return targetCommitSha;
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

            com.docst.domain.DocumentVersion newVersion = documentService.upsertDocument(
                    repo.getId(),
                    path,
                    commitSha,
                    content,
                    commitInfo.authorName(),
                    commitInfo.authorEmail(),
                    commitInfo.committedAt(),
                    commitInfo.message()
            );

            // Chunk and embed the newly created document version
            if (newVersion != null) {
                try {
                    // Step 1: Chunking
                    chunkingService.chunkAndSave(newVersion);
                    log.debug("Chunked document version: {} for {}", newVersion.getId(), path);

                    // Step 2: Embedding (Spring AI VectorStore)
                    int embeddedCount = embeddingService.embedDocumentVersion(newVersion);
                    log.debug("Embedded {} chunks for document version: {} ({})",
                        embeddedCount, newVersion.getId(), path);

                } catch (Exception error) {
                    log.error("Failed to chunk/embed document: {}", path, error);
                    // Continue processing even if chunking/embedding fails
                }
            }

            log.debug("Processed document: {} (title: {})", path, parsed.title());

        } catch (Exception e) {
            log.error("Failed to process document: {}", path, e);
        }
    }

    /**
     * 변경된 문서 파일을 처리한다.
     * 변경 타입에 따라 적절한 처리를 수행한다.
     *
     * @param git Git 인스턴스
     * @param repo 레포지토리 엔티티
     * @param changedFile 변경된 파일 정보
     * @param commitInfo 커밋 정보 (GitCommitWalker.CommitInfo)
     */
    private void processChangedDocument(Git git, Repository repo,
                                         GitCommitWalker.ChangedFile changedFile,
                                         GitCommitWalker.CommitInfo commitInfo) {
        try {
            // GitCommitWalker.CommitInfo를 GitService.CommitInfo로 변환
            GitService.CommitInfo serviceCommitInfo = new GitService.CommitInfo(
                    commitInfo.sha(),
                    commitInfo.authorName(),
                    commitInfo.authorEmail(),
                    commitInfo.committedAt(),
                    commitInfo.fullMessage()
            );

            switch (changedFile.changeType()) {
                case ADDED, MODIFIED -> {
                    // 파일 내용 읽어서 처리
                    processDocument(git, repo, changedFile.path(), commitInfo.sha(), serviceCommitInfo);
                }
                case DELETED -> {
                    // 문서 삭제 처리
                    documentService.markDeleted(repo.getId(), changedFile.path());
                    log.debug("Marked document as deleted: {}", changedFile.path());
                }
                case RENAMED -> {
                    // 경로 변경 처리 (기존 문서 삭제, 새 경로로 추가)
                    documentService.markDeleted(repo.getId(), changedFile.oldPath());
                    processDocument(git, repo, changedFile.path(), commitInfo.sha(), serviceCommitInfo);
                    log.debug("Renamed document: {} -> {}", changedFile.oldPath(), changedFile.path());
                }
            }
        } catch (Exception e) {
            log.error("Failed to process changed document: {}", changedFile.path(), e);
        }
    }
}
