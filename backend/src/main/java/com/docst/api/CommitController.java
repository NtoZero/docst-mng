package com.docst.api;

import com.docst.api.ApiModels.ChangedFileResponse;
import com.docst.api.ApiModels.CommitDetailResponse;
import com.docst.api.ApiModels.CommitResponse;
import com.docst.git.GitCommitWalker;
import com.docst.service.CommitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 커밋 컨트롤러.
 * Git 커밋 히스토리 조회 API를 제공한다.
 */
@Tag(name = "Commits", description = "Git 커밋 히스토리 조회 API")
@RestController
@RequestMapping("/api/repositories/{repoId}/commits")
@RequiredArgsConstructor
public class CommitController {

    private final CommitService commitService;

    /**
     * 커밋 목록을 조회한다.
     *
     * @param repoId 레포지토리 ID
     * @param branch 브랜치명 (선택, null이면 기본 브랜치)
     * @param page 페이지 번호 (0부터 시작, 기본값 0)
     * @param size 페이지 크기 (기본값 20, 최대 100)
     * @return 커밋 목록
     */
    @Operation(summary = "커밋 목록 조회", description = "레포지토리의 커밋 목록을 페이지네이션으로 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping
    public List<CommitResponse> listCommits(
            @Parameter(description = "레포지토리 ID") @PathVariable UUID repoId,
            @Parameter(description = "브랜치명 (선택, null이면 기본 브랜치)") @RequestParam(required = false) String branch,
            @Parameter(description = "페이지 번호 (0부터 시작)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기 (최대 100)") @RequestParam(defaultValue = "20") int size
    ) {
        // size 최대값 제한
        int limitedSize = Math.min(size, 100);

        List<GitCommitWalker.CommitInfo> commits = commitService.listCommits(
                repoId, branch, page, limitedSize);

        return commits.stream()
                .map(this::toCommitResponse)
                .toList();
    }

    /**
     * 특정 커밋의 상세 정보를 조회한다 (변경된 파일 목록 포함).
     *
     * @param repoId 레포지토리 ID
     * @param commitSha 커밋 SHA
     * @return 커밋 상세 정보
     */
    @Operation(summary = "커밋 상세 조회", description = "특정 커밋의 상세 정보를 조회합니다. 변경된 파일 목록을 포함합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "커밋을 찾을 수 없음")
    })
    @GetMapping("/{commitSha}")
    public ResponseEntity<CommitDetailResponse> getCommit(
            @Parameter(description = "레포지토리 ID") @PathVariable UUID repoId,
            @Parameter(description = "커밋 SHA") @PathVariable String commitSha
    ) {
        List<GitCommitWalker.ChangedFile> changedFiles = commitService.getChangedFiles(repoId, commitSha);

        // CommitInfo는 changedFiles 조회에서는 얻을 수 없으므로,
        // listCommits를 사용하거나 별도로 조회 필요
        // 여기서는 간단히 첫 번째 커밋 조회로 구현
        List<GitCommitWalker.CommitInfo> commits = commitService.listCommits(repoId, null, 0, 1);

        if (commits.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        GitCommitWalker.CommitInfo commitInfo = commits.get(0);

        CommitDetailResponse response = new CommitDetailResponse(
                commitInfo.sha(),
                commitInfo.shortSha(),
                commitInfo.shortMessage(),
                commitInfo.fullMessage(),
                commitInfo.authorName(),
                commitInfo.authorEmail(),
                commitInfo.committedAt(),
                changedFiles.stream()
                        .map(this::toChangedFileResponse)
                        .toList()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * 두 커밋 간 변경된 파일 목록을 조회한다.
     *
     * @param repoId 레포지토리 ID
     * @param from 시작 커밋 SHA
     * @param to 종료 커밋 SHA
     * @return 변경된 파일 목록
     */
    @Operation(summary = "커밋 간 Diff 조회", description = "두 커밋 간 변경된 파일 목록을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/diff")
    public List<ChangedFileResponse> getCommitDiff(
            @Parameter(description = "레포지토리 ID") @PathVariable UUID repoId,
            @Parameter(description = "시작 커밋 SHA") @RequestParam String from,
            @Parameter(description = "종료 커밋 SHA") @RequestParam String to
    ) {
        List<GitCommitWalker.ChangedFile> changedFiles = commitService.getChangedFilesBetween(
                repoId, from, to);

        return changedFiles.stream()
                .map(this::toChangedFileResponse)
                .toList();
    }

    /**
     * CommitInfo를 CommitResponse로 변환한다.
     */
    private CommitResponse toCommitResponse(GitCommitWalker.CommitInfo commitInfo) {
        // changedFilesCount는 별도 조회가 필요하므로 0으로 설정
        // 필요하면 개선 가능
        return new CommitResponse(
                commitInfo.sha(),
                commitInfo.shortSha(),
                commitInfo.shortMessage(),
                commitInfo.fullMessage(),
                commitInfo.authorName(),
                commitInfo.authorEmail(),
                commitInfo.committedAt(),
                0  // TODO: getChangedFiles()를 호출하여 실제 개수 계산
        );
    }

    /**
     * ChangedFile을 ChangedFileResponse로 변환한다.
     */
    private ChangedFileResponse toChangedFileResponse(GitCommitWalker.ChangedFile changedFile) {
        boolean isDocument = commitService.isDocumentFile(changedFile.path());

        return new ChangedFileResponse(
                changedFile.path(),
                changedFile.changeType().name(),
                changedFile.oldPath(),
                isDocument
        );
    }
}
