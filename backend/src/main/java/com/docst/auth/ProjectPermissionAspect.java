package com.docst.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.UUID;

/**
 * 프로젝트 권한 체크 Aspect.
 * @RequireProjectRole 및 @RequireRepositoryAccess 어노테이션이 붙은 메서드 실행 전에 권한을 검증한다.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ProjectPermissionAspect {

    private final PermissionService permissionService;

    /**
     * @RequireProjectRole 어노테이션이 붙은 메서드 실행 전에 권한을 검증한다.
     *
     * @param joinPoint       조인 포인트
     * @param requireProjectRole 어노테이션
     */
    @Before("@annotation(requireProjectRole)")
    public void checkProjectPermission(JoinPoint joinPoint, RequireProjectRole requireProjectRole) {
        log.debug("Checking project permission: required role = {}", requireProjectRole.role());

        // 현재 사용자 조회
        UUID userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new PermissionDeniedException("Authentication required");
        }

        // 프로젝트 ID 추출
        UUID projectId = extractParameterValue(joinPoint, requireProjectRole.projectIdParam(), UUID.class);
        if (projectId == null) {
            throw new IllegalArgumentException(
                    "Project ID parameter not found: " + requireProjectRole.projectIdParam());
        }

        // 권한 검증
        permissionService.requireProjectPermission(userId, projectId, requireProjectRole.role());

        log.debug("Project permission check passed for user {} on project {}", userId, projectId);
    }

    /**
     * @RequireRepositoryAccess 어노테이션이 붙은 메서드 실행 전에 권한을 검증한다.
     *
     * @param joinPoint               조인 포인트
     * @param requireRepositoryAccess 어노테이션
     */
    @Before("@annotation(requireRepositoryAccess)")
    public void checkRepositoryPermission(JoinPoint joinPoint, RequireRepositoryAccess requireRepositoryAccess) {
        log.debug("Checking repository permission: required role = {}", requireRepositoryAccess.role());

        // 현재 사용자 조회
        UUID userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new PermissionDeniedException("Authentication required");
        }

        // 레포지토리 ID 추출
        UUID repositoryId = extractParameterValue(joinPoint, requireRepositoryAccess.repositoryIdParam(), UUID.class);
        if (repositoryId == null) {
            throw new IllegalArgumentException(
                    "Repository ID parameter not found: " + requireRepositoryAccess.repositoryIdParam());
        }

        // 권한 검증
        permissionService.requireRepositoryPermission(userId, repositoryId, requireRepositoryAccess.role());

        log.debug("Repository permission check passed for user {} on repository {}", userId, repositoryId);
    }

    /**
     * 메서드 파라미터에서 특정 이름의 값을 추출한다.
     *
     * @param joinPoint     조인 포인트
     * @param parameterName 파라미터 이름
     * @param expectedType  기대되는 타입
     * @param <T>           타입
     * @return 파라미터 값 (찾지 못하면 null)
     */
    @SuppressWarnings("unchecked")
    private <T> T extractParameterValue(JoinPoint joinPoint, String parameterName, Class<T> expectedType) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Parameter[] parameters = method.getParameters();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            String name = parameter.getName();

            // 파라미터 이름이 일치하고 타입도 일치하는 경우
            if (name.equals(parameterName) && expectedType.isAssignableFrom(parameter.getType())) {
                return (T) args[i];
            }
        }

        log.warn("Parameter '{}' not found in method {}", parameterName, method.getName());
        return null;
    }
}
