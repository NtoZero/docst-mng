package com.docst.gitrepo.service;

import com.docst.gitrepo.Repository;
import com.docst.gitrepo.repository.RepositoryRepository;
import com.docst.git.GitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 폴더 트리 조회 서비스.
 * Git 레포지토리의 디렉토리 구조를 조회한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FolderTreeService {

    private final GitService gitService;
    private final RepositoryRepository repositoryRepository;

    /**
     * 레포지토리의 폴더 트리를 조회한다.
     *
     * @param repositoryId 레포지토리 ID
     * @param maxDepth     최대 탐색 깊이 (기본 4, 최대 6)
     * @return 폴더 트리 항목 리스트
     */
    public List<FolderTreeItem> getFolderTree(UUID repositoryId, int maxDepth) {
        Repository repo = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repositoryId));

        // 깊이 제한
        int effectiveDepth = Math.max(1, Math.min(maxDepth, 6));

        try (Git git = gitService.cloneOrOpen(repo)) {
            String latestCommit = gitService.getLatestCommitSha(git, repo.getDefaultBranch());
            return buildFolderTree(git, latestCommit, effectiveDepth);
        } catch (Exception e) {
            log.error("Failed to get folder tree for repository: {}", repositoryId, e);
            return List.of();
        }
    }

    /**
     * 레포지토리에서 사용되는 파일 확장자 목록을 조회한다.
     *
     * @param repositoryId 레포지토리 ID
     * @return 확장자 목록 (점 포함, 예: [".md", ".java"])
     */
    public List<String> getFileExtensions(UUID repositoryId) {
        Repository repo = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repositoryId));

        Set<String> extensions = new TreeSet<>();

        try (Git git = gitService.cloneOrOpen(repo)) {
            String latestCommit = gitService.getLatestCommitSha(git, repo.getDefaultBranch());

            try (RevWalk revWalk = new RevWalk(git.getRepository())) {
                ObjectId commitId = git.getRepository().resolve(latestCommit);
                if (commitId == null) {
                    return List.of();
                }

                RevCommit commit = revWalk.parseCommit(commitId);
                try (TreeWalk treeWalk = new TreeWalk(git.getRepository())) {
                    treeWalk.addTree(commit.getTree());
                    treeWalk.setRecursive(true);

                    while (treeWalk.next()) {
                        String path = treeWalk.getPathString();
                        String fileName = path.substring(path.lastIndexOf('/') + 1);
                        int dotIndex = fileName.lastIndexOf('.');
                        if (dotIndex > 0) {
                            extensions.add(fileName.substring(dotIndex + 1).toLowerCase());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to get file extensions for repository: {}", repositoryId, e);
            return List.of();
        }

        return new ArrayList<>(extensions);
    }

    private List<FolderTreeItem> buildFolderTree(Git git, String commitSha, int maxDepth) throws Exception {
        Set<String> directories = new TreeSet<>();

        try (RevWalk revWalk = new RevWalk(git.getRepository())) {
            ObjectId commitId = git.getRepository().resolve(commitSha);
            if (commitId == null) {
                return List.of();
            }

            RevCommit commit = revWalk.parseCommit(commitId);
            try (TreeWalk treeWalk = new TreeWalk(git.getRepository())) {
                treeWalk.addTree(commit.getTree());
                treeWalk.setRecursive(true);

                while (treeWalk.next()) {
                    String path = treeWalk.getPathString();
                    // 디렉토리 경로 추출
                    int lastSlash = path.lastIndexOf('/');
                    if (lastSlash > 0) {
                        String dir = path.substring(0, lastSlash);
                        directories.add(dir);
                        // 상위 디렉토리들도 추가
                        while ((lastSlash = dir.lastIndexOf('/')) > 0) {
                            dir = dir.substring(0, lastSlash);
                            directories.add(dir);
                        }
                    }
                }
            }
        }

        return buildTreeStructure(directories, maxDepth);
    }

    private List<FolderTreeItem> buildTreeStructure(Set<String> directories, int maxDepth) {
        Map<String, FolderTreeItem> itemMap = new LinkedHashMap<>();
        List<FolderTreeItem> roots = new ArrayList<>();

        // 경로를 정렬하여 처리 (부모가 먼저 처리되도록)
        List<String> sortedDirs = new ArrayList<>(directories);
        Collections.sort(sortedDirs);

        for (String dir : sortedDirs) {
            int depth = dir.split("/").length;
            if (depth > maxDepth) {
                continue;
            }

            String name = dir.contains("/") ? dir.substring(dir.lastIndexOf('/') + 1) : dir;
            FolderTreeItem item = new FolderTreeItem(dir + "/", name, true, new ArrayList<>());
            itemMap.put(dir, item);

            int lastSlash = dir.lastIndexOf('/');
            if (lastSlash > 0) {
                String parentDir = dir.substring(0, lastSlash);
                FolderTreeItem parent = itemMap.get(parentDir);
                if (parent != null) {
                    parent.children().add(item);
                }
            } else {
                roots.add(item);
            }
        }

        return roots;
    }

    /**
     * 폴더 트리 항목.
     *
     * @param path        폴더 경로 (예: "docs/", "src/main/")
     * @param name        폴더 이름 (예: "docs", "main")
     * @param isDirectory 항상 true
     * @param children    자식 폴더 목록
     */
    public record FolderTreeItem(
            String path,
            String name,
            boolean isDirectory,
            List<FolderTreeItem> children
    ) {}
}
