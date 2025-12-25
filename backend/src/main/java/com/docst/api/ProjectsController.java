package com.docst.api;

import com.docst.api.ApiModels.CreateProjectRequest;
import com.docst.api.ApiModels.ProjectResponse;
import com.docst.api.ApiModels.UpdateProjectRequest;
import com.docst.domain.Project;
import com.docst.service.ProjectService;
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
    @GetMapping
    public List<ProjectResponse> listProjects() {
        return projectService.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * 새 프로젝트를 생성한다.
     *
     * @param request 생성 요청
     * @return 생성된 프로젝트 (201 Created)
     */
    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(@RequestBody CreateProjectRequest request) {
        Project project = projectService.create(request.name(), request.description());
        ProjectResponse response = toResponse(project);
        return ResponseEntity.created(URI.create("/api/projects/" + project.getId())).body(response);
    }

    /**
     * 프로젝트를 조회한다.
     *
     * @param projectId 프로젝트 ID
     * @return 프로젝트 정보 (없으면 404)
     */
    @GetMapping("/{projectId}")
    public ResponseEntity<ProjectResponse> getProject(@PathVariable UUID projectId) {
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
    @PutMapping("/{projectId}")
    public ResponseEntity<ProjectResponse> updateProject(
            @PathVariable UUID projectId,
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
    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> deleteProject(@PathVariable UUID projectId) {
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
