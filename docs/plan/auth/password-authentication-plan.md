# 비밀번호 인증 시스템 설계 및 구현 계획 (Argon2id)

## 현황 분석

### 현재 문제점
- `AuthController.java`의 `/api/auth/local/login` 엔드포인트가 비밀번호 검증 없이 이메일만으로 로그인 허용
- `User` 엔티티에 비밀번호 저장 필드 없음
- `BCryptPasswordEncoder`가 `SecurityConfig`에 정의되어 있으나 실제 사용되지 않음
- 보안 취약점: 누구나 타인의 이메일로 로그인 가능

### 현재 구조
```java
// User.java - 비밀번호 필드 없음
@Entity
public class User {
    private UUID id;
    private AuthProvider provider;  // GITHUB, LOCAL
    private String providerUserId;
    private String email;
    private String displayName;
    // ❌ password 필드 없음
}

// AuthController.java - 비밀번호 검증 없음
@PostMapping("/local/login")
public ResponseEntity<AuthTokenResponse> login(@RequestBody LoginRequest request) {
    User user = userService.createOrUpdateLocalUser(request.email(), request.displayName());
    String token = jwtService.generateToken(user.getId(), user.getEmail());
    return ResponseEntity.ok(response);
}
```

---

## 2025 Argon2id 선택 이유

### Argon2id - 최신 보안 표준
**특징:**
- 2015년 Password Hashing Competition 우승 알고리즘
- GPU, ASIC, 사이드채널 공격에 대한 최고 수준의 저항성
- 메모리 하드 함수 (Memory-hard function)로 대규모 병렬 공격 방어
- Argon2id는 Argon2i와 Argon2d의 하이브리드로 균형잡힌 보안 제공
- OWASP 2025 최우선 권장 알고리즘

**OWASP 권장 파라미터:**
```
- Memory: 19MB (19456 KiB)
- Iterations: 2
- Parallelism: 1
- Salt: 16 bytes (자동 생성)
- Hash Length: 32 bytes
```

**Spring Security 지원:**
- `Argon2PasswordEncoder` (Spring Security 5.3+)
- Bouncy Castle 라이브러리 기반

**저장 형식:**
```
$argon2id$v=19$m=19456,t=2,p=1$saltBase64$hashBase64
```

**왜 Argon2id를 처음부터 사용하는가?**
- 신규 프로젝트이므로 레거시 호환성 불필요
- 최고 수준의 보안성을 처음부터 확보
- BCrypt 대비 GPU 공격에 훨씬 강력한 저항성
- 향후 10년 이상 안전한 알고리즘
- 마이그레이션 복잡도 제거

---

## 구현 계획

### Phase 1: MVP - Argon2id 기반 기본 인증 (1주)

**목표:** Argon2id를 이용한 안전한 비밀번호 인증 시스템 구축

#### 1.1 의존성 추가

**build.gradle:**
```gradle
dependencies {
    // Spring Security (Argon2 지원)
    implementation 'org.springframework.boot:spring-boot-starter-security'

    // Bouncy Castle (Argon2 구현체)
    implementation 'org.bouncycastle:bcprov-jdk18on:1.78.1'
}
```

#### 1.2 데이터베이스 스키마 변경

**Flyway 마이그레이션 생성:**
```sql
-- V3__add_password_authentication.sql

-- User 테이블에 비밀번호 필드 추가
ALTER TABLE dm_user
ADD COLUMN password_hash VARCHAR(150);

-- password_hash 필드에 대한 체크 제약 조건 (LOCAL 사용자는 필수)
-- 향후 CHECK 제약으로 강제할 수 있음:
-- ALTER TABLE dm_user ADD CONSTRAINT chk_local_password
-- CHECK (provider != 'LOCAL' OR password_hash IS NOT NULL);

-- 인덱스 추가: 이메일 기반 조회 최적화
CREATE INDEX idx_user_email ON dm_user(email) WHERE provider = 'LOCAL';

-- 기존 LOCAL 사용자 삭제 (데이터 버림)
DELETE FROM dm_user WHERE provider = 'LOCAL';
```

**참고:**
- `password_hash` 길이를 150으로 설정 (Argon2 해시는 약 100자 내외)
- 기존 LOCAL 사용자는 모두 삭제하고 재가입 유도

#### 1.2.1 최초 관리자 계정 생성 전략

기존 데이터를 버리므로 최초 관리자 계정을 생성하는 전략이 필요합니다. 다음 3가지 옵션 중 선택:

**옵션 1: 환경 변수 기반 자동 생성 (권장)**

애플리케이션 시작 시 환경 변수에서 관리자 정보를 읽어 자동 생성합니다.

**application.yml:**
```yaml
docst:
  admin:
    enabled: true  # 초기 관리자 생성 활성화
    email: ${DOCST_ADMIN_EMAIL:admin@docst.local}
    password: ${DOCST_ADMIN_PASSWORD:}  # 환경 변수 필수
    display-name: ${DOCST_ADMIN_NAME:System Admin}
```

**.env 파일:**
```bash
DOCST_ADMIN_EMAIL=admin@example.com
DOCST_ADMIN_PASSWORD=ChangeMe123!
DOCST_ADMIN_NAME=Admin
```

**AdminInitializer:**
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminInitializer {

    private final UserService userService;
    private final PasswordValidator passwordValidator;

    @Value("${docst.admin.enabled:false}")
    private boolean adminEnabled;

    @Value("${docst.admin.email:}")
    private String adminEmail;

    @Value("${docst.admin.password:}")
    private String adminPassword;

    @Value("${docst.admin.display-name:System Admin}")
    private String adminDisplayName;

    /**
     * 애플리케이션 시작 시 초기 관리자 계정 생성
     */
    @PostConstruct
    public void initializeAdmin() {
        if (!adminEnabled) {
            log.info("Admin initialization is disabled");
            return;
        }

        if (adminEmail == null || adminEmail.isBlank()) {
            log.warn("Admin email not configured. Skipping admin initialization.");
            return;
        }

        if (adminPassword == null || adminPassword.isBlank()) {
            log.error("Admin password not configured. Admin account will not be created.");
            throw new IllegalStateException(
                "DOCST_ADMIN_PASSWORD environment variable must be set"
            );
        }

        try {
            // 기존 관리자 계정 확인
            Optional<User> existingAdmin = userService.findByProviderAndProviderUserId(
                AuthProvider.LOCAL,
                adminEmail
            );

            if (existingAdmin.isPresent()) {
                log.info("Admin account already exists: {}", adminEmail);
                return;
            }

            // 비밀번호 검증
            passwordValidator.validate(adminPassword);

            // 관리자 계정 생성
            User admin = userService.createLocalUser(
                adminEmail,
                adminPassword,
                adminDisplayName
            );

            log.info("✓ Initial admin account created successfully: {}", adminEmail);
            log.warn("⚠ Please change the admin password immediately after first login!");

        } catch (Exception e) {
            log.error("Failed to create admin account: {}", e.getMessage(), e);
            throw new RuntimeException("Admin initialization failed", e);
        }
    }
}
```

**장점:**
- 환경에 따라 다른 관리자 설정 가능
- Docker/Kubernetes 환경에서 쉽게 관리
- 자동화된 배포에 적합

**단점:**
- 환경 변수 관리 필요
- 비밀번호가 환경 변수에 노출 (첫 로그인 후 즉시 변경 권장)

---

**옵션 2: Flyway 마이그레이션으로 생성**

마이그레이션 스크립트에서 직접 관리자 계정을 생성합니다.

**V3__add_password_authentication.sql:**
```sql
-- User 테이블에 비밀번호 필드 추가
ALTER TABLE dm_user ADD COLUMN password_hash VARCHAR(150);
CREATE INDEX idx_user_email ON dm_user(email) WHERE provider = 'LOCAL';

-- 기존 LOCAL 사용자 삭제
DELETE FROM dm_user WHERE provider = 'LOCAL';

-- 초기 관리자 계정 생성
-- 비밀번호: ChangeMe123! (Argon2id 해시)
-- ⚠ 프로덕션에서는 반드시 변경해야 함
INSERT INTO dm_user (
    id,
    provider,
    provider_user_id,
    email,
    display_name,
    password_hash,
    created_at
) VALUES (
    gen_random_uuid(),
    'LOCAL',
    'admin@docst.local',
    'admin@docst.local',
    'System Admin',
    '$argon2id$v=19$m=19456,t=2,p=1$...',  -- 사전 생성된 해시
    CURRENT_TIMESTAMP
) ON CONFLICT DO NOTHING;
```

**Argon2 해시 사전 생성 (로컬에서 실행):**
```java
// 일회성 유틸리티 코드
public class PasswordHashGenerator {
    public static void main(String[] args) {
        PasswordEncoder encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        String password = "ChangeMe123!";
        String hash = encoder.encode(password);
        System.out.println("Hash: " + hash);
    }
}
```

**장점:**
- 별도 초기화 로직 불필요
- 데이터베이스 설정만으로 완료

**단점:**
- 하드코딩된 비밀번호 (보안 위험)
- 환경별로 다른 관리자 설정 어려움
- SQL에 해시 직접 입력 (가독성 낮음)

---

**옵션 3: 초기화 전용 엔드포인트 (One-time Setup)**

최초 1회만 실행 가능한 설정 엔드포인트를 제공합니다.

**SetupController:**
```java
@RestController
@RequestMapping("/api/setup")
@RequiredArgsConstructor
@Slf4j
public class SetupController {

    private final UserService userService;
    private final PasswordValidator passwordValidator;
    private final UserRepository userRepository;

    /**
     * 초기 관리자 계정 생성
     * 단, 시스템에 사용자가 하나도 없을 때만 실행 가능
     */
    @PostMapping("/initialize")
    public ResponseEntity<?> initializeSystem(@RequestBody InitializeRequest request) {
        // 시스템에 사용자가 이미 존재하는지 확인
        long userCount = userRepository.count();
        if (userCount > 0) {
            return ResponseEntity.status(403).body(Map.of(
                "error", "System already initialized",
                "message", "시스템이 이미 초기화되었습니다."
            ));
        }

        // 비밀번호 검증
        passwordValidator.validate(request.password());

        // 관리자 계정 생성
        User admin = userService.createLocalUser(
            request.email(),
            request.password(),
            request.displayName()
        );

        log.info("System initialized with admin account: {}", request.email());

        return ResponseEntity.status(201).body(Map.of(
            "message", "시스템이 성공적으로 초기화되었습니다.",
            "adminEmail", admin.getEmail()
        ));
    }

    /**
     * 시스템 초기화 필요 여부 확인
     */
    @GetMapping("/status")
    public ResponseEntity<?> setupStatus() {
        long userCount = userRepository.count();
        boolean needsSetup = userCount == 0;

        return ResponseEntity.ok(Map.of(
            "needsSetup", needsSetup,
            "userCount", userCount
        ));
    }

    public record InitializeRequest(
        String email,
        String password,
        String displayName
    ) {}
}
```

**SecurityConfig 수정:**
```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(session ->
            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        )
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(
                "/api/auth/**",
                "/api/setup/**",  // 초기화 엔드포인트 허용
                "/api/webhook/**",
                "/actuator/health",
                "/error"
            ).permitAll()
            .anyRequest().authenticated()
        )
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
}
```

**프론트엔드 초기화 화면 (선택):**
```typescript
// 애플리케이션 로드 시 체크
const checkSetupStatus = async () => {
  const response = await fetch('/api/setup/status');
  const { needsSetup } = await response.json();

  if (needsSetup) {
    // 초기화 화면으로 리다이렉트
    router.push('/setup');
  }
};

// 초기화 실행
const initializeSystem = async (email: string, password: string, displayName: string) => {
  const response = await fetch('/api/setup/initialize', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password, displayName }),
  });

  if (response.ok) {
    router.push('/login');
  }
};
```

**장점:**
- 사용자가 직접 관리자 정보 입력
- 환경 변수 관리 불필요
- 보안상 안전 (비밀번호 하드코딩 없음)

**단점:**
- 별도 UI 필요
- 자동화된 배포에 적합하지 않음
- 누군가 먼저 접근하면 관리자 계정 생성 가능 (방어 필요)

---

**권장 조합: 옵션 1 + 옵션 3**

1. **개발/테스트 환경**: 옵션 1 (환경 변수 자동 생성)
2. **프로덕션 환경**: 옵션 3 (초기화 엔드포인트)

**설정 예시:**
```yaml
# application.yml
docst:
  admin:
    enabled: ${DOCST_ADMIN_AUTO_INIT:false}  # 프로덕션에서는 false
```

```bash
# 개발 환경 (.env)
DOCST_ADMIN_AUTO_INIT=true
DOCST_ADMIN_EMAIL=dev@localhost
DOCST_ADMIN_PASSWORD=DevPassword123!

# 프로덕션 환경
DOCST_ADMIN_AUTO_INIT=false  # 초기화 엔드포인트 사용
```

**보안 권장사항:**
1. 초기 비밀번호는 강력하게 설정
2. 최초 로그인 후 즉시 비밀번호 변경 강제 (Phase 3에서 구현)
3. 초기화 엔드포인트는 HTTPS만 허용
4. 초기화 완료 후 엔드포인트 비활성화 고려

#### 1.3 User 엔티티 수정

```java
@Entity
@Table(name = "dm_user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
    // 기존 필드들...

    /**
     * 비밀번호 해시 (LOCAL 사용자만 사용)
     * Argon2id 형식: $argon2id$v=19$m=19456,t=2,p=1$...
     */
    @Column(name = "password_hash", length = 150)
    private String passwordHash;

    /**
     * 비밀번호가 설정되어 있는지 확인
     */
    public boolean hasPassword() {
        return passwordHash != null && !passwordHash.isEmpty();
    }

    /**
     * LOCAL 사용자인지 확인
     */
    public boolean isLocalUser() {
        return provider == AuthProvider.LOCAL;
    }

    /**
     * 비밀번호 해시 설정 (package-private for service layer)
     */
    void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /**
     * 비밀번호 해시 조회 (package-private for service layer)
     */
    String getPasswordHash() {
        return passwordHash;
    }
}
```

#### 1.4 SecurityConfig 수정

```java
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/**",
                                "/api/webhook/**",
                                "/actuator/health",
                                "/error"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Argon2id PasswordEncoder
     * OWASP 권장 파라미터 사용
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    // 또는 커스텀 파라미터 설정
    @Bean
    public PasswordEncoder customArgon2PasswordEncoder() {
        return new Argon2PasswordEncoder(
            16,      // saltLength (bytes)
            32,      // hashLength (bytes)
            1,       // parallelism
            19456,   // memory (KiB) = 19MB
            2        // iterations
        );
    }
}
```

**주요 변경사항:**
- 기존 `BCryptPasswordEncoder` 제거
- `Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()` 사용
- Spring Security 5.8+ 기본 설정이 OWASP 권장 파라미터와 일치

#### 1.5 UserService 수정

```java
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 로컬 사용자 생성 (회원가입)
     *
     * @param email 이메일 주소
     * @param password 평문 비밀번호
     * @param displayName 표시 이름
     * @return 생성된 사용자
     */
    @Transactional
    public User createLocalUser(String email, String password, String displayName) {
        // 중복 이메일 검증
        if (userRepository.findByProviderAndProviderUserId(AuthProvider.LOCAL, email).isPresent()) {
            throw new IllegalArgumentException("이미 존재하는 이메일입니다.");
        }

        // Argon2id 비밀번호 해싱
        String hashedPassword = passwordEncoder.encode(password);

        User user = new User(AuthProvider.LOCAL, email, email, displayName);
        user.setPasswordHash(hashedPassword);

        return userRepository.save(user);
    }

    /**
     * 로컬 사용자 인증 (로그인)
     *
     * @param email 이메일 주소
     * @param password 평문 비밀번호
     * @return 인증된 사용자 (실패 시 empty)
     */
    public Optional<User> authenticateLocalUser(String email, String password) {
        Optional<User> userOpt = userRepository.findByProviderAndProviderUserId(
            AuthProvider.LOCAL, email
        );

        if (userOpt.isEmpty()) {
            return Optional.empty();
        }

        User user = userOpt.get();

        // 비밀번호 검증
        if (!user.hasPassword() || !passwordEncoder.matches(password, user.getPasswordHash())) {
            return Optional.empty();
        }

        return Optional.of(user);
    }

    /**
     * 비밀번호 변경
     *
     * @param userId 사용자 ID
     * @param oldPassword 기존 비밀번호
     * @param newPassword 새 비밀번호
     */
    @Transactional
    public void changePassword(UUID userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        if (!user.isLocalUser()) {
            throw new IllegalStateException("LOCAL 사용자만 비밀번호를 변경할 수 있습니다.");
        }

        // 기존 비밀번호 검증
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("기존 비밀번호가 일치하지 않습니다.");
        }

        // 새 비밀번호 해싱 및 저장
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
}
```

#### 1.6 AuthController 수정

```java
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;
    private final PasswordValidator passwordValidator;

    /**
     * 회원가입
     */
    @PostMapping("/local/register")
    public ResponseEntity<UserResponse> register(@RequestBody RegisterRequest request) {
        // 비밀번호 복잡도 검증
        passwordValidator.validate(request.password());

        User user = userService.createLocalUser(
            request.email(),
            request.password(),
            request.displayName()
        );

        UserResponse response = new UserResponse(
            user.getId(),
            user.getProvider().name(),
            user.getProviderUserId(),
            user.getEmail(),
            user.getDisplayName(),
            user.getCreatedAt()
        );

        return ResponseEntity.status(201).body(response);
    }

    /**
     * 로그인
     */
    @PostMapping("/local/login")
    public ResponseEntity<AuthTokenResponse> login(@RequestBody LoginRequest request) {
        Optional<User> userOpt = userService.authenticateLocalUser(
            request.email(),
            request.password()
        );

        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        User user = userOpt.get();
        String token = jwtService.generateToken(user.getId(), user.getEmail());

        AuthTokenResponse response = new AuthTokenResponse(token, "Bearer", 86400);
        return ResponseEntity.ok(response);
    }

    /**
     * 비밀번호 변경
     */
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
        Authentication authentication,
        @RequestBody ChangePasswordRequest request
    ) {
        if (authentication == null || !(authentication.getPrincipal() instanceof User)) {
            return ResponseEntity.status(401).build();
        }

        User user = (User) authentication.getPrincipal();

        // 새 비밀번호 복잡도 검증
        passwordValidator.validate(request.newPassword());

        userService.changePassword(
            user.getId(),
            request.oldPassword(),
            request.newPassword()
        );

        return ResponseEntity.noContent().build();
    }

    // Request/Response DTOs
    public record RegisterRequest(String email, String password, String displayName) {}
    public record LoginRequest(String email, String password) {}
    public record ChangePasswordRequest(String oldPassword, String newPassword) {}
}
```

#### 1.7 비밀번호 검증 유틸리티

```java
@Component
public class PasswordValidator {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 128;
    private static final Pattern UPPERCASE = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL = Pattern.compile("[^a-zA-Z0-9]");

    /**
     * 비밀번호 복잡도 검증
     * - 최소 8자, 최대 128자
     * - 대문자, 소문자, 숫자, 특수문자 중 3가지 이상 포함
     */
    public void validate(String password) {
        if (password == null) {
            throw new IllegalArgumentException("비밀번호는 필수입니다.");
        }

        // 길이 검증
        if (password.length() < MIN_LENGTH) {
            throw new IllegalArgumentException(
                "비밀번호는 최소 " + MIN_LENGTH + "자 이상이어야 합니다."
            );
        }

        if (password.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                "비밀번호는 최대 " + MAX_LENGTH + "자까지 가능합니다."
            );
        }

        // 복잡도 검증
        int strength = 0;
        if (UPPERCASE.matcher(password).find()) strength++;
        if (LOWERCASE.matcher(password).find()) strength++;
        if (DIGIT.matcher(password).find()) strength++;
        if (SPECIAL.matcher(password).find()) strength++;

        if (strength < 3) {
            throw new IllegalArgumentException(
                "비밀번호는 대문자, 소문자, 숫자, 특수문자 중 3가지 이상을 포함해야 합니다."
            );
        }

        // 추가 검증: 반복 문자 제한
        if (hasRepeatingCharacters(password, 3)) {
            throw new IllegalArgumentException(
                "동일한 문자가 3회 이상 연속으로 반복될 수 없습니다."
            );
        }
    }

    /**
     * 연속 반복 문자 확인
     */
    private boolean hasRepeatingCharacters(String password, int maxRepeat) {
        for (int i = 0; i < password.length() - maxRepeat + 1; i++) {
            char current = password.charAt(i);
            boolean repeating = true;

            for (int j = 1; j < maxRepeat; j++) {
                if (password.charAt(i + j) != current) {
                    repeating = false;
                    break;
                }
            }

            if (repeating) {
                return true;
            }
        }

        return false;
    }

    /**
     * 비밀번호 강도 평가 (선택적)
     * @return 0-4 (약함-매우강함)
     */
    public int assessStrength(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return 0;
        }

        int score = 0;

        // 길이 점수
        if (password.length() >= 12) score++;
        if (password.length() >= 16) score++;

        // 문자 종류 점수
        if (UPPERCASE.matcher(password).find()) score++;
        if (LOWERCASE.matcher(password).find()) score++;
        if (DIGIT.matcher(password).find()) score++;
        if (SPECIAL.matcher(password).find()) score++;

        return Math.min(score, 4);
    }
}
```

#### 1.8 테스트 코드

```java
@SpringBootTest
@Transactional
class UserServicePasswordTest {

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("Argon2id 해싱이 정상적으로 작동한다")
    void testArgon2PasswordHashing() {
        String rawPassword = "MySecurePass123!";
        User user = userService.createLocalUser("test@example.com", rawPassword, "Test User");

        // 평문과 해시가 다름
        assertNotEquals(rawPassword, user.getPasswordHash());

        // Argon2id 형식 확인
        assertTrue(user.getPasswordHash().startsWith("$argon2id$"));

        // 비밀번호 매칭 확인
        assertTrue(passwordEncoder.matches(rawPassword, user.getPasswordHash()));
    }

    @Test
    @DisplayName("잘못된 비밀번호로 인증이 실패한다")
    void testAuthenticationFailsWithWrongPassword() {
        userService.createLocalUser("test@example.com", "CorrectPass123!", "Test");

        Optional<User> result = userService.authenticateLocalUser(
            "test@example.com",
            "WrongPassword"
        );

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("올바른 비밀번호로 인증이 성공한다")
    void testAuthenticationSucceedsWithCorrectPassword() {
        String password = "CorrectPass123!";
        userService.createLocalUser("test@example.com", password, "Test");

        Optional<User> result = userService.authenticateLocalUser(
            "test@example.com",
            password
        );

        assertTrue(result.isPresent());
        assertEquals("test@example.com", result.get().getEmail());
    }

    @Test
    @DisplayName("중복 이메일로 가입이 실패한다")
    void testDuplicateEmailRegistrationFails() {
        userService.createLocalUser("test@example.com", "Password123!", "User1");

        assertThrows(IllegalArgumentException.class, () -> {
            userService.createLocalUser("test@example.com", "DifferentPass456!", "User2");
        });
    }

    @Test
    @DisplayName("비밀번호 변경이 정상 작동한다")
    void testPasswordChange() {
        String oldPassword = "OldPass123!";
        String newPassword = "NewPass456!";

        User user = userService.createLocalUser("test@example.com", oldPassword, "Test");

        userService.changePassword(user.getId(), oldPassword, newPassword);

        // 새 비밀번호로 인증 성공
        Optional<User> result = userService.authenticateLocalUser("test@example.com", newPassword);
        assertTrue(result.isPresent());

        // 기존 비밀번호로 인증 실패
        Optional<User> oldResult = userService.authenticateLocalUser("test@example.com", oldPassword);
        assertTrue(oldResult.isEmpty());
    }

    @Test
    @DisplayName("잘못된 기존 비밀번호로 변경이 실패한다")
    void testPasswordChangeFailsWithWrongOldPassword() {
        User user = userService.createLocalUser("test@example.com", "CorrectPass123!", "Test");

        assertThrows(IllegalArgumentException.class, () -> {
            userService.changePassword(user.getId(), "WrongOldPass", "NewPass456!");
        });
    }
}

@SpringBootTest
class PasswordValidatorTest {

    @Autowired
    private PasswordValidator passwordValidator;

    @Test
    @DisplayName("유효한 비밀번호가 검증을 통과한다")
    void testValidPasswordPassesValidation() {
        assertDoesNotThrow(() -> {
            passwordValidator.validate("ValidPass123!");
        });
    }

    @Test
    @DisplayName("짧은 비밀번호가 검증에 실패한다")
    void testShortPasswordFailsValidation() {
        assertThrows(IllegalArgumentException.class, () -> {
            passwordValidator.validate("Short1!");
        });
    }

    @Test
    @DisplayName("복잡도가 낮은 비밀번호가 검증에 실패한다")
    void testWeakPasswordFailsValidation() {
        assertThrows(IllegalArgumentException.class, () -> {
            passwordValidator.validate("onlylowercase");  // 소문자만
        });

        assertThrows(IllegalArgumentException.class, () -> {
            passwordValidator.validate("ONLYUPPERCASE");  // 대문자만
        });

        assertThrows(IllegalArgumentException.class, () -> {
            passwordValidator.validate("12345678");  // 숫자만
        });
    }

    @Test
    @DisplayName("반복 문자가 있는 비밀번호가 검증에 실패한다")
    void testRepeatingCharactersFailValidation() {
        assertThrows(IllegalArgumentException.class, () -> {
            passwordValidator.validate("Passsss123!");  // 's' 5회 반복
        });
    }

    @Test
    @DisplayName("비밀번호 강도 평가가 정상 작동한다")
    void testPasswordStrengthAssessment() {
        assertEquals(0, passwordValidator.assessStrength("weak"));
        assertEquals(2, passwordValidator.assessStrength("Better1!"));
        assertEquals(4, passwordValidator.assessStrength("VeryStr0ng!Pass"));
    }
}
```

---

### Phase 2: 보안 강화 기능 (2주)

**목표:** Rate Limiting, 비밀번호 재설정, 감사 로그 등 추가 보안 기능 구현

#### 2.1 Rate Limiting (Brute Force 방어)

**의존성 추가:**
```gradle
dependencies {
    // Caffeine Cache (Google Guava 대안)
    implementation 'com.github.ben-manes.caffeine:caffeine:3.1.8'
}
```

**LoginAttemptService:**
```java
@Component
public class LoginAttemptService {

    private final Cache<String, Integer> attemptsCache;
    private static final int MAX_ATTEMPTS = 5;
    private static final int BLOCK_DURATION_MINUTES = 15;

    public LoginAttemptService() {
        attemptsCache = Caffeine.newBuilder()
            .expireAfterWrite(BLOCK_DURATION_MINUTES, TimeUnit.MINUTES)
            .build();
    }

    /**
     * 로그인 성공 시 시도 횟수 초기화
     */
    public void loginSucceeded(String email) {
        attemptsCache.invalidate(email);
    }

    /**
     * 로그인 실패 시 시도 횟수 증가
     */
    public void loginFailed(String email) {
        Integer attempts = attemptsCache.getIfPresent(email);
        attemptsCache.put(email, (attempts == null ? 0 : attempts) + 1);
    }

    /**
     * 차단 여부 확인
     */
    public boolean isBlocked(String email) {
        Integer attempts = attemptsCache.getIfPresent(email);
        return attempts != null && attempts >= MAX_ATTEMPTS;
    }

    /**
     * 남은 차단 시간 (초)
     */
    public long getRemainingBlockTime(String email) {
        // Caffeine은 expiry 시간을 직접 조회하기 어려우므로 고정값 반환
        return isBlocked(email) ? BLOCK_DURATION_MINUTES * 60 : 0;
    }
}
```

**AuthController에 적용:**
```java
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;
    private final PasswordValidator passwordValidator;
    private final LoginAttemptService loginAttemptService;

    @PostMapping("/local/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        // Rate Limiting 확인
        if (loginAttemptService.isBlocked(request.email())) {
            return ResponseEntity.status(429)
                .header("Retry-After", String.valueOf(loginAttemptService.getRemainingBlockTime(request.email())))
                .body(Map.of(
                    "error", "Too many failed login attempts",
                    "message", "계정이 일시적으로 잠겼습니다. 15분 후 다시 시도해주세요."
                ));
        }

        Optional<User> userOpt = userService.authenticateLocalUser(
            request.email(),
            request.password()
        );

        if (userOpt.isEmpty()) {
            loginAttemptService.loginFailed(request.email());
            return ResponseEntity.status(401).body(Map.of(
                "error", "Invalid credentials",
                "message", "이메일 또는 비밀번호가 올바르지 않습니다."
            ));
        }

        loginAttemptService.loginSucceeded(request.email());

        User user = userOpt.get();
        String token = jwtService.generateToken(user.getId(), user.getEmail());

        return ResponseEntity.ok(new AuthTokenResponse(token, "Bearer", 86400));
    }
}
```

#### 2.2 비밀번호 재설정 (이메일 기반)

**PasswordResetToken 엔티티:**
```java
@Entity
@Table(name = "dm_password_reset_token", indexes = {
    @Index(name = "idx_token_hash", columnList = "token_hash")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 토큰 해시 (SHA-256) - 원본 토큰은 저장하지 않음 */
    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used", nullable = false)
    private boolean used = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public PasswordResetToken(User user, String tokenHash, Instant expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public void markAsUsed() {
        this.used = true;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
```

**Flyway 마이그레이션:**
```sql
-- V4__add_password_reset_token.sql
CREATE TABLE dm_password_reset_token (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES dm_user(id) ON DELETE CASCADE,
    token_hash VARCHAR(100) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_token_hash ON dm_password_reset_token(token_hash);
CREATE INDEX idx_user_id ON dm_password_reset_token(user_id);
```

**PasswordResetService:**
```java
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordValidator passwordValidator;
    // private final EmailService emailService;  // 추후 구현

    private static final int TOKEN_EXPIRY_HOURS = 1;

    /**
     * 비밀번호 재설정 토큰 생성 및 이메일 발송
     */
    @Transactional
    public void requestPasswordReset(String email) {
        User user = userRepository.findByProviderAndProviderUserId(AuthProvider.LOCAL, email)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 보안 토큰 생성 (32 bytes = 256 bits)
        String rawToken = generateSecureToken();
        String hashedToken = hashToken(rawToken);

        // 기존 미사용 토큰 무효화
        resetTokenRepository.findByUserAndUsedFalse(user).forEach(token -> {
            token.markAsUsed();
        });

        // 새 토큰 생성
        PasswordResetToken resetToken = new PasswordResetToken(
            user,
            hashedToken,
            Instant.now().plus(TOKEN_EXPIRY_HOURS, ChronoUnit.HOURS)
        );

        resetTokenRepository.save(resetToken);

        // 이메일 발송 (추후 구현)
        // emailService.sendPasswordResetEmail(user.getEmail(), rawToken);

        // 개발 환경에서는 로그로 출력
        System.out.println("Password reset token for " + email + ": " + rawToken);
    }

    /**
     * 토큰 검증 및 비밀번호 재설정
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        String hashedToken = hashToken(token);

        PasswordResetToken resetToken = resetTokenRepository.findByTokenHashAndUsedFalse(hashedToken)
            .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 토큰입니다."));

        if (resetToken.isExpired()) {
            throw new IllegalArgumentException("만료된 토큰입니다.");
        }

        // 비밀번호 복잡도 검증
        passwordValidator.validate(newPassword);

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));

        resetToken.markAsUsed();

        userRepository.save(user);
        resetTokenRepository.save(resetToken);
    }

    /**
     * 보안 토큰 생성 (URL-safe Base64)
     */
    private String generateSecureToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 토큰 해싱 (SHA-256)
     */
    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 알고리즘을 찾을 수 없습니다.", e);
        }
    }
}
```

**AuthController에 엔드포인트 추가:**
```java
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final PasswordResetService passwordResetService;

    /**
     * 비밀번호 재설정 요청
     */
    @PostMapping("/password-reset/request")
    public ResponseEntity<Void> requestPasswordReset(@RequestBody PasswordResetRequest request) {
        passwordResetService.requestPasswordReset(request.email());

        // 보안상 이메일 존재 여부와 관계없이 성공 응답
        return ResponseEntity.noContent().build();
    }

    /**
     * 비밀번호 재설정 (토큰 사용)
     */
    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> resetPassword(@RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    public record PasswordResetRequest(String email) {}
    public record PasswordResetConfirmRequest(String token, String newPassword) {}
}
```

#### 2.3 감사 로그 (Audit Logging)

**AuthAuditLog 엔티티:**
```java
@Entity
@Table(name = "dm_auth_audit_log", indexes = {
    @Index(name = "idx_user_timestamp", columnList = "user_id, timestamp"),
    @Index(name = "idx_event_timestamp", columnList = "event, timestamp")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AuthEvent event;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(length = 500)
    private String details;

    public AuthAuditLog(User user, AuthEvent event, String ipAddress, String userAgent, String details) {
        this.user = user;
        this.event = event;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.details = details;
        this.timestamp = Instant.now();
    }

    public enum AuthEvent {
        LOGIN_SUCCESS,
        LOGIN_FAILED,
        PASSWORD_CHANGED,
        PASSWORD_RESET_REQUESTED,
        PASSWORD_RESET_COMPLETED,
        ACCOUNT_CREATED,
        ACCOUNT_LOCKED,
        LOGOUT
    }
}
```

**Flyway 마이그레이션:**
```sql
-- V5__add_auth_audit_log.sql
CREATE TABLE dm_auth_audit_log (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES dm_user(id) ON DELETE SET NULL,
    event VARCHAR(50) NOT NULL,
    ip_address VARCHAR(45),
    user_agent VARCHAR(255),
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    details VARCHAR(500)
);

CREATE INDEX idx_user_timestamp ON dm_auth_audit_log(user_id, timestamp);
CREATE INDEX idx_event_timestamp ON dm_auth_audit_log(event, timestamp);
```

**AuthAuditLogger:**
```java
@Component
@RequiredArgsConstructor
public class AuthAuditLogger {

    private final AuthAuditLogRepository auditLogRepository;

    /**
     * 인증 이벤트 로깅 (비동기)
     */
    @Async
    public void logAuthEvent(
        User user,
        AuthAuditLog.AuthEvent event,
        HttpServletRequest request,
        String details
    ) {
        String ipAddress = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        AuthAuditLog log = new AuthAuditLog(user, event, ipAddress, userAgent, details);
        auditLogRepository.save(log);
    }

    /**
     * 인증 이벤트 로깅 (사용자 없이, 예: 로그인 실패)
     */
    @Async
    public void logAuthEvent(
        AuthAuditLog.AuthEvent event,
        HttpServletRequest request,
        String details
    ) {
        logAuthEvent(null, event, request, details);
    }

    /**
     * 클라이언트 IP 주소 추출
     */
    private String getClientIp(HttpServletRequest request) {
        String[] headers = {
            "X-Forwarded-For",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED",
            "HTTP_VIA",
            "REMOTE_ADDR"
        };

        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // X-Forwarded-For can contain multiple IPs, take the first one
                return ip.split(",")[0].trim();
            }
        }

        return request.getRemoteAddr();
    }
}
```

**비동기 설정 (Application.java):**
```java
@SpringBootApplication
@EnableAsync
public class DocstApplication {
    public static void main(String[] args) {
        SpringApplication.run(DocstApplication.class, args);
    }

    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("audit-log-");
        executor.initialize();
        return executor;
    }
}
```

**AuthController에 감사 로그 추가:**
```java
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthAuditLogger auditLogger;

    @PostMapping("/local/register")
    public ResponseEntity<UserResponse> register(
        @RequestBody RegisterRequest request,
        HttpServletRequest httpRequest
    ) {
        passwordValidator.validate(request.password());

        User user = userService.createLocalUser(
            request.email(),
            request.password(),
            request.displayName()
        );

        // 감사 로그
        auditLogger.logAuthEvent(user, AuthEvent.ACCOUNT_CREATED, httpRequest, "Local account created");

        UserResponse response = new UserResponse(
            user.getId(),
            user.getProvider().name(),
            user.getProviderUserId(),
            user.getEmail(),
            user.getDisplayName(),
            user.getCreatedAt()
        );

        return ResponseEntity.status(201).body(response);
    }

    @PostMapping("/local/login")
    public ResponseEntity<?> login(
        @RequestBody LoginRequest request,
        HttpServletRequest httpRequest
    ) {
        if (loginAttemptService.isBlocked(request.email())) {
            auditLogger.logAuthEvent(AuthEvent.LOGIN_FAILED, httpRequest,
                "Account blocked due to too many failed attempts: " + request.email());

            return ResponseEntity.status(429)
                .header("Retry-After", String.valueOf(loginAttemptService.getRemainingBlockTime(request.email())))
                .body(Map.of(
                    "error", "Too many failed login attempts",
                    "message", "계정이 일시적으로 잠겼습니다. 15분 후 다시 시도해주세요."
                ));
        }

        Optional<User> userOpt = userService.authenticateLocalUser(
            request.email(),
            request.password()
        );

        if (userOpt.isEmpty()) {
            loginAttemptService.loginFailed(request.email());
            auditLogger.logAuthEvent(AuthEvent.LOGIN_FAILED, httpRequest,
                "Invalid credentials for: " + request.email());

            return ResponseEntity.status(401).body(Map.of(
                "error", "Invalid credentials",
                "message", "이메일 또는 비밀번호가 올바르지 않습니다."
            ));
        }

        loginAttemptService.loginSucceeded(request.email());

        User user = userOpt.get();

        // 감사 로그
        auditLogger.logAuthEvent(user, AuthEvent.LOGIN_SUCCESS, httpRequest, "Successful login");

        String token = jwtService.generateToken(user.getId(), user.getEmail());

        return ResponseEntity.ok(new AuthTokenResponse(token, "Bearer", 86400));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
        Authentication authentication,
        @RequestBody ChangePasswordRequest request,
        HttpServletRequest httpRequest
    ) {
        if (authentication == null || !(authentication.getPrincipal() instanceof User)) {
            return ResponseEntity.status(401).build();
        }

        User user = (User) authentication.getPrincipal();

        passwordValidator.validate(request.newPassword());

        userService.changePassword(
            user.getId(),
            request.oldPassword(),
            request.newPassword()
        );

        // 감사 로그
        auditLogger.logAuthEvent(user, AuthEvent.PASSWORD_CHANGED, httpRequest, "Password changed");

        return ResponseEntity.noContent().build();
    }
}
```

---

### Phase 3: 엔터프라이즈 기능 (3-4주, 선택적)

**목표:** MFA, 비밀번호 정책, 세션 관리 등 고급 보안 기능

#### 3.1 다중 인증 요소 (MFA/2FA)

**의존성 추가:**
```gradle
dependencies {
    // Google Authenticator (TOTP)
    implementation 'com.warrenstrange:googleauth:1.5.0'

    // QR Code 생성 (선택)
    implementation 'com.google.zxing:core:3.5.3'
    implementation 'com.google.zxing:javase:3.5.3'
}
```

**UserMfa 엔티티:**
```java
@Entity
@Table(name = "dm_user_mfa")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserMfa {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /** MFA 비밀키 (암호화 저장) */
    @Column(name = "secret_key", nullable = false)
    private String secretKey;

    @Column(nullable = false)
    private boolean enabled = false;

    @Column(name = "enabled_at")
    private Instant enabledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UserMfa(User user, String secretKey) {
        this.user = user;
        this.secretKey = secretKey;
        this.createdAt = Instant.now();
    }

    public void enable() {
        this.enabled = true;
        this.enabledAt = Instant.now();
    }

    public void disable() {
        this.enabled = false;
        this.enabledAt = null;
    }
}
```

**MfaService:**
```java
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MfaService {

    private final UserMfaRepository mfaRepository;
    private final EncryptionService encryptionService;  // 암호화 서비스 (추후 구현)
    private final GoogleAuthenticator authenticator = new GoogleAuthenticator();

    /**
     * MFA 설정 시작 (QR 코드 생성)
     */
    @Transactional
    public MfaSetupResponse setupMfa(User user) {
        // 기존 MFA 설정 확인
        Optional<UserMfa> existingMfa = mfaRepository.findByUser(user);
        if (existingMfa.isPresent() && existingMfa.get().isEnabled()) {
            throw new IllegalStateException("MFA가 이미 활성화되어 있습니다.");
        }

        // 새 비밀키 생성
        GoogleAuthenticatorKey key = authenticator.createCredentials();
        String encryptedKey = encryptionService.encrypt(key.getKey());

        // MFA 엔티티 생성 (비활성화 상태)
        UserMfa mfa = existingMfa.orElse(new UserMfa(user, encryptedKey));
        mfa.setSecretKey(encryptedKey);
        mfaRepository.save(mfa);

        // QR 코드 URL 생성
        String qrCodeUrl = GoogleAuthenticatorQRGenerator.getOtpAuthURL(
            "Docst",
            user.getEmail(),
            key
        );

        return new MfaSetupResponse(key.getKey(), qrCodeUrl);
    }

    /**
     * MFA 활성화 (검증 후)
     */
    @Transactional
    public void enableMfa(User user, int verificationCode) {
        UserMfa mfa = mfaRepository.findByUser(user)
            .orElseThrow(() -> new IllegalStateException("MFA 설정이 없습니다."));

        String secretKey = encryptionService.decrypt(mfa.getSecretKey());

        // 검증 코드 확인
        if (!authenticator.authorize(secretKey, verificationCode)) {
            throw new IllegalArgumentException("잘못된 인증 코드입니다.");
        }

        mfa.enable();
        mfaRepository.save(mfa);
    }

    /**
     * MFA 비활성화
     */
    @Transactional
    public void disableMfa(User user, int verificationCode) {
        UserMfa mfa = mfaRepository.findByUser(user)
            .orElseThrow(() -> new IllegalStateException("MFA 설정이 없습니다."));

        String secretKey = encryptionService.decrypt(mfa.getSecretKey());

        // 검증 코드 확인
        if (!authenticator.authorize(secretKey, verificationCode)) {
            throw new IllegalArgumentException("잘못된 인증 코드입니다.");
        }

        mfa.disable();
        mfaRepository.save(mfa);
    }

    /**
     * MFA 검증
     */
    public boolean verifyMfa(User user, int code) {
        Optional<UserMfa> mfaOpt = mfaRepository.findByUser(user);

        if (mfaOpt.isEmpty() || !mfaOpt.get().isEnabled()) {
            return true;  // MFA 비활성화 상태
        }

        String secretKey = encryptionService.decrypt(mfaOpt.get().getSecretKey());
        return authenticator.authorize(secretKey, code);
    }

    /**
     * MFA 활성화 여부
     */
    public boolean isMfaEnabled(User user) {
        return mfaRepository.findByUser(user)
            .map(UserMfa::isEnabled)
            .orElse(false);
    }

    public record MfaSetupResponse(String secretKey, String qrCodeUrl) {}
}
```

**로그인 플로우에 MFA 추가:**
```java
@PostMapping("/local/login")
public ResponseEntity<?> login(
    @RequestBody LoginRequest request,
    HttpServletRequest httpRequest
) {
    // ... 기존 Rate Limiting 및 인증 로직 ...

    User user = userOpt.get();

    // MFA 확인
    if (mfaService.isMfaEnabled(user)) {
        if (request.mfaCode() == null) {
            // MFA 코드 요청
            return ResponseEntity.status(403).body(Map.of(
                "requireMfa", true,
                "message", "2단계 인증 코드를 입력해주세요."
            ));
        }

        if (!mfaService.verifyMfa(user, request.mfaCode())) {
            auditLogger.logAuthEvent(user, AuthEvent.LOGIN_FAILED, httpRequest,
                "Invalid MFA code");

            return ResponseEntity.status(401).body(Map.of(
                "error", "Invalid MFA code",
                "message", "잘못된 인증 코드입니다."
            ));
        }
    }

    loginAttemptService.loginSucceeded(request.email());
    auditLogger.logAuthEvent(user, AuthEvent.LOGIN_SUCCESS, httpRequest, "Successful login");

    String token = jwtService.generateToken(user.getId(), user.getEmail());
    return ResponseEntity.ok(new AuthTokenResponse(token, "Bearer", 86400));
}

// DTO 수정
public record LoginRequest(String email, String password, Integer mfaCode) {}
```

#### 3.2 비밀번호 정책 관리

**PasswordPolicyConfig:**
```java
@Configuration
@ConfigurationProperties(prefix = "docst.security.password-policy")
@Data
public class PasswordPolicyConfig {
    private int minLength = 8;
    private int maxLength = 128;
    private int minStrength = 3;
    private boolean requireUppercase = true;
    private boolean requireLowercase = true;
    private boolean requireDigit = true;
    private boolean requireSpecial = true;
    private int maxRepeatingChars = 3;
    private int historyCount = 5;  // 최근 N개 비밀번호 재사용 금지
    private int expirationDays = 90;  // 비밀번호 만료 기간 (0 = 무제한)
}
```

**application.yml:**
```yaml
docst:
  security:
    password-policy:
      min-length: 8
      max-length: 128
      min-strength: 3
      history-count: 5
      expiration-days: 90
```

**PasswordHistory 엔티티:**
```java
@Entity
@Table(name = "dm_password_history", indexes = {
    @Index(name = "idx_user_created", columnList = "user_id, created_at DESC")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "password_hash", nullable = false, length = 150)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public PasswordHistory(User user, String passwordHash) {
        this.user = user;
        this.passwordHash = passwordHash;
        this.createdAt = Instant.now();
    }
}
```

**PasswordPolicyService:**
```java
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PasswordPolicyService {

    private final PasswordHistoryRepository passwordHistoryRepository;
    private final PasswordPolicyConfig policyConfig;
    private final PasswordEncoder passwordEncoder;

    /**
     * 비밀번호 정책 검증 (재사용 방지 포함)
     */
    public void validatePasswordPolicy(User user, String newPassword) {
        // 이전 비밀번호 재사용 방지
        if (policyConfig.getHistoryCount() > 0) {
            List<PasswordHistory> history = passwordHistoryRepository
                .findTopNByUserOrderByCreatedAtDesc(user, policyConfig.getHistoryCount());

            for (PasswordHistory old : history) {
                if (passwordEncoder.matches(newPassword, old.getPasswordHash())) {
                    throw new PasswordPolicyViolationException(
                        "최근 " + policyConfig.getHistoryCount() + "개의 비밀번호는 재사용할 수 없습니다."
                    );
                }
            }
        }
    }

    /**
     * 비밀번호 히스토리 저장
     */
    @Transactional
    public void recordPasswordHistory(User user, String passwordHash) {
        PasswordHistory history = new PasswordHistory(user, passwordHash);
        passwordHistoryRepository.save(history);
    }

    /**
     * 비밀번호 만료 확인
     */
    public boolean isPasswordExpired(User user) {
        if (policyConfig.getExpirationDays() <= 0) {
            return false;  // 만료 기능 비활성화
        }

        PasswordHistory latest = passwordHistoryRepository
            .findTopByUserOrderByCreatedAtDesc(user);

        if (latest == null) {
            return false;
        }

        Instant expirationDate = latest.getCreatedAt()
            .plus(policyConfig.getExpirationDays(), ChronoUnit.DAYS);

        return Instant.now().isAfter(expirationDate);
    }
}
```

**UserService에 히스토리 기록 추가:**
```java
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserService {

    private final PasswordPolicyService passwordPolicyService;

    @Transactional
    public User createLocalUser(String email, String password, String displayName) {
        // ... 기존 로직 ...

        String hashedPassword = passwordEncoder.encode(password);
        User user = new User(AuthProvider.LOCAL, email, email, displayName);
        user.setPasswordHash(hashedPassword);

        User savedUser = userRepository.save(user);

        // 비밀번호 히스토리 기록
        passwordPolicyService.recordPasswordHistory(savedUser, hashedPassword);

        return savedUser;
    }

    @Transactional
    public void changePassword(UUID userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        if (!user.isLocalUser()) {
            throw new IllegalStateException("LOCAL 사용자만 비밀번호를 변경할 수 있습니다.");
        }

        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("기존 비밀번호가 일치하지 않습니다.");
        }

        // 비밀번호 정책 검증 (재사용 방지)
        passwordPolicyService.validatePasswordPolicy(user, newPassword);

        String hashedPassword = passwordEncoder.encode(newPassword);
        user.setPasswordHash(hashedPassword);
        userRepository.save(user);

        // 비밀번호 히스토리 기록
        passwordPolicyService.recordPasswordHistory(user, hashedPassword);
    }
}
```

---

## 보안 체크리스트

### Phase 1: MVP (Argon2id 기본 인증)
- [ ] Bouncy Castle 의존성 추가
- [ ] User 엔티티에 password_hash 필드 추가 (VARCHAR(150))
- [ ] Argon2PasswordEncoder 설정 (OWASP 권장 파라미터)
- [ ] 회원가입 엔드포인트 구현
- [ ] 로그인 시 비밀번호 검증
- [ ] 비밀번호 복잡도 검증 (최소 8자, 3가지 문자 유형, 반복 문자 제한)
- [ ] 비밀번호 변경 기능
- [ ] HTTPS 강제 (프로덕션)
- [ ] 단위 테스트 작성

### Phase 2: 보안 강화
- [ ] Rate Limiting 구현 (5회 실패 시 15분 차단)
- [ ] 비밀번호 재설정 기능 (SHA-256 해시 토큰)
- [ ] 감사 로그 (로그인 성공/실패, 비밀번호 변경, IP/User-Agent 추적)
- [ ] 비동기 로깅 설정
- [ ] 통합 테스트

### Phase 3: 엔터프라이즈 (선택적)
- [ ] TOTP 기반 2FA/MFA
- [ ] 비밀번호 정책 관리 (만료, 히스토리)
- [ ] 세션 관리 (동시 로그인 제한)
- [ ] 계정 잠금 (5회 실패 시 30분)
- [ ] 의심스러운 로그인 감지
- [ ] 관리자 대시보드

---

## 참고 자료

### 공식 문서
- [Spring Security Password Storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html)
- [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)
- [Argon2 RFC 9106](https://datatracker.ietf.org/doc/html/rfc9106)

### 알고리즘 비교
- [Argon2 vs bcrypt vs scrypt - Stytch](https://stytch.com/blog/argon2-vs-bcrypt-vs-scrypt/)
- [Password Hashing Guide 2025](https://guptadeepak.com/the-complete-guide-to-password-hashing-argon2-vs-bcrypt-vs-scrypt-vs-pbkdf2-2026/)
- [Best Password Hashing Algorithms 2025](https://bellatorcyber.com/blog/best-password-hashing-algorithms-of-2023/)

### 구현 가이드
- [Best Practices for Storing and Validating Passwords in Java](https://www.javacodegeeks.com/2025/05/best-practices-for-storing-and-validating-passwords-in-java-bcrypt-argon2-pbkdf2.html)
- [Secure Password Hashing in Java - DZone](https://dzone.com/articles/secure-password-hashing-in-java)

---

## 구현 우선순위

1. **즉시 구현 (보안 취약점 해소)**: Phase 1 전체 (Argon2id 기본 인증) - 1주
2. **단기 (2-3주)**: Phase 2 전체 (Rate Limiting, 비밀번호 재설정, 감사 로그)
3. **중기 (선택적)**: Phase 3 중 필요한 기능 선택적 구현

**권장 시작점:**
- 개발/테스트 환경: Phase 1 완료 후 테스트
- 프로덕션 환경: Phase 2까지 완료 후 출시
- 엔터프라이즈 요구사항: Phase 3 기능 선택적 추가
