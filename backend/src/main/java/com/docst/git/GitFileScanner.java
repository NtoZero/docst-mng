package com.docst.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class GitFileScanner {

    private static final Logger log = LoggerFactory.getLogger(GitFileScanner.class);

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

    public boolean isDocumentFile(String path) {
        for (Pattern pattern : DOC_PATTERNS) {
            if (pattern.matcher(path).matches()) {
                return true;
            }
        }
        return false;
    }

    public List<Pattern> getDocPatterns() {
        return DOC_PATTERNS;
    }
}
