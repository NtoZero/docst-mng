package com.docst.auth;

import com.docst.domain.ProjectRole;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 레포지토리 접근 권한 체크 어노테이션.
 * 레포지토리의 소속 프로젝트에 대한 사용자 권한을 검증한다.
 *
 * 사용 예시:
 * <pre>
 * {@code
 * @RequireRepositoryAccess(role = ProjectRole.VIEWER, repositoryIdParam = "repositoryId")
 * public List<Document> getDocuments(@PathVariable UUID repositoryId) {
 *     // 레포지토리의 프로젝트에 VIEWER 이상의 권한이 있는 사용자만 접근 가능
 * }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRepositoryAccess {

    /**
     * 요구되는 최소 역할.
     * 이 역할 이상의 권한을 가진 사용자만 접근 가능하다.
     * (OWNER > ADMIN > EDITOR > VIEWER)
     */
    ProjectRole role();

    /**
     * 레포지토리 ID를 담고 있는 파라미터 이름.
     * 메서드의 파라미터 중 레포지토리 ID를 가진 파라미터 이름을 지정한다.
     *
     * 예: "repositoryId", "id"
     */
    String repositoryIdParam();
}
