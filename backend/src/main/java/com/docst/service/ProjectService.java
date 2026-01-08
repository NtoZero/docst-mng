package com.docst.service;

import com.docst.domain.Project;
import com.docst.domain.ProjectMember;
import com.docst.domain.ProjectRole;
import com.docst.domain.User;
import com.docst.repository.ProjectMemberRepository;
import com.docst.repository.ProjectRepository;
import com.docst.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 프로젝트 서비스.
 * 프로젝트 및 멤버십에 대한 비즈니스 로직을 담당한다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;

    /**
     * 모든 프로젝트를 조회한다.
     *
     * @return 프로젝트 목록 (생성일순)
     */
    public List<Project> findAll() {
        return projectRepository.findAllByOrderByCreatedAt();
    }

    /**
     * 사용자가 멤버로 속한 프로젝트를 조회한다.
     *
     * @param userId 사용자 ID
     * @return 프로젝트 목록
     */
    public List<Project> findByMemberUserId(UUID userId) {
        return projectRepository.findByMemberUserId(userId);
    }

    /**
     * ID로 프로젝트를 조회한다.
     *
     * @param id 프로젝트 ID
     * @return 프로젝트 (존재하지 않으면 empty)
     */
    public Optional<Project> findById(UUID id) {
        return projectRepository.findById(id);
    }

    /**
     * 새 프로젝트를 생성하고 소유자를 설정한다.
     *
     * @param name 프로젝트 이름
     * @param description 프로젝트 설명
     * @param owner 프로젝트 소유자
     * @return 생성된 프로젝트
     */
    @Transactional
    public Project create(String name, String description, User owner) {
        Project project = new Project(name, description);
        project.addMember(owner, ProjectRole.OWNER);
        return projectRepository.save(project);
    }

    /**
     * 새 프로젝트를 생성한다 (소유자 없음).
     *
     * @param name 프로젝트 이름
     * @param description 프로젝트 설명
     * @return 생성된 프로젝트
     */
    @Transactional
    public Project create(String name, String description) {
        Project project = new Project(name, description);
        return projectRepository.save(project);
    }

    /**
     * 새 프로젝트를 생성하고 소유자를 설정한다 (userId 기반).
     *
     * @param name 프로젝트 이름
     * @param description 프로젝트 설명
     * @param ownerId 프로젝트 소유자 ID
     * @return 생성된 프로젝트
     * @throws IllegalArgumentException 사용자가 존재하지 않을 경우
     */
    @Transactional
    public Project create(String name, String description, UUID ownerId) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + ownerId));
        return create(name, description, owner);
    }

    /**
     * 프로젝트 정보를 업데이트한다.
     *
     * @param id 프로젝트 ID
     * @param name 새 이름 (null이면 변경하지 않음)
     * @param description 새 설명 (null이면 변경하지 않음)
     * @param active 활성화 상태 (null이면 변경하지 않음)
     * @return 업데이트된 프로젝트 (존재하지 않으면 empty)
     */
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

    /**
     * 프로젝트를 삭제한다.
     *
     * @param id 프로젝트 ID
     */
    @Transactional
    public void delete(UUID id) {
        projectRepository.deleteById(id);
    }

    /**
     * 프로젝트에 멤버를 추가한다.
     *
     * @param projectId 프로젝트 ID
     * @param user 추가할 사용자
     * @param role 부여할 역할
     * @return 생성된 멤버십
     * @throws IllegalArgumentException 프로젝트가 존재하지 않을 경우
     * @throws IllegalStateException 사용자가 이미 멤버일 경우
     */
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

    /**
     * 프로젝트의 특정 사용자 멤버십을 조회한다.
     *
     * @param projectId 프로젝트 ID
     * @param userId 사용자 ID
     * @return 멤버십 (존재하지 않으면 empty)
     */
    public Optional<ProjectMember> findMember(UUID projectId, UUID userId) {
        return projectMemberRepository.findByProjectIdAndUserId(projectId, userId);
    }

    /**
     * 프로젝트의 모든 멤버를 조회한다.
     *
     * @param projectId 프로젝트 ID
     * @return 멤버 목록
     */
    public List<ProjectMember> findMembers(UUID projectId) {
        return projectMemberRepository.findByProjectId(projectId);
    }

    /**
     * 프로젝트에서 멤버를 제거한다.
     *
     * @param projectId 프로젝트 ID
     * @param userId 제거할 사용자 ID
     */
    @Transactional
    public void removeMember(UUID projectId, UUID userId) {
        projectMemberRepository.deleteByProjectIdAndUserId(projectId, userId);
    }
}
