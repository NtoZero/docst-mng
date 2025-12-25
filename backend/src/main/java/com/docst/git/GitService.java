package com.docst.git;

import com.docst.domain.Repository;
import com.docst.domain.Repository.RepoProvider;
import com.docst.repository.RepositoryRepository;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Git 서비스.
 * JGit을 사용하여 Git 레포지토리 조작 기능을 제공한다.
 */
@Service
@Slf4j
public class GitService {

    private final RepositoryRepository repositoryRepository;
    private final Path gitRootPath;

    /**
     * GitService 생성자.
     *
     * @param repositoryRepository 레포지토리 리포지토리
     * @param gitRootPath Git 로컬 저장소 루트 경로
     */
    public GitService(RepositoryRepository repositoryRepository,
                      @Value("${docst.git.root-path:/data/git}") String gitRootPath) {
        this.repositoryRepository = repositoryRepository;
        this.gitRootPath = Path.of(gitRootPath);
    }

    /**
     * 레포지토리 ID로 로컬 경로를 반환한다.
     *
     * @param repositoryId 레포지토리 ID
     * @return 로컬 경로
     */
    public Path getLocalPath(UUID repositoryId) {
        return gitRootPath.resolve(repositoryId.toString());
    }

    /**
     * 레포지토리를 clone하거나 이미 존재하면 open한다.
     *
     * @param repo 레포지토리 엔티티
     * @return Git 인스턴스
     * @throws GitAPIException Git 작업 실패 시
     * @throws IOException I/O 오류 발생 시
     */
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

    /**
     * 원격 레포지토리에서 변경사항을 가져온다.
     *
     * @param git Git 인스턴스
     * @param branch 브랜치명
     * @throws GitAPIException Git 작업 실패 시
     */
    public void fetch(Git git, String branch) throws GitAPIException {
        log.info("Fetching branch: {}", branch);
        git.fetch()
                .setRemote("origin")
                .call();
    }

    /**
     * 특정 브랜치로 checkout한다.
     *
     * @param git Git 인스턴스
     * @param branch 브랜치명
     * @throws GitAPIException Git 작업 실패 시
     */
    public void checkout(Git git, String branch) throws GitAPIException {
        log.info("Checking out branch: {}", branch);
        git.checkout()
                .setName(branch)
                .setCreateBranch(false)
                .call();
    }

    /**
     * 브랜치의 최신 커밋 SHA를 반환한다.
     *
     * @param git Git 인스턴스
     * @param branch 브랜치명
     * @return 커밋 SHA
     * @throws IOException I/O 오류 발생 시
     * @throws IllegalStateException 브랜치가 존재하지 않을 경우
     */
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

    /**
     * 특정 커밋에서 파일 내용을 읽는다.
     *
     * @param git Git 인스턴스
     * @param commitSha 커밋 SHA
     * @param path 파일 경로
     * @return 파일 내용 (존재하지 않으면 empty)
     * @throws IOException I/O 오류 발생 시
     */
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

    /**
     * 커밋 정보를 조회한다.
     *
     * @param git Git 인스턴스
     * @param commitSha 커밋 SHA
     * @return 커밋 정보 (존재하지 않으면 null)
     * @throws IOException I/O 오류 발생 시
     */
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

    /**
     * 레포지토리의 clone URL을 반환한다.
     */
    private String getCloneUrl(Repository repo) {
        if (repo.getProvider() == RepoProvider.LOCAL) {
            return repo.getLocalMirrorPath();
        }
        return repo.getCloneUrl();
    }

    /**
     * 커밋 정보를 나타내는 레코드.
     *
     * @param sha 커밋 SHA
     * @param authorName 작성자 이름
     * @param authorEmail 작성자 이메일
     * @param committedAt 커밋 시각
     * @param message 커밋 메시지
     */
    public record CommitInfo(
            String sha,
            String authorName,
            String authorEmail,
            Instant committedAt,
            String message
    ) {}
}
