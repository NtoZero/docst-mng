package com.docst.service;

import com.docst.domain.Repository;
import com.docst.git.DocumentParser;
import com.docst.git.GitFileScanner;
import com.docst.git.GitService;
import com.docst.git.GitService.CommitInfo;
import com.docst.repository.RepositoryRepository;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class GitSyncService {

    private static final Logger log = LoggerFactory.getLogger(GitSyncService.class);

    private final GitService gitService;
    private final GitFileScanner gitFileScanner;
    private final DocumentParser documentParser;
    private final DocumentService documentService;
    private final RepositoryRepository repositoryRepository;

    public GitSyncService(GitService gitService,
                          GitFileScanner gitFileScanner,
                          DocumentParser documentParser,
                          DocumentService documentService,
                          RepositoryRepository repositoryRepository) {
        this.gitService = gitService;
        this.gitFileScanner = gitFileScanner;
        this.documentParser = documentParser;
        this.documentService = documentService;
        this.repositoryRepository = repositoryRepository;
    }

    @Transactional
    public String syncRepository(UUID repositoryId, String branch) {
        Repository repo = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repositoryId));

        try (Git git = gitService.cloneOrOpen(repo)) {
            // Update local mirror path
            Path localPath = gitService.getLocalPath(repositoryId);
            repo.setLocalMirrorPath(localPath.toString());
            repositoryRepository.save(repo);

            // Fetch and checkout
            gitService.fetch(git, branch);
            gitService.checkout(git, branch);

            // Get latest commit
            String latestCommit = gitService.getLatestCommitSha(git, branch);
            CommitInfo commitInfo = gitService.getCommitInfo(git, latestCommit);

            log.info("Syncing repository {} at commit {}", repo.getFullName(), latestCommit.substring(0, 7));

            // Scan document files
            List<String> documentPaths = gitFileScanner.scanDocumentFiles(git, latestCommit);
            log.info("Found {} document files", documentPaths.size());

            // Process each document
            for (String path : documentPaths) {
                processDocument(git, repo, path, latestCommit, commitInfo);
            }

            return latestCommit;

        } catch (GitAPIException | IOException e) {
            log.error("Failed to sync repository: {}", repo.getFullName(), e);
            throw new RuntimeException("Sync failed: " + e.getMessage(), e);
        }
    }

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
