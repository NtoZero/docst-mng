package com.docst.service;

import com.docst.domain.Project;
import com.docst.domain.ProjectMember;
import com.docst.domain.ProjectRole;
import com.docst.domain.User;
import com.docst.repository.ProjectMemberRepository;
import com.docst.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;

    public ProjectService(ProjectRepository projectRepository, ProjectMemberRepository projectMemberRepository) {
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
    }

    public List<Project> findAll() {
        return projectRepository.findAllByOrderByCreatedAt();
    }

    public List<Project> findByMemberUserId(UUID userId) {
        return projectRepository.findByMemberUserId(userId);
    }

    public Optional<Project> findById(UUID id) {
        return projectRepository.findById(id);
    }

    @Transactional
    public Project create(String name, String description, User owner) {
        Project project = new Project(name, description);
        project.addMember(owner, ProjectRole.OWNER);
        return projectRepository.save(project);
    }

    @Transactional
    public Project create(String name, String description) {
        Project project = new Project(name, description);
        return projectRepository.save(project);
    }

    @Transactional
    public Optional<Project> update(UUID id, String name, String description, Boolean active) {
        return projectRepository.findById(id)
                .map(project -> {
                    if (name != null) project.setName(name);
                    if (description != null) project.setDescription(description);
                    if (active != null) project.setActive(active);
                    return projectRepository.save(project);
                });
    }

    @Transactional
    public void delete(UUID id) {
        projectRepository.deleteById(id);
    }

    @Transactional
    public ProjectMember addMember(UUID projectId, User user, ProjectRole role) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        if (projectMemberRepository.existsByProjectIdAndUserId(projectId, user.getId())) {
            throw new IllegalStateException("User is already a member of this project");
        }

        ProjectMember member = new ProjectMember(project, user, role);
        return projectMemberRepository.save(member);
    }

    public Optional<ProjectMember> findMember(UUID projectId, UUID userId) {
        return projectMemberRepository.findByProjectIdAndUserId(projectId, userId);
    }

    public List<ProjectMember> findMembers(UUID projectId) {
        return projectMemberRepository.findByProjectId(projectId);
    }

    @Transactional
    public void removeMember(UUID projectId, UUID userId) {
        projectMemberRepository.deleteByProjectIdAndUserId(projectId, userId);
    }
}
