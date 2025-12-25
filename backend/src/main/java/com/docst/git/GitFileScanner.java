package com.docst.git;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Git 파일 스캐너.
 * Git 레포지토리에서 문서 파일을 스캔하여 추출한다.
 */
@Component
@Slf4j
public class GitFileScanner {

    /**
     * 문서 파일로 인식할 패턴 목록.
     * README, docs 디렉토리, ADR, OpenAPI 등을 포함한다.
     */
    private static final List<Pattern> DOC_PATTERNS = List.of(
            Pattern.compile("^README\\.md$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^readme\\.md$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^docs/.*\\.md$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^docs/.*\\.adoc$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^documentation/.*\\.md$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^architecture/.*\\.md$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^adr/.*\\.md$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^adrs/.*\\.md$", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*openapi\\.(yaml|yml|json)$", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*swagger\\.(yaml|yml|json)$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^CHANGELOG\\.md$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^CONTRIBUTING\\.md$", Pattern.CASE_INSENSITIVE)
    );

    /**
     * 특정 커밋에서 문서 파일 목록을 스캔한다.
     *
     * @param git Git 인스턴스
     * @param commitSha 커밋 SHA
     * @return 문서 파일 경로 목록
     * @throws IOException I/O 오류 발생 시
     */
    public List<String> scanDocumentFiles(Git git, String commitSha) throws IOException {
        List<String> documentPaths = new ArrayList<>();

        try (RevWalk revWalk = new RevWalk(git.getRepository())) {
            ObjectId commitId = git.getRepository().resolve(commitSha);
            if (commitId == null) {
                log.warn("Commit not found: {}", commitSha);
                return documentPaths;
            }

            RevCommit commit = revWalk.parseCommit(commitId);
            RevTree tree = commit.getTree();

            try (TreeWalk treeWalk = new TreeWalk(git.getRepository())) {
                treeWalk.addTree(tree);
                treeWalk.setRecursive(true);

                while (treeWalk.next()) {
                    String path = treeWalk.getPathString();
                    if (isDocumentFile(path)) {
                        documentPaths.add(path);
                        log.debug("Found document: {}", path);
                    }
                }
            }
        }

        log.info("Scanned {} document files at commit {}", documentPaths.size(), commitSha.substring(0, 7));
        return documentPaths;
    }

    /**
     * 파일 경로가 문서 파일인지 확인한다.
     *
     * @param path 파일 경로
     * @return 문서 파일이면 true
     */
    public boolean isDocumentFile(String path) {
        for (Pattern pattern : DOC_PATTERNS) {
            if (pattern.matcher(path).matches()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 문서 파일 패턴 목록을 반환한다.
     *
     * @return 패턴 목록 (불변)
     */
    public List<Pattern> getDocPatterns() {
        return DOC_PATTERNS;
    }
}
