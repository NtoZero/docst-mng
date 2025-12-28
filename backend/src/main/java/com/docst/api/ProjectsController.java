package com.docst.api;

import com.docst.api.ApiModels.CreateProjectRequest;
import com.docst.api.ApiModels.ProjectResponse;
import com.docst.api.ApiModels.UpdateProjectRequest;
import com.docst.auth.RequireProjectRole;
import com.docst.auth.SecurityUtils;
import com.docst.domain.Project;
import com.docst.domain.ProjectRole;
import com.docst.domain.User;
import com.docst.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * 프로젝트 컨트롤러.
 * 프로젝트 CRUD 기능을 제공한다.
 */
@Tag(name = "Projects", description = "프로젝트 관리 API")
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectsController {

    private final ProjectService projectService;

    /**
     * 모든 프로젝트를 조회한다.
     *
     * @return 프로젝트 목록
     */
    @Operation(summary = "프로젝트 목록 조회", description = "모든 프로젝트를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping
    public List<ProjectResponse> listProjects() {
        return projectService.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 새 프로젝트를 생성한다.
     * 현재 사용자가 자동으로 프로젝트 소유자(OWNER)로 추가된다.
     *
     * @param request 생성 요청
     * @return 생성된 프로젝트 (201 Created)
     */
    @Operation(summary = "프로젝트 생성", description = "새 프로젝트를 생성하고 현재 사용자를 소유자로 설정합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "프로젝트 생성 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청")
    })
    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(@RequestBody CreateProjectRequest request) {
        User currentUser = SecurityUtils.requireCurrentUser();
        Project project = projectService.create(request.name(), request.description(), currentUser);
        ProjectResponse response = toResponse(project);
        return ResponseEntity.created(URI.create("/api/projects/" + project.getId())).body(response);
    }

    /**
     * 프로젝트를 조회한다.
     *
     * @param projectId 프로젝트 ID
     * @return 프로젝트 정보 (없으면 404)
     */
    @Operation(summary = "프로젝트 조회", description = "프로젝트 ID로 프로젝트 정보를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "프로젝트를 찾을 수 없음")
    })
    @GetMapping("/{projectId}")
    @RequireProjectRole(role = ProjectRole.VIEWER, projectIdParam = "projectId")
    public ResponseEntity<ProjectResponse> getProject(
            @Parameter(description = "프로젝트 ID") @PathVariable UUID projectId) {
        return projectService.findById(projectId)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 프로젝트 정보를 업데이트한다.
     *
     * @param projectId 프로젝트 ID
     * @param request 업데이트 요청
     * @return 업데이트된 프로젝트 (없으면 404)
     */
    @Operation(summary = "프로젝트 수정", description = "프로젝트 정보를 수정합니다. (ADMIN 권한 필요)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "404", description = "프로젝트를 찾을 수 없음")
    })
    @PutMapping("/{projectId}")
    @RequireProjectRole(role = ProjectRole.ADMIN, projectIdParam = "projectId")
    public ResponseEntity<ProjectResponse> updateProject(
            @Parameter(description = "프로젝트 ID") @PathVariable UUID projectId,
            @RequestBody UpdateProjectRequest request
    ) {
        return projectService.update(projectId, request.name(), request.description(), request.active())
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 프로젝트를 삭제한다.
     *
     * @param projectId 프로젝트 ID
     * @return 204 No Content
     */
    @Operation(summary = "프로젝트 삭제", description = "프로젝트를 삭제합니다. (OWNER 권한 필요)")
    @ApiResponse(responseCode = "204", description = "삭제 성공")
    @DeleteMapping("/{projectId}")
    @RequireProjectRole(role = ProjectRole.OWNER, projectIdParam = "projectId")
    public ResponseEntity<Void> deleteProject(
            @Parameter(description = "프로젝트 ID") @PathVariable UUID projectId) {
        projectService.delete(projectId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Project 엔티티를 응답 DTO로 변환한다.
     */
    private ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.isActive(),
                project.getCreatedAt()
        );
    }
}
