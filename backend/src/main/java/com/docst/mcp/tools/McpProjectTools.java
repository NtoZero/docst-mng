package com.docst.mcp.tools;

import com.docst.auth.SecurityUtils;
import com.docst.auth.UserPrincipal;
import com.docst.mcp.McpModels.*;
import com.docst.project.service.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * MCP Project Tools.
 * Spring AI 1.1.0+ @Tool annotation 기반 프로젝트 관련 MCP 도구.
 *
 * 제공 도구:
 * - list_projects: 사용자의 프로젝트 목록 조회
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class McpProjectTools {

    private final ProjectService projectService;

    /**
     * 프로젝트 목록 조회.
     * 인증된 사용자가 접근 가능한 모든 프로젝트를 반환.
     * 문서 검색 전 프로젝트 ID를 확인하는 데 사용.
     */
    @Tool(name = "list_projects", description = "List all projects the authenticated user has access to. " +
          "Use this first to discover available projects before searching or listing documents. " +
          "Returns project IDs, names, descriptions, and your role in each project.")
    public ListProjectsResult listProjects() {
        log.info("MCP Tool: listProjects");

        UserPrincipal principal = SecurityUtils.getCurrentUserPrincipal();
        if (principal == null) {
            throw new IllegalStateException("Authentication required. Please provide an API key.");
        }

        var projects = projectService.findByMemberUserId(principal.id()).stream()
            .map(project -> {
                String role = projectService.findMember(project.getId(), principal.id())
                    .map(m -> m.getRole().name())
                    .orElse("VIEWER");
                return new ProjectSummary(
                    project.getId(),
                    project.getName(),
                    project.getDescription(),
                    role
                );
            })
            .toList();

        log.info("Found {} projects for user {}", projects.size(), principal.id());
        return new ListProjectsResult(projects);
    }
}
