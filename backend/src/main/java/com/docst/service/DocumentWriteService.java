package com.docst.service;

import com.docst.api.ApiModels.SyncMode;
import com.docst.auth.PermissionService;
import com.docst.domain.Document;
import com.docst.domain.ProjectRole;
import com.docst.domain.Repository;
import com.docst.git.GitWriteService;
import com.docst.mcp.McpModels.CreateDocumentInput;
import com.docst.mcp.McpModels.CreateDocumentResult;
import com.docst.mcp.McpModels.UpdateDocumentInput;
import com.docst.mcp.McpModels.UpdateDocumentResult;
import com.docst.repository.RepositoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.UUID;

/**
 * 문서 쓰기 서비스.
 * MCP write 도구들의 비즈니스 로직을 처리한다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentWriteService {

    private final DocumentService documentService;
    private final GitWriteService gitWriteService;
    private final GitSyncService gitSyncService;
    private final RepositoryRepository repositoryRepository;
    private final PermissionService permissionService;

    /**
     * 새 문서를 생성하고 선택적으로 커밋한다.
     *
     * @param input 생성 요청 (repositoryId, path, content, message, branch, createCommit)
     * @param userId 실제 작업 수행 사용자 ID (권한 검사용)
     * @param username 실제 작업 수행 사용자 이름 (커밋 메시지에 포함)
     * @return 생성 결과
     * @throws IllegalArgumentException 레포지토리가 존재하지 않을 경우
     * @throws SecurityException 권한이 없을 경우
     * @throws RuntimeException Git 작업 실패 시
     */
    @Transactional
    public CreateDocumentResult createDocument(CreateDocumentInput input, UUID userId, String username) {
        // 1. 레포지토리 조회
        Repository repo = repositoryRepository.findById(input.repositoryId())
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + input.repositoryId()));

        // 2. 권한 검사
        permissionService.requireRepositoryPermission(
                userId,
                input.repositoryId(),
                ProjectRole.EDITOR
        );

        // 3. 로컬 경로 계산
        Path localPath = gitWriteService.getLocalPath(repo);
        Path filePath = localPath.resolve(input.path());

        // 4. 파일 쓰기
        try {
            gitWriteService.writeFile(filePath, input.content());
        } catch (Exception e) {
            throw new RuntimeException("Failed to write file: " + e.getMessage(), e);
        }

        // 5. 커밋 (선택적)
        String commitSha = null;
        boolean committed = false;

        if (Boolean.TRUE.equals(input.createCommit())) {
            try {
                String message = input.message() != null && !input.message().isEmpty()
                        ? input.message()
                        : "Create " + input.path();

                commitSha = gitWriteService.commitFile(
                        repo,
                        input.path(),
                        message,
                        input.branch(),
                        username
                );

                committed = true;
                log.info("Created commit for new document: {} ({})", input.path(), commitSha.substring(0, 8));

                // 6. 동기화 (커밋한 경우)
                gitSyncService.syncRepository(
                        null, // jobId - 비동기 작업 아님
                        repo.getId(),
                        input.branch(),
                        SyncMode.SPECIFIC_COMMIT,
                        commitSha,
                        null,
                        true // enableEmbedding - 기본값
                );

                log.info("Synced new document to database: {}", input.path());
            } catch (Exception e) {
                throw new RuntimeException("Failed to commit or sync: " + e.getMessage(), e);
            }
        }

        // 7. 문서 ID 조회 (동기화 후)
        UUID documentId = null;
        if (committed) {
            documentId = documentService.findByProjectId(repo.getProject().getId()).stream()
                    .filter(doc -> doc.getPath().equals(input.path()))
                    .findFirst()
                    .map(Document::getId)
                    .orElse(null);
        }

        return new CreateDocumentResult(
                documentId,
                input.path(),
                commitSha,
                committed,
                committed ? "Document created and committed" : "Document created (not committed)"
        );
    }

    /**
     * 기존 문서를 수정하고 선택적으로 커밋한다.
     *
     * @param input 수정 요청 (documentId, content, message, branch, createCommit)
     * @param userId 실제 작업 수행 사용자 ID (권한 검사용)
     * @param username 실제 작업 수행 사용자 이름 (커밋 메시지에 포함)
     * @return 수정 결과
     * @throws IllegalArgumentException 문서가 존재하지 않을 경우
     * @throws SecurityException 권한이 없을 경우
     * @throws RuntimeException Git 작업 실패 시
     */
    @Transactional
    public UpdateDocumentResult updateDocument(UpdateDocumentInput input, UUID userId, String username) {
        // 1. 문서 조회
        Document doc = documentService.findById(input.documentId())
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + input.documentId()));

        Repository repo = doc.getRepository();

        // 2. 권한 검사
        permissionService.requireDocumentPermission(
                userId,
                input.documentId(),
                ProjectRole.EDITOR
        );

        // 3. 로컬 경로 계산
        Path localPath = gitWriteService.getLocalPath(repo);
        Path filePath = localPath.resolve(doc.getPath());

        // 4. 파일 쓰기
        try {
            gitWriteService.writeFile(filePath, input.content());
        } catch (Exception e) {
            throw new RuntimeException("Failed to write file: " + e.getMessage(), e);
        }

        // 5. 커밋 (선택적)
        String commitSha = null;
        boolean committed = false;

        if (Boolean.TRUE.equals(input.createCommit())) {
            try {
                String message = input.message() != null && !input.message().isEmpty()
                        ? input.message()
                        : "Update " + doc.getPath();

                commitSha = gitWriteService.commitFile(
                        repo,
                        doc.getPath(),
                        message,
                        input.branch(),
                        username
                );

                committed = true;
                log.info("Created commit for updated document: {} ({})", doc.getPath(), commitSha.substring(0, 8));

                // 6. 동기화 (커밋한 경우)
                gitSyncService.syncRepository(
                        null, // jobId
                        repo.getId(),
                        input.branch(),
                        SyncMode.SPECIFIC_COMMIT,
                        commitSha,
                        null,
                        true // enableEmbedding
                );

                log.info("Synced updated document to database: {}", doc.getPath());
            } catch (Exception e) {
                throw new RuntimeException("Failed to commit or sync: " + e.getMessage(), e);
            }
        }

        return new UpdateDocumentResult(
                doc.getId(),
                doc.getPath(),
                commitSha,
                committed,
                committed ? "Document updated and committed" : "Document updated (not committed)"
        );
    }

    /**
     * 원격 레포지토리로 푸시한다.
     *
     * @param repositoryId 레포지토리 ID
     * @param branch 푸시할 브랜치
     * @throws IllegalArgumentException 레포지토리가 존재하지 않을 경우
     * @throws RuntimeException Git 작업 실패 시
     */
    public void pushToRemote(UUID repositoryId, String branch) {
        Repository repo = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repositoryId));

        try {
            gitWriteService.pushToRemote(repo, branch);
            log.info("Successfully pushed repository: {} to remote", repo.getFullName());
        } catch (Exception e) {
            throw new RuntimeException("Failed to push to remote: " + e.getMessage(), e);
        }
    }
}
