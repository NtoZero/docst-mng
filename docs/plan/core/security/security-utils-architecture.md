# SecurityUtils 아키텍처 분석 보고서

## 개요

본 보고서는 Docst 백엔드 애플리케이션에서 Spring Security의 `@AuthenticationPrincipal` 대신 `SecurityUtils` 유틸리티 클래스를 채택한 아키텍처적 결정 사유를 분석한다.

---

## 1. 현황 분석

### 1.1 관련 파일

| 파일 | 역할 |
|------|------|
| `SecurityUtils.java` | 인증 정보 조회 유틸리티 |
| `UserPrincipal.java` | Principal DTO (record) |
| `JwtAuthenticationFilter.java` | JWT 토큰 인증 필터 |
| `ApiKeyAuthenticationFilter.java` | API Key 인증 필터 |
| `ProjectPermissionAspect.java` | 프로젝트 권한 AOP |

### 1.2 사용 현황

```
SecurityUtils 사용: 6개 파일
@AuthenticationPrincipal 사용: 0개 파일
```

---

## 2. 핵심 채택 사유

### 2.1 LazyInitializationException 회피

#### 문제 상황

Spring Security에서 `User` JPA 엔티티를 직접 Principal로 사용할 경우:

```java
// 문제가 되는 코드
@GetMapping("/me")
public UserResponse getMe(@AuthenticationPrincipal User user) {
    return new UserResponse(user.getId(), user.getEmail());
}
```

Spring MVC는 요청 처리 완료 후 `AbstractAuthenticationToken.getName()`을 호출한다. 이 시점에 Hibernate 세션이 이미 닫혀 있으면 Lazy 필드 접근 시 `LazyInitializationException`이 발생한다.

#### 해결 방안

`UserPrincipal` record DTO를 도입하여 필요한 필드만 즉시 추출:

```java
// UserPrincipal.java
public record UserPrincipal(
    UUID id,
    String email,
    String displayName,
    UUID defaultProjectId
) implements Principal {

    public static UserPrincipal from(User user) {
        return new UserPrincipal(
            user.getId(),
            user.getEmail(),
            user.getDisplayName(),
            null
        );
    }

    @Override
    public String getName() {
        return email;  // 안전: 이미 추출된 값
    }
}
```

JWT 필터에서 엔티티 대신 DTO 사용:

```java
// JwtAuthenticationFilter.java:46-48
User user = userRepository.findById(userId).orElse(null);
if (user != null) {
    UserPrincipal principal = UserPrincipal.from(user);
    // principal을 Authentication에 설정
}
```

### 2.2 AOP 기반 권한 체크 아키텍처

#### 프로젝트의 권한 체크 패턴

Docst는 선언적 권한 체크를 위해 커스텀 어노테이션 + AOP를 사용한다:

```java
// 컨트롤러에서의 사용
@GetMapping("/{projectId}")
@RequireProjectRole(role = ProjectRole.VIEWER, projectIdParam = "projectId")
public ResponseEntity<ProjectResponse> getProject(@PathVariable UUID projectId) {
    // 권한 체크는 AOP에서 이미 완료됨
}
```

#### AOP에서 SecurityUtils 필요성

```java
// ProjectPermissionAspect.java:34-51
@Before("@annotation(requireProjectRole)")
public void checkProjectPermission(JoinPoint joinPoint, RequireProjectRole requireProjectRole) {
    // AOP에서는 컨트롤러 파라미터로 주입받을 수 없음
    // SecurityContextHolder에서 직접 조회 필요
    UUID userId = SecurityUtils.getCurrentUserId();

    if (userId == null) {
        throw new PermissionDeniedException("Authentication required");
    }

    UUID projectId = extractParameterValue(joinPoint, requireProjectRole.projectIdParam(), UUID.class);
    permissionService.requireProjectPermission(userId, projectId, requireProjectRole.role());
}
```

`@AuthenticationPrincipal`은 컨트롤러 메서드 파라미터에서만 동작하므로, AOP Aspect에서는 `SecurityUtils`를 통한 직접 접근이 필수적이다.

### 2.3 다중 인증 방식 지원

#### 인증 필터 종류

| 필터 | Principal 타입 | 용도 |
|------|---------------|------|
| `JwtAuthenticationFilter` | `UserPrincipal` | 웹 UI 로그인 |
| `ApiKeyAuthenticationFilter` | `UserPrincipal` (with defaultProjectId) | API/MCP 호출 |

#### SecurityUtils의 다형적 처리

```java
// SecurityUtils.java:66-83
public static UUID getCurrentUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
        return null;
    }

    Object principal = authentication.getPrincipal();

    // UserPrincipal 처리 (권장)
    if (principal instanceof UserPrincipal userPrincipal) {
        return userPrincipal.id();
    }

    // User 엔티티 처리 (하위 호환)
    if (principal instanceof User user) {
        return user.getId();
    }

    return null;
}
```

`@AuthenticationPrincipal`은 단일 타입만 주입하므로 이런 유연성을 제공하기 어렵다.

### 2.4 서비스 레이어 재사용성

#### @AuthenticationPrincipal의 한계

```java
// 컨트롤러에서만 사용 가능
@PostMapping
public ResponseEntity<?> create(@AuthenticationPrincipal UserPrincipal user) {
    service.create(user.id());  // 매번 ID 전달 필요
}
```

#### SecurityUtils의 활용 범위

```
┌─────────────────────────────────────────────────────┐
│                    Controller                        │
│  SecurityUtils.requireCurrentUserId() ✓             │
└─────────────────────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────┐
│                    Service                           │
│  SecurityUtils.getCurrentUserId() ✓                 │
└─────────────────────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────┐
│                 AOP Aspect                           │
│  SecurityUtils.getCurrentUserId() ✓                 │
└─────────────────────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────┐
│              Utility / Helper                        │
│  SecurityUtils.isAuthenticated() ✓                  │
└─────────────────────────────────────────────────────┘
```

### 2.5 일관된 예외 처리

#### requireXxx() 패턴

```java
// SecurityUtils.java:100-106
public static UUID requireCurrentUserId() {
    UUID userId = getCurrentUserId();
    if (userId == null) {
        throw new SecurityException("Authentication required");
    }
    return userId;
}

// SecurityUtils.java:114-120
public static UserPrincipal requireCurrentUserPrincipal() {
    UserPrincipal principal = getCurrentUserPrincipal();
    if (principal == null) {
        throw new SecurityException("Authentication required");
    }
    return principal;
}
```

이 패턴의 장점:
- 중앙 집중식 예외 처리
- 일관된 에러 메시지
- 호출부에서 null 체크 불필요

---

## 3. 비교 분석

### 3.1 기능 비교표

| 기능 | @AuthenticationPrincipal | SecurityUtils |
|------|--------------------------|---------------|
| 컨트롤러 사용 | ✓ | ✓ |
| 서비스 레이어 사용 | ✗ | ✓ |
| AOP Aspect 사용 | ✗ | ✓ |
| 다중 Principal 타입 | △ (복잡) | ✓ |
| LazyInit 안전성 | △ (주의 필요) | ✓ |
| 중앙 집중 예외 처리 | ✗ | ✓ |
| 테스트 용이성 | ✓ (주입 기반) | △ (static) |
| 코드 간결성 | ✓ | △ |

### 3.2 트레이드오프

#### SecurityUtils 장점
- 어디서든 동일한 방식으로 인증 정보 접근
- AOP 패턴과 자연스럽게 통합
- 다중 인증 방식 통합 처리
- 명시적 예외 처리 패턴

#### SecurityUtils 단점
- Static 메서드로 인한 테스트 어려움 (Mockito static mock 필요)
- 의존성 주입 원칙 위배
- SecurityContextHolder에 대한 암묵적 의존

---

## 4. 설계 결정 근거

### 4.1 아키텍처 적합성

Docst의 핵심 아키텍처 특성:

1. **AOP 기반 권한 체크**: `@RequireProjectRole`, `@RequireRepositoryAccess`
2. **다중 인증 방식**: JWT + API Key
3. **JPA 엔티티 사용**: LazyInitializationException 위험
4. **계층 간 인증 정보 공유**: Controller → Service → Repository

이러한 특성에서 `SecurityUtils`가 `@AuthenticationPrincipal`보다 적합하다.

### 4.2 일관성 원칙

프로젝트 전체에서 단일 패턴 사용:
- 모든 인증 정보 조회는 `SecurityUtils`를 통해
- 권한 체크는 `@RequireXxx` 어노테이션으로
- 컨트롤러에서 직접 사용자 정보 파라미터 주입 없음

---

## 5. 사용 가이드라인

### 5.1 권장 사용법

```java
// 인증 필수인 경우
UUID userId = SecurityUtils.requireCurrentUserId();
UserPrincipal principal = SecurityUtils.requireCurrentUserPrincipal();

// 인증 선택적인 경우 (게스트 허용)
UUID userId = SecurityUtils.getCurrentUserId();
if (userId != null) {
    // 인증된 사용자 처리
}

// 인증 여부만 확인
if (SecurityUtils.isAuthenticated()) {
    // ...
}
```

### 5.2 Deprecated 메서드

```java
// ❌ Deprecated: User 엔티티 직접 반환
User user = SecurityUtils.getCurrentUser();
User user = SecurityUtils.requireCurrentUser();

// ✓ 권장: UserPrincipal 또는 userId 사용
UserPrincipal principal = SecurityUtils.getCurrentUserPrincipal();
UUID userId = SecurityUtils.getCurrentUserId();
```

### 5.3 테스트 작성

```java
@ExtendWith(MockitoExtension.class)
class MyServiceTest {

    @Test
    void testWithAuthenticatedUser() {
        // SecurityContextHolder를 직접 설정
        UserPrincipal principal = new UserPrincipal(
            UUID.randomUUID(), "test@example.com", "Test User", null
        );
        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);

        try {
            // 테스트 실행
            myService.doSomething();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
```

---

## 6. 결론

Docst 백엔드에서 `SecurityUtils` 채택은 다음 요인들의 종합적 결과이다:

1. **기술적 필요성**: JPA 엔티티의 LazyInitializationException 회피
2. **아키텍처 정합성**: AOP 기반 권한 체크 패턴과의 통합
3. **확장성**: 다중 인증 방식의 통합 처리
4. **일관성**: 모든 계층에서 동일한 방식의 인증 정보 접근

`@AuthenticationPrincipal`의 간결함을 포기하는 대신, 프로젝트 전체의 아키텍처 일관성과 유지보수성을 확보한 합리적인 설계 결정이다.

---

## 부록: 관련 코드 위치

| 구성 요소 | 파일 경로 |
|----------|----------|
| SecurityUtils | `backend/src/main/java/com/docst/auth/SecurityUtils.java` |
| UserPrincipal | `backend/src/main/java/com/docst/auth/UserPrincipal.java` |
| JWT 필터 | `backend/src/main/java/com/docst/auth/JwtAuthenticationFilter.java` |
| API Key 필터 | `backend/src/main/java/com/docst/auth/ApiKeyAuthenticationFilter.java` |
| 권한 AOP | `backend/src/main/java/com/docst/auth/ProjectPermissionAspect.java` |
| 권한 어노테이션 | `backend/src/main/java/com/docst/auth/RequireProjectRole.java` |