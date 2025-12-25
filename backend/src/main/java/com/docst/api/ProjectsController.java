package com.docst.api;

import com.docst.api.ApiModels.CreateProjectRequest;
import com.docst.api.ApiModels.ProjectResponse;
import com.docst.api.ApiModels.UpdateProjectRequest;
import com.docst.domain.Project;
import com.docst.service.ProjectService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
public class ProjectsController {

    private final ProjectService projectService;

    public ProjectsController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public List<ProjectResponse> listProjects() {
        return projectService.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(@RequestBody CreateProjectRequest request) {
        Project project = projectService.create(request.name(), request.description());
        ProjectResponse response = toResponse(project);
        return ResponseEntity.created(URI.create("/api/projects/" + project.getId())).body(response);
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<ProjectResponse> getProject(@PathVariable UUID projectId) {
        return projectService.findById(projectId)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

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

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> deleteProject(@PathVariable UUID projectId) {
        projectService.delete(projectId);
        return ResponseEntity.noContent().build();
    }

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
