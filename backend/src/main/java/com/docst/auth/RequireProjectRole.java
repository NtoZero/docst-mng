package com.docst.auth;

import com.docst.project.ProjectRole;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 프로젝트 권한 체크 어노테이션.
 * 메서드 실행 전에 현재 사용자의 프로젝트 역할을 검증한다.
 *
 * 사용 예시:
 * <pre>
 * {@code
 * @RequireProjectRole(role = ProjectRole.ADMIN, projectIdParam = "projectId")
 * public void syncRepository(@PathVariable UUID projectId, ...) {
 *     // ADMIN 이상의 권한이 있는 사용자만 실행 가능
 * }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireProjectRole {

    /**
     * 요구되는 최소 역할.
     * 이 역할 이상의 권한을 가진 사용자만 접근 가능하다.
     * (OWNER > ADMIN > EDITOR > VIEWER)
     */
    ProjectRole role();

    /**
     * 프로젝트 ID를 담고 있는 파라미터 이름.
     * 메서드의 파라미터 중 프로젝트 ID를 가진 파라미터 이름을 지정한다.
     *
     * 예: "projectId", "id"
     */
    String projectIdParam();
}
