# Phase 3-E: 권한 체크 AOP 구현

## 개요

AOP(Aspect-Oriented Programming)를 활용하여 프로젝트 및 레포지토리에 대한 선언적 권한 검증 기능을 구현했습니다.

### 주요 기능
- **역할 기반 접근 제어**: OWNER, ADMIN, EDITOR, VIEWER 4단계 역할
- **선언적 권한 검증**: 어노테이션 기반 메서드 레벨 권한 체크
- **AOP 기반 자동 검증**: 비즈니스 로직과 권한 검증 분리
- **프로젝트/레포지토리 권한**: 프로젝트 멤버십 및 레포지토리 소속 기반 권한 확인

---

## 구현 내용

### 1. ProjectRole Enum

**위치**: `backend/src/main/java/com/docst/domain/ProjectRole.java`

프로젝트 멤버 역할을 정의하는 enum입니다.

```java
public enum ProjectRole {
    /** 프로젝트 소유자 - 모든 권한 */
    OWNER,
    /** 관리자 - 레포 관리, 동기화 실행 */
    ADMIN,
    /** 편집자 - 문서 수정 (향후) */
    EDITOR,
    /** 뷰어 - 읽기 전용 */
    VIEWER;

    /**
     * 현재 역할이 요구 역할 이상의 권한을 가지는지 확인한다.
     *
     * @param required 요구되는 역할
     * @return 권한이 있으면 true
     */
    public boolean hasPermission(ProjectRole required) {
        return this.ordinal() <= required.ordinal();
    }
}
```

**권한 계층**:
- `OWNER` > `ADMIN` > `EDITOR` > `VIEWER`
- `ADMIN` 역할은 `ADMIN`, `EDITOR`, `VIEWER` 권한 모두 가능
- `ordinal()` 기반 비교로 계층 구조 구현

---

### 2. @RequireProjectRole 어노테이션

**위치**: `backend/src/main/java/com/docst/auth/RequireProjectRole.java`

프로젝트 권한을 검증하는 어노테이션입니다.

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireProjectRole {
    /**
     * 요구되는 최소 역할.
     */
    ProjectRole role();

    /**
     * 프로젝트 ID를 담고 있는 파라미터 이름.
     */
    String projectIdParam();
}
```

**사용 예시**:
```java
@GetMapping("/projects/{projectId}")
@RequireProjectRole(role = ProjectRole.VIEWER, projectIdParam = "projectId")
public ResponseEntity<ProjectResponse> getProject(@PathVariable UUID projectId) {
    // VIEWER 이상의 권한을 가진 사용자만 접근 가능
}
```

---

### 3. @RequireRepositoryAccess 어노테이션

**위치**: `backend/src/main/java/com/docst/auth/RequireRepositoryAccess.java`

레포지토리 권한을 검증하는 어노테이션입니다. 레포지토리의 소속 프로젝트에 대한 사용자 권한을 검증합니다.

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRepositoryAccess {
    /**
     * 요구되는 최소 역할.
     */
    ProjectRole role();

    /**
     * 레포지토리 ID를 담고 있는 파라미터 이름.
     */
    String repositoryIdParam();
}
```

**사용 예시**:
```java
@GetMapping("/repositories/{repositoryId}/documents")
@RequireRepositoryAccess(role = ProjectRole.VIEWER, repositoryIdParam = "repositoryId")
public List<DocumentResponse> getDocuments(@PathVariable UUID repositoryId) {
    // 레포지토리의 프로젝트에 VIEWER 이상의 권한을 가진 사용자만 접근 가능
}
```

---

### 4. SecurityUtils

**위치**: `backend/src/main/java/com/docst/auth/SecurityUtils.java`

현재 인증된 사용자 정보를 조회하는 유틸리티입니다.

```java
public class SecurityUtils {
    /**
     * 현재 인증된 사용자를 반환한다.
     */
    public static User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof User) {
            return (User) principal;
        }
        return null;
    }

    /**
     * 현재 인증된 사용자 ID를 반환한다.
     */
    public static UUID getCurrentUserId() {
        User user = getCurrentUser();
        return user != null ? user.getId() : null;
    }

    /**
     * 현재 인증된 사용자를 반환한다. 인증되지 않았으면 예외를 발생시킨다.
     */
    public static User requireCurrentUser() {
        User user = getCurrentUser();
        if (user == null) {
            throw new SecurityException("Authentication required");
        }
        return user;
    }
}
```

**특징**:
- Spring Security의 `SecurityContextHolder`에서 사용자 정보 추출
- `requireCurrentUser()`: 인증되지 않으면 예외 발생 (fail-fast)

---

### 5. PermissionService

**위치**: `backend/src/main/java/com/docst/auth/PermissionService.java`

프로젝트 및 레포지토리에 대한 사용자 권한을 검증하는 서비스입니다.

#### 5.1 프로젝트 권한 검증

```java
@Transactional(readOnly = true)
public boolean hasProjectPermission(UUID userId, UUID projectId, ProjectRole required) {
    Optional<ProjectMember> memberOpt = projectMemberRepository.findByProjectIdAndUserId(projectId, userId);
    if (memberOpt.isEmpty()) {
        log.debug("User {} is not a member of project {}", userId, projectId);
        return false;
    }

    ProjectMember member = memberOpt.get();
    boolean hasPermission = member.getRole().hasPermission(required);

    log.debug("User {} has role {} in project {} (required: {}): {}",
            userId, member.getRole(), projectId, required, hasPermission);

    return hasPermission;
}
```

**로직**:
1. 프로젝트 멤버십 조회 (`dm_project_member` 테이블)
2. 멤버가 아니면 `false` 반환
3. 멤버의 역할이 요구 역할 이상인지 확인 (`ProjectRole.hasPermission()`)

#### 5.2 레포지토리 권한 검증

```java
@Transactional(readOnly = true)
public boolean hasRepositoryPermission(UUID userId, UUID repositoryId, ProjectRole required) {
    Optional<Repository> repoOpt = repositoryRepository.findById(repositoryId);
    if (repoOpt.isEmpty()) {
        log.warn("Repository {} not found", repositoryId);
        return false;
    }

    UUID projectId = repoOpt.get().getProject().getId();
    return hasProjectPermission(userId, projectId, required);
}
```

**로직**:
1. 레포지토리 조회
2. 레포지토리의 소속 프로젝트 ID 추출
3. 프로젝트 권한 검증으로 위임

#### 5.3 권한 강제 검증

```java
public void requireProjectPermission(UUID userId, UUID projectId, ProjectRole required) {
    if (!hasProjectPermission(userId, projectId, required)) {
        throw new PermissionDeniedException(
                String.format("User %s does not have %s permission for project %s",
                        userId, required, projectId)
        );
    }
}
```

**사용처**: AOP Aspect에서 권한 체크 실패 시 예외 발생

---

### 6. ProjectPermissionAspect (AOP)

**위치**: `backend/src/main/java/com/docst/auth/ProjectPermissionAspect.java`

어노테이션이 붙은 메서드 실행 전에 권한을 검증하는 Aspect입니다.

#### 6.1 @RequireProjectRole 처리

```java
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
```

**동작 흐름**:
1. 현재 사용자 ID 추출 (인증되지 않았으면 예외)
2. 메서드 파라미터에서 프로젝트 ID 추출
3. `PermissionService.requireProjectPermission()` 호출
4. 권한 없으면 `PermissionDeniedException` 발생

#### 6.2 파라미터 추출

```java
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
```

**특징**:
- Java Reflection API로 메서드 파라미터 접근
- 파라미터 이름과 타입 모두 확인
- 파라미터 이름 유지를 위해 컴파일 시 `-parameters` 옵션 필요 (Gradle/Maven 기본 설정)

---

### 7. PermissionDeniedException

**위치**: `backend/src/main/java/com/docst/auth/PermissionDeniedException.java`

권한이 없을 때 발생하는 예외입니다.

```java
public class PermissionDeniedException extends RuntimeException {
    public PermissionDeniedException(String message) {
        super(message);
    }
}
```

---

### 8. GlobalExceptionHandler

**위치**: `backend/src/main/java/com/docst/config/GlobalExceptionHandler.java`

전역 예외 처리기입니다.

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    /**
     * 권한 거부 예외 처리.
     */
    @ExceptionHandler(PermissionDeniedException.class)
    public ResponseEntity<Map<String, String>> handlePermissionDenied(PermissionDeniedException e) {
        log.warn("Permission denied: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(Map.of(
                        "error", "Permission denied",
                        "message", e.getMessage()
                ));
    }

    /**
     * 보안 예외 처리 (인증 필요).
     */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, String>> handleSecurityException(SecurityException e) {
        log.warn("Security exception: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(Map.of(
                        "error", "Unauthorized",
                        "message", e.getMessage()
                ));
    }
}
```

**HTTP 상태 코드**:
- `401 Unauthorized`: 인증되지 않음 (로그인 필요)
- `403 Forbidden`: 인증되었지만 권한 없음

---

## 컨트롤러 적용 예시

### ProjectsController

**위치**: `backend/src/main/java/com/docst/api/ProjectsController.java`

```java
@RestController
@RequestMapping("/api/projects")
public class ProjectsController {

    // 프로젝트 조회: VIEWER 이상
    @GetMapping("/{projectId}")
    @RequireProjectRole(role = ProjectRole.VIEWER, projectIdParam = "projectId")
    public ResponseEntity<ProjectResponse> getProject(@PathVariable UUID projectId) {
        // ...
    }

    // 프로젝트 수정: ADMIN 이상
    @PutMapping("/{projectId}")
    @RequireProjectRole(role = ProjectRole.ADMIN, projectIdParam = "projectId")
    public ResponseEntity<ProjectResponse> updateProject(
            @PathVariable UUID projectId,
            @RequestBody UpdateProjectRequest request) {
        // ...
    }

    // 프로젝트 삭제: OWNER만
    @DeleteMapping("/{projectId}")
    @RequireProjectRole(role = ProjectRole.OWNER, projectIdParam = "projectId")
    public ResponseEntity<Void> deleteProject(@PathVariable UUID projectId) {
        // ...
    }
}
```

### RepositoriesController

**위치**: `backend/src/main/java/com/docst/api/RepositoriesController.java`

```java
@RestController
@RequestMapping("/api")
public class RepositoriesController {

    // 레포지토리 목록 조회: 프로젝트 VIEWER 이상
    @GetMapping("/projects/{projectId}/repositories")
    @RequireProjectRole(role = ProjectRole.VIEWER, projectIdParam = "projectId")
    public List<RepositoryResponse> listRepositories(@PathVariable UUID projectId) {
        // ...
    }

    // 레포지토리 생성: 프로젝트 ADMIN 이상
    @PostMapping("/projects/{projectId}/repositories")
    @RequireProjectRole(role = ProjectRole.ADMIN, projectIdParam = "projectId")
    public ResponseEntity<RepositoryResponse> createRepository(
            @PathVariable UUID projectId,
            @RequestBody CreateRepositoryRequest request) {
        // ...
    }

    // 레포지토리 조회: 레포지토리의 프로젝트 VIEWER 이상
    @GetMapping("/repositories/{repoId}")
    @RequireRepositoryAccess(role = ProjectRole.VIEWER, repositoryIdParam = "repoId")
    public ResponseEntity<RepositoryResponse> getRepository(@PathVariable UUID repoId) {
        // ...
    }
}
```

---

## 권한 매트릭스

### 프로젝트 권한

| 작업 | OWNER | ADMIN | EDITOR | VIEWER |
|------|-------|-------|--------|--------|
| 프로젝트 조회 | ✅ | ✅ | ✅ | ✅ |
| 프로젝트 수정 | ✅ | ✅ | ❌ | ❌ |
| 프로젝트 삭제 | ✅ | ❌ | ❌ | ❌ |
| 레포지토리 생성 | ✅ | ✅ | ❌ | ❌ |
| 레포지토리 수정 | ✅ | ✅ | ❌ | ❌ |
| 레포지토리 삭제 | ✅ | ❌ | ❌ | ❌ |
| 동기화 실행 | ✅ | ✅ | ❌ | ❌ |
| 문서 조회 | ✅ | ✅ | ✅ | ✅ |
| 문서 검색 | ✅ | ✅ | ✅ | ✅ |
| 그래프 조회 | ✅ | ✅ | ✅ | ✅ |

### 역할별 설명

- **OWNER**: 프로젝트 소유자, 모든 작업 가능 (프로젝트 삭제 포함)
- **ADMIN**: 관리자, 레포지토리 관리 및 동기화 실행 가능
- **EDITOR**: 편집자, 문서 수정 가능 (향후 구현)
- **VIEWER**: 뷰어, 읽기 전용

---

## 동작 흐름

```
┌─────────────────┐
│ Client Request  │
│ GET /api/       │
│ projects/{id}   │
└────────┬────────┘
         │
         ▼
┌──────────────────────────────────┐
│ Spring Security Filter Chain     │
│ - JwtAuthenticationFilter        │
│ - Extract user from JWT          │
│ - Set SecurityContext            │
└────────┬─────────────────────────┘
         │
         ▼
┌──────────────────────────────────┐
│ ProjectPermissionAspect (AOP)    │
│ @Before advice                   │
└────────┬─────────────────────────┘
         │
         ├─ 1. SecurityUtils.getCurrentUserId()
         │
         ├─ 2. Extract projectId from params
         │
         ├─ 3. PermissionService.requireProjectPermission()
         │      │
         │      ├─ ProjectMemberRepository.findByProjectIdAndUserId()
         │      │
         │      ├─ Check member.getRole().hasPermission(required)
         │      │
         │      └─ Throw PermissionDeniedException if false
         │
         ▼
┌──────────────────────────────────┐
│ Controller Method Execution      │
│ @RequireProjectRole(VIEWER, ...) │
│ public getProject(...) { ... }   │
└────────┬─────────────────────────┘
         │
         ▼
┌──────────────────────────────────┐
│ Service Layer                    │
│ ProjectService.findById(...)     │
└────────┬─────────────────────────┘
         │
         ▼
┌──────────────────────────────────┐
│ Response                         │
│ 200 OK + ProjectResponse         │
│ 403 Forbidden (권한 없음)        │
│ 401 Unauthorized (인증 필요)      │
└──────────────────────────────────┘
```

---

## 에러 응답 예시

### 401 Unauthorized (인증 필요)

```http
HTTP/1.1 401 Unauthorized
Content-Type: application/json

{
  "error": "Unauthorized",
  "message": "Authentication required"
}
```

### 403 Forbidden (권한 없음)

```http
HTTP/1.1 403 Forbidden
Content-Type: application/json

{
  "error": "Permission denied",
  "message": "User 123e4567-e89b-12d3-a456-426614174000 does not have ADMIN permission for project 789abc12-e34f-56g7-h890-123456789abc"
}
```

---

## 향후 개선 사항

### 1. 동적 권한 설정
- 역할별 권한을 DB에서 관리
- 커스텀 권한 정의 가능

### 2. 리소스 레벨 권한
- 문서 단위 권한 (특정 문서만 읽기/쓰기)
- 태그 기반 권한

### 3. 권한 캐싱
- Redis를 사용한 멤버십 캐싱
- 권한 체크 성능 최적화

### 4. 감사 로그 (Audit Log)
- 권한 체크 실패 기록
- 민감한 작업 로깅

### 5. 멤버 초대 시스템
- 이메일 초대
- 초대 토큰 관리

---

## 파일 변경 사항

### 생성된 파일
- `backend/src/main/java/com/docst/auth/RequireProjectRole.java`
- `backend/src/main/java/com/docst/auth/RequireRepositoryAccess.java`
- `backend/src/main/java/com/docst/auth/SecurityUtils.java`
- `backend/src/main/java/com/docst/auth/PermissionService.java`
- `backend/src/main/java/com/docst/auth/ProjectPermissionAspect.java`
- `backend/src/main/java/com/docst/auth/PermissionDeniedException.java`
- `backend/src/main/java/com/docst/config/GlobalExceptionHandler.java`

### 수정된 파일
- `backend/src/main/java/com/docst/api/ProjectsController.java`: 권한 어노테이션 적용
- `backend/src/main/java/com/docst/api/RepositoriesController.java`: 권한 어노테이션 적용

---

## 결론

Phase 3-E에서는 AOP를 활용한 선언적 권한 검증 시스템을 구현했습니다.

**주요 성과**:
- ✅ 역할 기반 접근 제어 (RBAC) 구현
- ✅ AOP로 비즈니스 로직과 권한 검증 분리
- ✅ 어노테이션 기반 선언적 권한 체크
- ✅ 프로젝트 및 레포지토리 권한 검증
- ✅ 전역 예외 처리로 일관된 에러 응답

**다음 단계**:
- Phase 4: MCP Tools 고도화
- 프론트엔드 권한 UI 구현
- 권한 캐싱 및 성능 최적화
