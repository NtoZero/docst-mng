package com.docst.llm.tools;

import com.docst.api.ApiModels.SyncMode;
import com.docst.git.BranchService;
import com.docst.service.GitSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Git 관련 LLM Tools.
 *
 * Spring AI 1.1.0+ @Tool annotation 기반 선언적 Tool 정의.
 * 브랜치 관리 및 동기화 기능을 LLM에 제공한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GitTools {

    private final BranchService branchService;
    private final GitSyncService gitSyncService;

    /**
     * 레포지토리 브랜치 목록 조회
     */
    @Tool(description = "List all branches in a repository. Returns branch names.")
    public List<String> listBranches(
        @ToolParam(description = "The repository ID") String repositoryId
    ) {
        log.info("Tool: listBranches - repositoryId={}", repositoryId);
        UUID repoId = UUID.fromString(repositoryId);
        return branchService.listBranches(repoId);
    }

    /**
     * 새 브랜치 생성
     */
    @Tool(description = "Create a new branch from an existing branch. Returns the created branch ref.")
    public String createBranch(
        @ToolParam(description = "The repository ID") String repositoryId,
        @ToolParam(description = "Name of the new branch") String branchName,
        @ToolParam(description = "Source branch to create from (default: main)", required = false) String fromBranch
    ) {
        log.info("Tool: createBranch - name={}, from={}, repositoryId={}",
            branchName, fromBranch, repositoryId);

        UUID repoId = UUID.fromString(repositoryId);
        String sourceBranch = (fromBranch != null && !fromBranch.isBlank()) ? fromBranch : "main";

        return branchService.createBranch(repoId, branchName, sourceBranch);
    }

    /**
     * 브랜치 전환
     */
    @Tool(description = "Switch to a different branch in the repository.")
    public String switchBranch(
        @ToolParam(description = "The repository ID") String repositoryId,
        @ToolParam(description = "Branch name to switch to") String branchName
    ) {
        log.info("Tool: switchBranch - branchName={}, repositoryId={}", branchName, repositoryId);

        UUID repoId = UUID.fromString(repositoryId);
        branchService.switchBranch(repoId, branchName);

        return "Switched to branch: " + branchName;
    }

    /**
     * 현재 브랜치 조회
     */
    @Tool(description = "Get the current branch name of the repository.")
    public String getCurrentBranch(
        @ToolParam(description = "The repository ID") String repositoryId
    ) {
        log.info("Tool: getCurrentBranch - repositoryId={}", repositoryId);

        UUID repoId = UUID.fromString(repositoryId);
        return branchService.getCurrentBranch(repoId);
    }

    /**
     * 레포지토리 동기화
     */
    @Tool(description = "Synchronize a repository with its remote origin. Pulls latest changes and updates the document index.")
    public String syncRepository(
        @ToolParam(description = "The repository ID to sync") String repositoryId,
        @ToolParam(description = "Branch name to sync (default: main)", required = false) String branch
    ) {
        log.info("Tool: syncRepository - repositoryId={}, branch={}", repositoryId, branch);

        UUID repoId = UUID.fromString(repositoryId);
        String targetBranch = (branch != null && !branch.isBlank()) ? branch : "main";

        // SyncJob을 생성하고 동기화 시작
        UUID jobId = UUID.randomUUID();
        String lastCommitSha = gitSyncService.syncRepository(
            jobId,
            repoId,
            targetBranch,
            SyncMode.INCREMENTAL,  // 증분 동기화
            null,  // targetCommitSha
            null,  // lastSyncedCommit
            false  // enableEmbedding
        );

        return "Repository synced. Last commit: " + lastCommitSha;
    }
}
