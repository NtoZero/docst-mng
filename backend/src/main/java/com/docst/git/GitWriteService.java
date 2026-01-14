package com.docst.git;

import com.docst.gitrepo.Repository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.RefSpec;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Git 쓰기 작업 서비스.
 * 파일 쓰기, 커밋, 푸시 등 Git 변경 작업을 담당한다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GitWriteService {

    private final GitService gitService;

    /**
     * 파일 내용을 로컬에 쓴다.
     * 부모 디렉토리가 없으면 자동으로 생성한다.
     *
     * @param filePath 파일 경로
     * @param content 파일 내용
     * @throws IOException 파일 쓰기 실패 시
     */
    public void writeFile(Path filePath, String content) throws IOException {
        // 부모 디렉토리 생성
        Path parent = filePath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
            log.debug("Created parent directories: {}", parent);
        }

        // 파일 쓰기
        Files.writeString(filePath, content, StandardCharsets.UTF_8);
        log.info("Wrote file: {}", filePath);
    }

    /**
     * 파일을 스테이징하고 커밋한다.
     * Bot이 커밋하되, 메시지에 실제 사용자를 명시한다.
     *
     * @param repo 레포지토리 엔티티
     * @param relativePath 파일 상대 경로 (레포지토리 루트 기준)
     * @param message 커밋 메시지
     * @param branch 대상 브랜치 (null이면 현재 브랜치)
     * @param username 실제 작업 수행 사용자 (커밋 메시지에 포함)
     * @return 생성된 커밋 SHA
     * @throws GitAPIException Git 작업 실패 시
     * @throws IOException I/O 오류 발생 시
     */
    public String commitFile(
            Repository repo,
            String relativePath,
            String message,
            String branch,
            String username
    ) throws GitAPIException, IOException {

        try (Git git = gitService.cloneOrOpen(repo)) {
            // 1. Checkout branch (지정된 경우)
            if (branch != null && !branch.isEmpty()) {
                log.info("Checking out branch: {}", branch);
                gitService.checkout(git, branch);
            }

            // 2. Stage file
            log.info("Staging file: {}", relativePath);
            git.add()
                    .addFilepattern(relativePath)
                    .call();

            // 3. Commit
            String commitMessage = message;
            if (username != null && !username.isEmpty()) {
                commitMessage = message + "\n\nby @" + username;
            }

            log.info("Committing file: {}", relativePath);
            RevCommit commit = git.commit()
                    .setMessage(commitMessage)
                    .setAuthor("Docst Bot", "bot@docst.com")
                    .call();

            String commitSha = commit.getName();
            log.info("Created commit: {} - {}", commitSha.substring(0, 8), message);

            return commitSha;
        }
    }

    /**
     * 로컬 커밋을 원격 레포지토리로 푸시한다.
     *
     * @param repo 레포지토리 엔티티
     * @param branch 푸시할 브랜치 (기본: main)
     * @throws GitAPIException Git 작업 실패 시
     * @throws IOException I/O 오류 발생 시
     */
    public void pushToRemote(Repository repo, String branch) throws GitAPIException, IOException {
        String targetBranch = branch != null && !branch.isEmpty() ? branch : "main";

        try (Git git = gitService.cloneOrOpen(repo)) {
            log.info("Pushing to remote: {}/{}", repo.getFullName(), targetBranch);

            git.push()
                    .setRemote("origin")
                    .setRefSpecs(new RefSpec(targetBranch + ":" + targetBranch))
                    .setCredentialsProvider(gitService.getCredentialsProvider(repo))
                    .call();

            log.info("Successfully pushed to remote: {}/{}", repo.getFullName(), targetBranch);
        }
    }

    /**
     * 레포지토리의 로컬 경로를 반환한다.
     * GitService의 getLocalPath를 위임한다.
     *
     * @param repo 레포지토리 엔티티
     * @return 로컬 경로
     */
    public Path getLocalPath(Repository repo) {
        return gitService.getLocalPath(repo.getId());
    }
}
