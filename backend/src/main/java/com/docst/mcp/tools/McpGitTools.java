package com.docst.mcp.tools;

import com.docst.auth.SecurityUtils;
import com.docst.auth.UserPrincipal;
import com.docst.mcp.McpModels.*;
import com.docst.document.service.DocumentWriteService;
import com.docst.sync.service.SyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * MCP Git Tools.
 * Spring AI 1.1.0+ @Tool annotation 기반 Git 관련 MCP 도구.
 *
 * 제공 도구:
 * - sync_repository: 레포지토리 동기화
 * - create_document: 새 문서 생성
 * - update_document: 문서 수정
 * - push_to_remote: 원격 푸시
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class McpGitTools {

    private final SyncService syncService;
    private final DocumentWriteService documentWriteService;

    /**
     * 레포지토리 동기화.
     * 원격 Git에서 최신 변경사항을 가져와 문서 인덱스 업데이트.
     */
    @Tool(name = "sync_repository", description = "Trigger synchronization of a repository from remote Git. " +
          "Pulls latest changes and updates the document index. " +
          "Returns a job ID to track progress.")
    public SyncRepositoryResult syncRepository(
        @ToolParam(description = "Repository ID to sync (UUID format)") String repositoryId,
        @ToolParam(description = "Branch name to sync (default: main)", required = false) String branch
    ) {
        log.info("MCP Tool: syncRepository - repositoryId={}, branch={}", repositoryId, branch);

        UUID repoId = UUID.fromString(repositoryId);
        var job = syncService.startSync(repoId, branch);

        return new SyncRepositoryResult(job.getId(), job.getStatus().name());
    }

    /**
     * 새 문서 생성.
     * 선택적으로 즉시 커밋 가능.
     */
    @Tool(name = "create_document", description = "Create a new document in a repository. " +
          "The document will be created at the specified path. " +
          "Set createCommit=true to commit immediately, or false to stage only.")
    public CreateDocumentResult createDocument(
        @ToolParam(description = "Repository ID where the document will be created (UUID format)")
        String repositoryId,
        @ToolParam(description = "File path for the new document (e.g., 'docs/guide.md')")
        String path,
        @ToolParam(description = "Initial content of the document (Markdown supported)")
        String content,
        @ToolParam(description = "Commit message (optional)", required = false)
        String message,
        @ToolParam(description = "Branch name (default: main)", required = false)
        String branch,
        @ToolParam(description = "Create commit immediately (default: true)", required = false)
        Boolean createCommit
    ) {
        log.info("MCP Tool: createDocument - repositoryId={}, path={}", repositoryId, path);

        UserPrincipal principal = SecurityUtils.getCurrentUserPrincipal();
        if (principal == null) {
            throw new IllegalStateException("Authentication required for document creation");
        }

        var input = new CreateDocumentInput(
            UUID.fromString(repositoryId),
            path,
            content,
            message,
            branch,
            createCommit
        );

        return documentWriteService.createDocument(input, principal.id(), principal.displayName());
    }

    /**
     * 문서 수정.
     * 기존 문서의 내용을 업데이트하고 선택적으로 커밋.
     */
    @Tool(name = "update_document", description = "Update an existing document's content. " +
          "Replaces the document content with the provided text. " +
          "Set createCommit=true to commit immediately, or false to stage only.")
    public UpdateDocumentResult updateDocument(
        @ToolParam(description = "Document ID to update (UUID format)")
        String documentId,
        @ToolParam(description = "New content for the document")
        String content,
        @ToolParam(description = "Commit message (optional)", required = false)
        String message,
        @ToolParam(description = "Branch name (default: main)", required = false)
        String branch,
        @ToolParam(description = "Create commit immediately (default: true)", required = false)
        Boolean createCommit
    ) {
        log.info("MCP Tool: updateDocument - documentId={}", documentId);

        UserPrincipal principal = SecurityUtils.getCurrentUserPrincipal();
        if (principal == null) {
            throw new IllegalStateException("Authentication required for document update");
        }

        var input = new UpdateDocumentInput(
            UUID.fromString(documentId),
            content,
            message,
            branch,
            createCommit
        );

        return documentWriteService.updateDocument(input, principal.id(), principal.displayName());
    }

    /**
     * 원격 푸시.
     * 로컬 커밋을 원격 레포지토리로 푸시.
     */
    @Tool(name = "push_to_remote", description = "Push local commits to the remote repository. " +
          "Sends all pending local commits to the remote origin.")
    public PushToRemoteResult pushToRemote(
        @ToolParam(description = "Repository ID to push (UUID format)")
        String repositoryId,
        @ToolParam(description = "Branch name to push (default: main)", required = false)
        String branch
    ) {
        log.info("MCP Tool: pushToRemote - repositoryId={}, branch={}", repositoryId, branch);

        UUID repoId = UUID.fromString(repositoryId);
        String targetBranch = branch != null ? branch : "main";

        try {
            documentWriteService.pushToRemote(repoId, targetBranch);
            return new PushToRemoteResult(
                repoId,
                targetBranch,
                true,
                "Successfully pushed to remote"
            );
        } catch (Exception e) {
            log.error("Failed to push to remote: {}", e.getMessage(), e);
            return new PushToRemoteResult(
                repoId,
                targetBranch,
                false,
                "Push failed: " + e.getMessage()
            );
        }
    }
}
