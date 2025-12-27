package com.docst.git;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Git 커밋 워커.
 * Git 커밋 히스토리를 순회하고 변경 파일을 추출한다.
 */
@Component
@Slf4j
public class GitCommitWalker {

    /**
     * 커밋 목록을 조회한다.
     *
     * @param git Git 인스턴스
     * @param branch 브랜치명
     * @param skip 건너뛸 커밋 수
     * @param limit 조회할 최대 커밋 수
     * @return 커밋 정보 목록
     * @throws IOException I/O 오류 발생 시
     */
    public List<CommitInfo> listCommits(Git git, String branch, int skip, int limit) throws IOException {
        List<CommitInfo> commits = new ArrayList<>();
        Repository repo = git.getRepository();

        try (RevWalk revWalk = new RevWalk(repo)) {
            // 브랜치의 최신 커밋부터 시작
            Ref ref = repo.findRef("refs/heads/" + branch);
            if (ref == null) {
                ref = repo.findRef("refs/remotes/origin/" + branch);
            }
            if (ref == null) {
                log.warn("Branch not found: {}", branch);
                return commits;
            }

            RevCommit start = revWalk.parseCommit(ref.getObjectId());
            revWalk.markStart(start);

            // skip 적용
            int skipped = 0;
            for (RevCommit commit : revWalk) {
                if (skipped < skip) {
                    skipped++;
                    continue;
                }

                commits.add(toCommitInfo(commit));

                if (commits.size() >= limit) {
                    break;
                }
            }
        } catch (Exception e) {
            log.error("Failed to list commits for branch {}: {}", branch, e.getMessage());
            throw new IOException("Failed to list commits", e);
        }

        log.info("Listed {} commits from branch {} (skip={}, limit={})", commits.size(), branch, skip, limit);
        return commits;
    }

    /**
     * 특정 커밋의 변경된 파일 목록을 조회한다.
     *
     * @param git Git 인스턴스
     * @param commitSha 커밋 SHA
     * @return 변경된 파일 목록
     * @throws IOException I/O 오류 발생 시
     */
    public List<ChangedFile> getChangedFiles(Git git, String commitSha) throws IOException {
        Repository repo = git.getRepository();
        List<ChangedFile> changedFiles = new ArrayList<>();

        try (RevWalk revWalk = new RevWalk(repo)) {
            ObjectId commitId = repo.resolve(commitSha);
            if (commitId == null) {
                log.warn("Commit not found: {}", commitSha);
                return changedFiles;
            }

            RevCommit commit = revWalk.parseCommit(commitId);
            RevCommit[] parents = commit.getParents();

            if (parents.length == 0) {
                // 최초 커밋 - 모든 파일이 ADDED
                log.debug("Initial commit, all files are added");
                return changedFiles; // TODO: 초기 커밋 처리
            }

            // 첫 번째 부모와 비교
            RevCommit parent = revWalk.parseCommit(parents[0]);
            changedFiles = diffCommits(repo, parent, commit);

        } catch (Exception e) {
            log.error("Failed to get changed files for commit {}: {}", commitSha, e.getMessage());
            throw new IOException("Failed to get changed files", e);
        }

        log.debug("Found {} changed files in commit {}", changedFiles.size(), commitSha.substring(0, 7));
        return changedFiles;
    }

    /**
     * 두 커밋 간 변경된 파일 목록을 조회한다.
     *
     * @param git Git 인스턴스
     * @param fromSha 시작 커밋 SHA
     * @param toSha 종료 커밋 SHA
     * @return 변경된 파일 목록
     * @throws IOException I/O 오류 발생 시
     */
    public List<ChangedFile> getChangedFilesBetween(Git git, String fromSha, String toSha) throws IOException {
        Repository repo = git.getRepository();
        List<ChangedFile> changedFiles = new ArrayList<>();

        try (RevWalk revWalk = new RevWalk(repo)) {
            ObjectId fromId = repo.resolve(fromSha);
            ObjectId toId = repo.resolve(toSha);

            if (fromId == null || toId == null) {
                log.warn("Commit not found: from={}, to={}", fromSha, toSha);
                return changedFiles;
            }

            RevCommit fromCommit = revWalk.parseCommit(fromId);
            RevCommit toCommit = revWalk.parseCommit(toId);

            changedFiles = diffCommits(repo, fromCommit, toCommit);

        } catch (Exception e) {
            log.error("Failed to get changed files between {} and {}: {}",
                    fromSha, toSha, e.getMessage());
            throw new IOException("Failed to get changed files between commits", e);
        }

        log.info("Found {} changed files between {} and {}",
                changedFiles.size(), fromSha.substring(0, 7), toSha.substring(0, 7));
        return changedFiles;
    }

    /**
     * fromSha ~ toSha 사이의 모든 커밋을 순회한다.
     *
     * @param git Git 인스턴스
     * @param fromSha 시작 커밋 SHA (exclusive)
     * @param toSha 종료 커밋 SHA (inclusive)
     * @return 커밋 정보 목록
     * @throws IOException I/O 오류 발생 시
     */
    public List<CommitInfo> walkCommits(Git git, String fromSha, String toSha) throws IOException {
        Repository repo = git.getRepository();
        List<CommitInfo> commits = new ArrayList<>();

        try (RevWalk revWalk = new RevWalk(repo)) {
            ObjectId fromId = repo.resolve(fromSha);
            ObjectId toId = repo.resolve(toSha);

            if (fromId == null || toId == null) {
                log.warn("Commit not found: from={}, to={}", fromSha, toSha);
                return commits;
            }

            RevCommit toCommit = revWalk.parseCommit(toId);
            RevCommit fromCommit = revWalk.parseCommit(fromId);

            revWalk.markStart(toCommit);
            revWalk.markUninteresting(fromCommit);

            for (RevCommit commit : revWalk) {
                commits.add(toCommitInfo(commit));
            }

        } catch (Exception e) {
            log.error("Failed to walk commits between {} and {}: {}",
                    fromSha, toSha, e.getMessage());
            throw new IOException("Failed to walk commits", e);
        }

        log.info("Walked {} commits between {} and {}",
                commits.size(), fromSha.substring(0, 7), toSha.substring(0, 7));
        return commits;
    }

    /**
     * 두 커밋 간 diff를 계산한다.
     */
    private List<ChangedFile> diffCommits(Repository repo, RevCommit oldCommit, RevCommit newCommit)
            throws IOException {
        List<ChangedFile> changedFiles = new ArrayList<>();

        try (ObjectReader reader = repo.newObjectReader();
             DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {

            diffFormatter.setRepository(repo);

            CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
            oldTreeIter.reset(reader, oldCommit.getTree());

            CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
            newTreeIter.reset(reader, newCommit.getTree());

            List<DiffEntry> diffs = diffFormatter.scan(oldTreeIter, newTreeIter);

            for (DiffEntry diff : diffs) {
                ChangeType changeType = switch (diff.getChangeType()) {
                    case ADD -> ChangeType.ADDED;
                    case MODIFY -> ChangeType.MODIFIED;
                    case DELETE -> ChangeType.DELETED;
                    case RENAME -> ChangeType.RENAMED;
                    case COPY -> ChangeType.MODIFIED; // COPY는 MODIFIED로 처리
                };

                String path = diff.getNewPath();
                String oldPath = diff.getOldPath();

                // DELETE의 경우 oldPath 사용
                if (changeType == ChangeType.DELETED) {
                    path = oldPath;
                }

                changedFiles.add(new ChangedFile(path, changeType, oldPath));
            }
        }

        return changedFiles;
    }

    /**
     * RevCommit을 CommitInfo로 변환한다.
     */
    private CommitInfo toCommitInfo(RevCommit commit) {
        return new CommitInfo(
                commit.getName(),
                commit.getName().substring(0, 7),
                commit.getFullMessage(),
                commit.getShortMessage(),
                commit.getAuthorIdent().getName(),
                commit.getAuthorIdent().getEmailAddress(),
                commit.getAuthorIdent().getWhen().toInstant()
        );
    }

    /**
     * 커밋 정보를 나타내는 레코드.
     */
    public record CommitInfo(
            String sha,
            String shortSha,
            String fullMessage,
            String shortMessage,
            String authorName,
            String authorEmail,
            Instant committedAt
    ) {}

    /**
     * 변경된 파일 정보를 나타내는 레코드.
     */
    public record ChangedFile(
            String path,
            ChangeType changeType,
            String oldPath  // RENAMED인 경우 이전 경로
    ) {}

    /**
     * 파일 변경 타입.
     */
    public enum ChangeType {
        ADDED,
        MODIFIED,
        DELETED,
        RENAMED
    }
}
