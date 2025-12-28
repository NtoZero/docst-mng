# Phase 3-A/B: JWT 인증 및 GitHub OAuth 구현 완료 보고서

> **구현 일자**: 2025-12-28
> **상태**: ✅ 완료 (백엔드 100%, 프론트엔드 100%)
> **구현자**: JWT (jjwt 0.12.5) + Spring Security + OAuth2 Client

---

## 개요

Phase 3-A/B는 개발용 하드코딩된 토큰을 실제 JWT 인증으로 전환하고, GitHub OAuth 소셜 로그인을 추가하는 단계입니다. 안전한 토큰 기반 인증과 편리한 소셜 로그인을 모두 지원합니다.

---

## Phase 3-A: JWT 인증 고도화

### 구현 개요

기존의 `"dev-token-" + userId` 하드코딩을 제거하고, 표준 JWT(JSON Web Token) 기반 인증으로 전환했습니다.

### 의존성

**build.gradle.kts**:
```kotlin
dependencies {
    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.5")
}
```

### 핵심 컴포넌트

#### 1. JwtConfig

**파일**: `backend/src/main/java/com/docst/auth/JwtConfig.java`

```java
@Configuration
@ConfigurationProperties(prefix = "docst.jwt")
@Getter
@Setter
public class JwtConfig {
    /**
     * JWT secret key (256-bit minimum)
     */
    private String secret = "your-256-bit-secret-key-change-this-in-production-environment";

    /**
     * JWT expiration time in seconds (default: 24 hours)
     */
    private long expiration = 86400;
}
```

**설정 (application.yml)**:
```yaml
docst:
  jwt:
    secret: ${JWT_SECRET:docst-dev-secret-key-change-in-production-must-be-256-bits}
    expiration: 86400  # 24 hours
```

**환경 변수 (.env)**:
```bash
JWT_SECRET=your-256-bit-secret-key-change-in-production
```

#### 2. JwtService

**파일**: `backend/src/main/java/com/docst/auth/JwtService.java`

**주요 메서드**:

1. **토큰 생성**:
```java
public String generateToken(UUID userId, String email) {
    Instant now = Instant.now();
    Instant expiration = now.plusSeconds(jwtConfig.getExpiration());

    return Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiration))
            .signWith(getSigningKey())
            .compact();
}
```

**JWT Claims**:
- `sub` (subject): User ID (UUID)
- `email`: 사용자 이메일
- `iat` (issued at): 발급 시각
- `exp` (expiration): 만료 시각

2. **토큰 검증**:
```java
public boolean validateToken(String token) {
    try {
        Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token);
        return true;
    } catch (Exception e) {
        log.debug("Invalid JWT token: {}", e.getMessage());
        return false;
    }
}
```

3. **사용자 정보 추출**:
```java
public UUID extractUserId(String token) {
    Claims claims = extractClaims(token);
    return UUID.fromString(claims.getSubject());
}

public String extractEmail(String token) {
    Claims claims = extractClaims(token);
    return claims.get("email", String.class);
}
```

4. **서명 키 생성**:
```java
private SecretKey getSigningKey() {
    byte[] keyBytes = jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8);
    return Keys.hmacShaKeyFor(keyBytes);
}
```

**알고리즘**: HMAC-SHA256 (HS256)

#### 3. JwtAuthenticationFilter

**파일**: `backend/src/main/java/com/docst/auth/JwtAuthenticationFilter.java`

**역할**: HTTP 요청의 Authorization 헤더에서 JWT를 추출하고 검증

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    private static final String HEADER_NAME = "Authorization";
    private static final String TOKEN_PREFIX = "Bearer ";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String token = extractTokenFromRequest(request);

            if (token != null && jwtService.validateToken(token)) {
                UUID userId = jwtService.extractUserId(token);

                User user = userRepository.findById(userId).orElse(null);
                if (user != null) {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    user,
                                    null,
                                    Collections.emptyList()
                            );
                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (Exception e) {
            log.debug("JWT authentication failed: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(HEADER_NAME);
        if (bearerToken != null && bearerToken.startsWith(TOKEN_PREFIX)) {
            return bearerToken.substring(TOKEN_PREFIX.length());
        }
        return null;
    }
}
```

**처리 플로우**:
1. Authorization 헤더에서 "Bearer " 제거
2. JWT 검증 (서명, 만료 시각)
3. User ID 추출 및 데이터베이스 조회
4. Spring Security Context에 인증 정보 설정

#### 4. SecurityConfig

**파일**: `backend/src/main/java/com/docst/config/SecurityConfig.java`

```java
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF 비활성화 (JWT 사용)
                .csrf(AbstractHttpConfigurer::disable)

                // 세션 비활성화 (Stateless)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // 권한 설정
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers(
                                "/api/auth/**",
                                "/actuator/health",
                                "/error"
                        ).permitAll()
                        // All other endpoints require authentication
                        .anyRequest().authenticated()
                )

                // JWT 필터 추가
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

**보안 설정**:
- CSRF 비활성화 (REST API이므로)
- Stateless 세션 (JWT 기반)
- `/api/auth/**` Public 접근 허용
- 나머지 엔드포인트 인증 필수
- JwtAuthenticationFilter를 UsernamePasswordAuthenticationFilter 앞에 배치

#### 5. AuthController 업데이트

**파일**: `backend/src/main/java/com/docst/api/AuthController.java`

**변경 사항**:

1. **로그인 - 실제 JWT 발급**:
```java
@PostMapping("/local/login")
public ResponseEntity<AuthTokenResponse> login(@RequestBody LoginRequest request) {
    User user = userService.createOrUpdateLocalUser(request.email(), request.displayName());
    // Generate JWT token
    String token = jwtService.generateToken(user.getId(), user.getEmail());
    AuthTokenResponse response = new AuthTokenResponse(token, "Bearer", 86400);
    return ResponseEntity.ok(response);
}
```

**Before**: `"dev-token-" + user.getId()`
**After**: 실제 JWT 토큰

2. **/me 엔드포인트 - 인증된 사용자 정보 반환**:
```java
@GetMapping("/me")
public ResponseEntity<UserResponse> me(Authentication authentication) {
    if (authentication == null || !(authentication.getPrincipal() instanceof User)) {
        return ResponseEntity.status(401).build();
    }

    User user = (User) authentication.getPrincipal();
    UserResponse response = new UserResponse(
            user.getId(),
            user.getProvider().name(),
            user.getProviderUserId(),
            user.getEmail(),
            user.getDisplayName(),
            user.getCreatedAt()
    );
    return ResponseEntity.ok(response);
}
```

**Before**: 플레이스홀더 데이터 반환
**After**: SecurityContext에서 실제 사용자 정보 반환

### JWT 토큰 예시

**생성된 토큰**:
```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3OC0xMjM0LTEyMzQtMTIzNC0xMjM0NTY3ODkwYWIiLCJlbWFpbCI6ImRlbW9AZG9jc3QuZGV2IiwiaWF0IjoxNzM1Mzc4ODAwLCJleHAiOjE3MzU0NjUyMDB9.signature
```

**디코딩된 Payload**:
```json
{
  "sub": "12345678-1234-1234-1234-1234567890ab",
  "email": "demo@docst.dev",
  "iat": 1735378800,
  "exp": 1735465200
}
```

### 프론트엔드 연동

**기존 코드는 변경 없음** - Authorization 헤더는 이미 전송 중:

```typescript
// lib/api.ts
const token = localStorage.getItem('docst-token');
if (token) {
    headers.Authorization = `Bearer ${token}`;
}
```

**변경 사항**: 토큰 형식만 변경 (하드코딩 → JWT)

---

## Phase 3-B: GitHub OAuth

### 구현 개요

GitHub 계정으로 로그인할 수 있는 OAuth 2.0 인증을 구현했습니다.

### 의존성

**build.gradle.kts**:
```kotlin
dependencies {
    // OAuth2
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
}
```

### GitHub OAuth App 설정

**1. GitHub Developer Settings에서 OAuth App 생성**:
- URL: https://github.com/settings/developers
- Application name: Docst
- Homepage URL: `http://localhost:3000`
- Authorization callback URL: `http://localhost:8342/api/auth/github/callback`

**2. Client ID와 Client Secret 발급**

**3. 환경 변수 설정 (.env)**:
```bash
GITHUB_CLIENT_ID=your-github-client-id
GITHUB_CLIENT_SECRET=your-github-client-secret
GITHUB_CALLBACK_URL=http://localhost:8342/api/auth/github/callback
GITHUB_FRONTEND_CALLBACK_URL=http://localhost:3000/auth/callback
```

### 핵심 컴포넌트

#### 1. GitHubOAuthService

**파일**: `backend/src/main/java/com/docst/auth/GitHubOAuthService.java`

**주요 메서드**:

1. **Authorization URL 생성**:
```java
public String getAuthorizationUrl(String state) {
    return String.format(
            "%s?client_id=%s&redirect_uri=%s&state=%s&scope=user:email",
            GITHUB_AUTH_URL,
            clientId,
            callbackUrl,
            state
    );
}
```

**URL 예시**:
```
https://github.com/login/oauth/authorize
  ?client_id=abc123
  &redirect_uri=http://localhost:8342/api/auth/github/callback
  &state=random-uuid
  &scope=user:email
```

2. **Code → Access Token 교환**:
```java
public String exchangeCodeForToken(String code) {
    RestTemplate restTemplate = new RestTemplate();

    Map<String, String> params = new HashMap<>();
    params.put("client_id", clientId);
    params.put("client_secret", clientSecret);
    params.put("code", code);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("Accept", "application/json");

    HttpEntity<Map<String, String>> request = new HttpEntity<>(params, headers);

    ResponseEntity<JsonNode> response = restTemplate.exchange(
            GITHUB_TOKEN_URL,
            HttpMethod.POST,
            request,
            JsonNode.class
    );

    return response.getBody().get("access_token").asText();
}
```

**GitHub API 호출**:
- POST https://github.com/login/oauth/access_token
- Body: `{ client_id, client_secret, code }`
- Response: `{ access_token, scope, token_type }`

3. **사용자 정보 조회**:
```java
public GitHubUserInfo getUserInfo(String accessToken) {
    RestTemplate restTemplate = new RestTemplate();

    HttpHeaders headers = new HttpHeaders();
    headers.set("Authorization", "Bearer " + accessToken);
    headers.set("Accept", "application/json");

    HttpEntity<Void> request = new HttpEntity<>(headers);

    ResponseEntity<JsonNode> response = restTemplate.exchange(
            GITHUB_USER_API,
            HttpMethod.GET,
            request,
            JsonNode.class
    );

    JsonNode body = response.getBody();
    return new GitHubUserInfo(
        body.get("id").asLong().toString(),
        body.get("login").asText(),
        body.get("email").asText(),
        body.get("name").asText()
    );
}
```

**GitHub API 호출**:
- GET https://api.github.com/user
- Header: `Authorization: Bearer <access_token>`
- Response: `{ id, login, email, name, ... }`

4. **JWT 발급**:
```java
public String processOAuthLogin(GitHubUserInfo userInfo) {
    User user = userService.createOrUpdateGitHubUser(
            userInfo.id(),
            userInfo.email(),
            userInfo.name()
    );

    return jwtService.generateToken(user.getId(), user.getEmail());
}
```

**UserService 메서드**:
```java
@Transactional
public User createOrUpdateGitHubUser(String providerUserId, String email, String displayName) {
    return userRepository.findByProviderAndProviderUserId(AuthProvider.GITHUB, providerUserId)
            .map(user -> {
                user.setEmail(email);
                user.setDisplayName(displayName);
                return userRepository.save(user);
            })
            .orElseGet(() -> {
                User newUser = new User();
                newUser.setProvider(AuthProvider.GITHUB);
                newUser.setProviderUserId(providerUserId);
                newUser.setEmail(email);
                newUser.setDisplayName(displayName);
                newUser.setCreatedAt(Instant.now());
                return userRepository.save(newUser);
            });
}
```

#### 2. GitHubOAuthController

**파일**: `backend/src/main/java/com/docst/auth/GitHubOAuthController.java`

**엔드포인트**:

1. **OAuth 시작**:
```java
@GetMapping("/api/auth/github/start")
public void startOAuth(HttpServletResponse response) throws IOException {
    String state = UUID.randomUUID().toString();
    // TODO: Store state for CSRF validation

    String authUrl = gitHubOAuthService.getAuthorizationUrl(state);
    response.sendRedirect(authUrl);
}
```

2. **OAuth 콜백**:
```java
@GetMapping("/api/auth/github/callback")
public void handleCallback(
        @RequestParam("code") String code,
        @RequestParam(value = "state", required = false) String state,
        HttpServletResponse response
) throws IOException {
    try {
        // TODO: Validate state

        String accessToken = gitHubOAuthService.exchangeCodeForToken(code);
        if (accessToken == null) {
            response.sendRedirect(frontendCallbackUrl + "?error=token_exchange_failed");
            return;
        }

        GitHubUserInfo userInfo = gitHubOAuthService.getUserInfo(accessToken);
        if (userInfo == null) {
            response.sendRedirect(frontendCallbackUrl + "?error=user_info_failed");
            return;
        }

        String jwtToken = gitHubOAuthService.processOAuthLogin(userInfo);

        response.sendRedirect(frontendCallbackUrl + "?token=" + jwtToken);

    } catch (Exception e) {
        log.error("OAuth callback error", e);
        response.sendRedirect(frontendCallbackUrl + "?error=authentication_failed");
    }
}
```

### OAuth 플로우

```
1. 사용자 클릭: "Continue with GitHub"
   → 프론트엔드: window.location.href = "http://localhost:8342/api/auth/github/start"

2. 백엔드 /start:
   → GitHub로 리다이렉트
   → https://github.com/login/oauth/authorize?client_id=...&state=...

3. 사용자: GitHub 로그인 및 승인

4. GitHub:
   → 백엔드 콜백으로 리다이렉트
   → http://localhost:8342/api/auth/github/callback?code=abc123&state=xyz

5. 백엔드 /callback:
   → code를 access_token으로 교환
   → access_token으로 사용자 정보 조회
   → User 생성/업데이트 (provider='GITHUB')
   → JWT 발급
   → 프론트엔드로 리다이렉트
   → http://localhost:3000/auth/callback?token=<JWT>

6. 프론트엔드 /auth/callback:
   → 토큰 저장 (localStorage)
   → /api/auth/me 호출로 사용자 정보 조회
   → 대시보드로 리다이렉트
```

### 프론트엔드 연동

#### 1. 로그인 페이지 - GitHub 버튼

**파일**: `frontend/app/[locale]/login/page.tsx`

```tsx
const handleGitHubLogin = () => {
  window.location.href = `${process.env.NEXT_PUBLIC_API_BASE || 'http://localhost:8342'}/api/auth/github/start`;
};

<Button onClick={handleGitHubLogin} variant="outline">
  <Github className="w-5 h-5 mr-2" />
  {t('loginWithGitHub')}
</Button>

<Separator />

{/* 기존 로컬 로그인 폼 */}
```

#### 2. OAuth 콜백 페이지

**파일**: `frontend/app/auth/callback/page.tsx`

```tsx
export default function OAuthCallbackPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { setAuth } = useAuthStore();
  const [status, setStatus] = useState<'loading' | 'success' | 'error'>('loading');

  useEffect(() => {
    const token = searchParams.get('token');
    const error = searchParams.get('error');

    if (error) {
      setStatus('error');
      return;
    }

    if (token) {
      localStorage.setItem('docst-token', token);

      fetch(`${API_BASE}/api/auth/me`, {
        headers: { Authorization: `Bearer ${token}` }
      })
        .then(res => res.json())
        .then(user => {
          setAuth(user, token);
          setStatus('success');
          setTimeout(() => router.push('/'), 1500);
        })
        .catch(err => setStatus('error'));
    }
  }, [searchParams]);

  return (
    <div className="min-h-screen flex items-center justify-center">
      {status === 'loading' && <Loader2 className="animate-spin" />}
      {status === 'success' && <CheckCircle className="text-green-500" />}
      {status === 'error' && <XCircle className="text-red-500" />}
    </div>
  );
}
```

**Suspense 래핑** (Next.js 요구사항):
```tsx
export default function OAuthCallbackPage() {
  return (
    <Suspense fallback={<Loader2 className="animate-spin" />}>
      <OAuthCallbackContent />
    </Suspense>
  );
}
```

#### 3. Separator 컴포넌트

**파일**: `frontend/components/ui/separator.tsx`

```tsx
export function Separator({ orientation = 'horizontal' }: SeparatorProps) {
  return (
    <div
      className={cn(
        'shrink-0 bg-border',
        orientation === 'horizontal' ? 'h-[1px] w-full' : 'h-full w-[1px]'
      )}
    />
  );
}
```

### 설정

**application.yml**:
```yaml
docst:
  github:
    client-id: ${GITHUB_CLIENT_ID:}
    client-secret: ${GITHUB_CLIENT_SECRET:}
    callback-url: ${GITHUB_CALLBACK_URL:http://localhost:8342/api/auth/github/callback}
    frontend-callback-url: ${GITHUB_FRONTEND_CALLBACK_URL:http://localhost:3000/auth/callback}
```

**환경 변수 (.env.example)**:
```bash
# GitHub OAuth Configuration
# Get your credentials from: https://github.com/settings/developers
GITHUB_CLIENT_ID=your-github-client-id
GITHUB_CLIENT_SECRET=your-github-client-secret
GITHUB_CALLBACK_URL=http://localhost:8342/api/auth/github/callback
GITHUB_FRONTEND_CALLBACK_URL=http://localhost:3000/auth/callback
```

---

## 보안 고려사항

### 1. JWT Secret

**주의**: 프로덕션에서는 강력한 256-bit 키 사용
```bash
# 키 생성
openssl rand -base64 32
# → your-generated-256-bit-secret-key
```

### 2. CSRF 보호

**OAuth state 파라미터**:
- 현재: UUID 생성만 (TODO: 검증 추가)
- 개선: 세션/캐시에 state 저장 후 콜백에서 검증

### 3. HTTPS 사용

**프로덕션 설정**:
```yaml
docst:
  github:
    callback-url: https://api.docst.com/api/auth/github/callback
    frontend-callback-url: https://docst.com/auth/callback
```

### 4. 토큰 만료

**JWT 만료 시각**:
- 기본: 24시간 (86400초)
- 리프레시 토큰 미구현 (TODO)

---

## 테스트

### 로컬 로그인 테스트

```bash
# 1. 백엔드 시작
./gradlew bootRun

# 2. 프론트엔드 시작
cd frontend && npm run dev

# 3. http://localhost:3000/login 접속
# 4. 이메일 입력 후 로그인
# 5. JWT 토큰 발급 확인 (개발자 도구 → Application → Local Storage)
```

### GitHub OAuth 테스트

```bash
# 1. GitHub OAuth App 설정 확인
# 2. .env 파일에 GITHUB_CLIENT_ID, GITHUB_CLIENT_SECRET 설정
# 3. 백엔드/프론트엔드 시작
# 4. "Continue with GitHub" 클릭
# 5. GitHub 로그인 및 승인
# 6. 콜백 후 토큰 발급 확인
```

### curl 테스트

```bash
# 로컬 로그인
curl -X POST http://localhost:8342/api/auth/local/login \
  -H "Content-Type: application/json" \
  -d '{"email":"demo@docst.dev","displayName":"Demo User"}'
# → {"accessToken":"eyJhbGc...","tokenType":"Bearer","expiresIn":86400}

# /me 엔드포인트
TOKEN="eyJhbGc..."
curl http://localhost:8342/api/auth/me \
  -H "Authorization: Bearer $TOKEN"
# → {"id":"...","provider":"LOCAL","email":"demo@docst.dev",...}
```

---

## 트러블슈팅

### 1. JWT 검증 실패

**증상**: 401 Unauthorized
**원인**: Secret 키 불일치
**해결**:
```bash
# .env 파일 확인
JWT_SECRET=your-256-bit-secret-key

# 백엔드 재시작
./gradlew bootRun
```

### 2. GitHub OAuth 콜백 에러

**증상**: `?error=token_exchange_failed`
**원인**: Client ID/Secret 오류
**해결**:
```bash
# GitHub Developer Settings에서 확인
# .env 파일 업데이트
GITHUB_CLIENT_ID=correct-id
GITHUB_CLIENT_SECRET=correct-secret
```

### 3. CORS 에러

**증상**: 프론트엔드에서 API 호출 실패
**원인**: CORS 설정 누락
**해결**: `WebConfig.java`에서 프론트엔드 URL 추가
```java
config.setAllowedOrigins(Arrays.asList(
    "http://localhost:3000",
    "http://127.0.0.1:3000"
));
```

---

## 다음 단계

### TODO 목록

- [ ] OAuth state 파라미터 검증 (CSRF 방지)
- [ ] JWT 리프레시 토큰 구현
- [ ] 토큰 블랙리스트 (로그아웃 시)
- [ ] Rate limiting (무차별 대입 공격 방지)
- [ ] 다중 OAuth 제공자 (Google, GitLab 등)

### Phase 3 나머지 작업

- [ ] Phase 3-C: Webhook 자동 동기화
- [ ] Phase 3-D: 문서 관계 그래프
- [ ] Phase 3-E: 권한 체크 AOP

---

## 참고 자료

- [JWT.io](https://jwt.io/) - JWT 디버거
- [GitHub OAuth Documentation](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps)
- [Spring Security Documentation](https://docs.spring.io/spring-security/reference/)
- [JJWT Documentation](https://github.com/jwtk/jjwt)
