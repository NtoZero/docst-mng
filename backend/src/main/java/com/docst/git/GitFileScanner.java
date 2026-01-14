package com.docst.git;

import com.docst.gitrepo.RepositorySyncConfig;
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
import java.util.regex.PatternSyntaxException;

/**
 * Git 파일 스캐너.
 * Git 레포지토리에서 문서 파일을 스캔하여 추출한다.
 */
@Component
@Slf4j
public class GitFileScanner {

    /**
     * 문서 파일로 인식할 패턴 목록.
     * Markdown, AsciiDoc, OpenAPI 등 문서 형식을 포함한다.
     */
    private static final List<Pattern> DOC_PATTERNS = List.of(
            // 모든 Markdown 파일 (.md)
            Pattern.compile(".*\\.md$", Pattern.CASE_INSENSITIVE),
            // 모든 AsciiDoc 파일 (.adoc)
            Pattern.compile(".*\\.adoc$", Pattern.CASE_INSENSITIVE),
            // OpenAPI/Swagger 스펙 파일
            Pattern.compile(".*openapi\\.(yaml|yml|json)$", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*swagger\\.(yaml|yml|json)$", Pattern.CASE_INSENSITIVE)
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

    /**
     * 전체 파일을 재귀적으로 스캔한다 (모든 파일, 패턴 무관).
     *
     * @param git Git 인스턴스
     * @param commitSha 커밋 SHA
     * @return 모든 파일 경로 목록
     * @throws IOException I/O 오류 발생 시
     */
    public List<String> scanAllFiles(Git git, String commitSha) throws IOException {
        List<String> allPaths = new ArrayList<>();

        try (RevWalk revWalk = new RevWalk(git.getRepository())) {
            ObjectId commitId = git.getRepository().resolve(commitSha);
            if (commitId == null) {
                log.warn("Commit not found: {}", commitSha);
                return allPaths;
            }

            RevCommit commit = revWalk.parseCommit(commitId);
            RevTree tree = commit.getTree();

            try (TreeWalk treeWalk = new TreeWalk(git.getRepository())) {
                treeWalk.addTree(tree);
                treeWalk.setRecursive(true);

                while (treeWalk.next()) {
                    String path = treeWalk.getPathString();
                    allPaths.add(path);
                }
            }
        }

        log.info("Scanned {} total files at commit {}", allPaths.size(), commitSha.substring(0, 7));
        return allPaths;
    }

    /**
     * 변경된 파일 중 문서 파일만 필터링한다.
     *
     * @param changedFiles 변경된 파일 목록
     * @return 문서 파일만 포함된 목록
     */
    public List<GitCommitWalker.ChangedFile> filterDocumentFiles(List<GitCommitWalker.ChangedFile> changedFiles) {
        List<GitCommitWalker.ChangedFile> documentFiles = new ArrayList<>();

        for (GitCommitWalker.ChangedFile file : changedFiles) {
            // DELETED가 아닌 파일은 path 체크, DELETED는 oldPath 체크
            String pathToCheck = file.changeType() == GitCommitWalker.ChangeType.DELETED
                    ? file.oldPath()
                    : file.path();

            if (isDocumentFile(pathToCheck)) {
                documentFiles.add(file);
            }
        }

        log.debug("Filtered {} document files from {} changed files",
                documentFiles.size(), changedFiles.size());
        return documentFiles;
    }

    // ===== Phase 12: 동적 Sync 설정 지원 메서드 =====

    /**
     * RepositorySyncConfig에서 패턴 목록을 동적으로 생성한다.
     *
     * @param config 동기화 설정 (null이면 기본 패턴 사용)
     * @return 컴파일된 패턴 목록
     */
    public List<Pattern> buildPatterns(RepositorySyncConfig config) {
        if (config == null) {
            return DOC_PATTERNS;
        }

        List<Pattern> patterns = new ArrayList<>();

        // 1. 확장자 기반 패턴
        for (String ext : config.getFileExtensions()) {
            String escaped = Pattern.quote(ext);
            patterns.add(Pattern.compile(".*\\." + escaped + "$", Pattern.CASE_INSENSITIVE));
        }

        // 2. OpenAPI 옵션
        if (config.scanOpenApi()) {
            patterns.add(Pattern.compile(".*openapi\\.(yaml|yml|json)$", Pattern.CASE_INSENSITIVE));
        }

        // 3. Swagger 옵션
        if (config.scanSwagger()) {
            patterns.add(Pattern.compile(".*swagger\\.(yaml|yml|json)$", Pattern.CASE_INSENSITIVE));
        }

        // 4. 커스텀 패턴 (ReDoS 방지: 길이 제한)
        for (String customPattern : config.getCustomPatterns()) {
            if (customPattern == null || customPattern.length() > 100) {
                log.warn("Custom pattern ignored (null or too long): {}", customPattern);
                continue;
            }
            try {
                patterns.add(Pattern.compile(customPattern, Pattern.CASE_INSENSITIVE));
            } catch (PatternSyntaxException e) {
                log.warn("Invalid custom pattern ignored: {} - {}", customPattern, e.getMessage());
            }
        }

        // 패턴이 없으면 기본 패턴 사용
        return patterns.isEmpty() ? DOC_PATTERNS : patterns;
    }

    /**
     * 경로가 제외 대상인지 확인한다.
     *
     * @param path   파일 경로
     * @param config 동기화 설정
     * @return 제외 대상이면 true
     */
    public boolean isExcludedPath(String path, RepositorySyncConfig config) {
        if (config == null) {
            return false;
        }

        for (String excludePath : config.getExcludePaths()) {
            // 경로 시작 또는 하위 경로 체크
            if (path.startsWith(excludePath) || path.startsWith(excludePath + "/")
                    || path.contains("/" + excludePath + "/") || path.contains("/" + excludePath)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 경로가 포함 대상인지 확인한다.
     *
     * @param path   파일 경로
     * @param config 동기화 설정
     * @return 포함 대상이면 true (빈 목록이면 모든 경로 허용)
     */
    public boolean isIncludedPath(String path, RepositorySyncConfig config) {
        if (config == null || config.getIncludePaths().isEmpty()) {
            return true;  // 빈 목록이면 모든 경로 포함
        }

        for (String includePath : config.getIncludePaths()) {
            if (path.startsWith(includePath) || path.startsWith(includePath + "/")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 동적 설정을 사용하여 파일이 문서 파일인지 확인한다.
     *
     * @param path     파일 경로
     * @param patterns 패턴 목록
     * @param config   동기화 설정
     * @return 문서 파일이면 true
     */
    public boolean isDocumentFile(String path, List<Pattern> patterns, RepositorySyncConfig config) {
        // 1. 제외 경로 체크
        if (isExcludedPath(path, config)) {
            return false;
        }

        // 2. 포함 경로 체크
        if (!isIncludedPath(path, config)) {
            return false;
        }

        // 3. 패턴 매칭
        for (Pattern pattern : patterns) {
            if (pattern.matcher(path).matches()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 동적 설정을 사용하여 문서 파일을 스캔한다.
     *
     * @param git       Git 인스턴스
     * @param commitSha 커밋 SHA
     * @param config    동기화 설정
     * @return 문서 파일 경로 목록
     * @throws IOException I/O 오류 발생 시
     */
    public List<String> scanDocumentFiles(Git git, String commitSha, RepositorySyncConfig config)
            throws IOException {
        List<String> documentPaths = new ArrayList<>();
        List<Pattern> patterns = buildPatterns(config);

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
                    if (isDocumentFile(path, patterns, config)) {
                        documentPaths.add(path);
                        log.debug("Found document: {}", path);
                    }
                }
            }
        }

        log.info("Scanned {} document files with config at commit {}",
                documentPaths.size(), commitSha.substring(0, 7));
        return documentPaths;
    }

    /**
     * 동적 설정을 사용하여 변경된 파일 중 문서 파일만 필터링한다.
     *
     * @param changedFiles 변경된 파일 목록
     * @param config       동기화 설정
     * @return 문서 파일만 포함된 목록
     */
    public List<GitCommitWalker.ChangedFile> filterDocumentFiles(
            List<GitCommitWalker.ChangedFile> changedFiles, RepositorySyncConfig config) {
        List<GitCommitWalker.ChangedFile> documentFiles = new ArrayList<>();
        List<Pattern> patterns = buildPatterns(config);

        for (GitCommitWalker.ChangedFile file : changedFiles) {
            // DELETED가 아닌 파일은 path 체크, DELETED는 oldPath 체크
            String pathToCheck = file.changeType() == GitCommitWalker.ChangeType.DELETED
                    ? file.oldPath()
                    : file.path();

            if (isDocumentFile(pathToCheck, patterns, config)) {
                documentFiles.add(file);
            }
        }

        log.debug("Filtered {} document files from {} changed files with config",
                documentFiles.size(), changedFiles.size());
        return documentFiles;
    }
}
