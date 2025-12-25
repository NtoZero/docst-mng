package com.docst.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.docst.api.ApiModels.CreateProjectRequest;
import com.docst.api.ApiModels.ProjectResponse;
import com.docst.api.ApiModels.UpdateProjectRequest;
import com.docst.store.InMemoryStore;

@RestController
@RequestMapping("/api/projects")
public class ProjectsController {
  private final InMemoryStore store;

  public ProjectsController(InMemoryStore store) {
    this.store = store;
  }

  @GetMapping
  public List<ProjectResponse> listProjects() {
    return store.listProjects().stream()
        .map(project -> new ProjectResponse(
            project.id(),
            project.name(),
            project.description(),
            project.active(),
            project.createdAt()
        ))
        .toList();
  }

  @PostMapping
  public ResponseEntity<ProjectResponse> createProject(@RequestBody CreateProjectRequest request) {
    var project = store.createProject(request.name(), request.description());
    ProjectResponse response = new ProjectResponse(
        project.id(),
        project.name(),
        project.description(),
        project.active(),
        project.createdAt()
    );
    return ResponseEntity.created(URI.create("/api/projects/" + project.id())).body(response);
  }

  @GetMapping("/{projectId}")
  public ResponseEntity<ProjectResponse> getProject(@PathVariable UUID projectId) {
    return store.getProject(projectId)
        .map(project -> new ProjectResponse(
            project.id(),
            project.name(),
            project.description(),
            project.active(),
            project.createdAt()
        ))
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @PutMapping("/{projectId}")
  public ResponseEntity<ProjectResponse> updateProject(
      @PathVariable UUID projectId,
      @RequestBody UpdateProjectRequest request
  ) {
    return store.updateProject(projectId, request.name(), request.description(), request.active())
        .map(project -> new ProjectResponse(
            project.id(),
            project.name(),
            project.description(),
            project.active(),
            project.createdAt()
        ))
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @DeleteMapping("/{projectId}")
  public ResponseEntity<Void> deleteProject(@PathVariable UUID projectId) {
    store.deleteProject(projectId);
    return ResponseEntity.noContent().build();
  }
}
