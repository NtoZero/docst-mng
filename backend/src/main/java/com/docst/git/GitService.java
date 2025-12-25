package com.docst.git;

import com.docst.domain.Repository;
import com.docst.domain.Repository.RepoProvider;
import com.docst.repository.RepositoryRepository;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

@Service
public class GitService {

    private static final Logger log = LoggerFactory.getLogger(GitService.class);

    private final RepositoryRepository repositoryRepository;
    private final Path gitRootPath;

    public GitService(RepositoryRepository repositoryRepository,
                      @Value("${docst.git.root-path:/data/git}") String gitRootPath) {
        this.repositoryRepository = repositoryRepository;
        this.gitRootPath = Path.of(gitRootPath);
    }

    public Path getLocalPath(UUID repositoryId) {
        return gitRootPath.resolve(repositoryId.toString());
    }

    public Git cloneOrOpen(Repository repo) throws GitAPIException, IOException {
        Path localPath = getLocalPath(repo.getId());
        File localDir = localPath.toFile();

        if (localDir.exists() && new File(localDir, ".git").exists()) {
            log.info("Opening existing repository: {}", localPath);
            return Git.open(localDir);
        }

        String cloneUrl = getCloneUrl(repo);
        log.info("Cloning repository: {} to {}", cloneUrl, localPath);

        return Git.cloneRepository()
                .setURI(cloneUrl)
                .setDirectory(localDir)
                .setBare(false)
                .call();
    }

    public void fetch(Git git, String branch) throws GitAPIException {
        log.info("Fetching branch: {}", branch);
        git.fetch()
                .setRemote("origin")
                .call();
    }

    public void checkout(Git git, String branch) throws GitAPIException {
        log.info("Checking out branch: {}", branch);
        git.checkout()
                .setName(branch)
                .setCreateBranch(false)
                .call();
    }

    public String getLatestCommitSha(Git git, String branch) throws IOException {
        Ref ref = git.getRepository().findRef("refs/heads/" + branch);
        if (ref == null) {
            ref = git.getRepository().findRef("refs/remotes/origin/" + branch);
        }
        if (ref == null) {
            throw new IllegalStateException("Branch not found: " + branch);
        }
        return ref.getObjectId().getName();
    }

    public Optional<String> getFileContent(Git git, String commitSha, String path) throws IOException {
        try (RevWalk revWalk = new RevWalk(git.getRepository())) {
            ObjectId commitId = git.getRepository().resolve(commitSha);
            if (commitId == null) {
                return Optional.empty();
            }

            RevCommit commit = revWalk.parseCommit(commitId);
            RevTree tree = commit.getTree();

            try (TreeWalk treeWalk = new TreeWalk(git.getRepository())) {
                treeWalk.addTree(tree);
                treeWalk.setRecursive(true);
                treeWalk.setFilter(PathFilter.create(path));

                if (!treeWalk.next()) {
                    return Optional.empty();
                }

                ObjectId objectId = treeWalk.getObjectId(0);
                ObjectLoader loader = git.getRepository().open(objectId);
                byte[] bytes = loader.getBytes();
                return Optional.of(new String(bytes, StandardCharsets.UTF_8));
            }
        }
    }

    public CommitInfo getCommitInfo(Git git, String commitSha) throws IOException {
        try (RevWalk revWalk = new RevWalk(git.getRepository())) {
            ObjectId commitId = git.getRepository().resolve(commitSha);
            if (commitId == null) {
                return null;
            }
            RevCommit commit = revWalk.parseCommit(commitId);
            return new CommitInfo(
                    commit.getName(),
                    commit.getAuthorIdent().getName(),
                    commit.getAuthorIdent().getEmailAddress(),
                    commit.getAuthorIdent().getWhen().toInstant(),
                    commit.getShortMessage()
            );
        }
    }

    private String getCloneUrl(Repository repo) {
        if (repo.getProvider() == RepoProvider.LOCAL) {
            return repo.getLocalMirrorPath();
        }
        return repo.getCloneUrl();
    }

    public record CommitInfo(
            String sha,
            String authorName,
            String authorEmail,
            java.time.Instant committedAt,
            String message
    ) {}
}
