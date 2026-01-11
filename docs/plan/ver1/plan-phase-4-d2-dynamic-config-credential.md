# Phase 4-D2: 동적 설정 및 크리덴셜 시스템

> **작성일**: 2025-12-31
> **기반**: Phase 4-D RAG 설정 시스템 완료
> **목표**: 외부 서비스 설정을 yml에서 완전히 제거하고 DB 기반 동적 관리로 전환

---

## 설계 결정 사항

| 항목 | 결정 | 이유 |
|------|------|------|
| API 키 범위 | **시스템 + 프로젝트 오버라이드** | 기본 키 공유 + 프로젝트별 비용 분리 |
| 동적화 범위 | **전체 외부 서비스** | Neo4j, Ollama, OpenAI 등 모든 외부 서비스 |
| 하위 호환성 | **없음** | yml 설정 완전 제거, DB만 사용 |
| UI 구현 | **관리자 설정 페이지 포함** | 백엔드 + 프론트엔드 함께 |

---

## 아키텍처

```
┌─────────────────────────────────────────────────────────────┐
│                    application.yml (부트스트랩만)            │
│  - PostgreSQL 메인 연결 (chicken-egg 문제 해결)              │
│  - JWT Secret                                               │
│  - Encryption Key                                           │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                 dm_system_config (런타임 설정)               │
│  - neo4j.uri, neo4j.enabled                                 │
│  - ollama.base-url, ollama.enabled                          │
│  - embedding.default-provider, embedding.default-model      │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│              dm_credential (암호화된 크리덴셜)               │
│  - scope: SYSTEM (전역) / PROJECT (프로젝트별)              │
│  - type: OPENAI_API_KEY, NEO4J_AUTH, ANTHROPIC_API_KEY 등   │
│  - encrypted_secret: AES-256-GCM 암호화                     │
└─────────────────────────────────────────────────────────────┘

설정 우선순위: 프로젝트 크리덴셜 > 시스템 크리덴셜 (폴백 없음)
```

---

## yml에서 제거되는 설정

```yaml
# application.yml에서 완전히 제거
spring:
  ai:
    openai:
      api-key: (제거)           # → dm_credential (OPENAI_API_KEY)
    ollama:
      base-url: (제거)          # → dm_system_config
      embedding:
        enabled: (제거)         # → dm_system_config
  neo4j:
    uri: (제거)                 # → dm_system_config
    authentication: (제거)      # → dm_credential (NEO4J_AUTH)

docst:
  rag:
    neo4j:
      enabled: (제거)           # → dm_system_config
      entity-extraction-model: (제거)  # → Project.ragConfig
      max-hop: (제거)           # → Project.ragConfig
```

---

## 데이터베이스 스키마

### V11__add_system_config_and_credential_scope.sql

```sql
-- ============================================================
-- 1. 시스템 설정 테이블
-- ============================================================
CREATE TABLE dm_system_config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    config_key VARCHAR(100) NOT NULL UNIQUE,
    config_value TEXT,
    config_type VARCHAR(50) NOT NULL DEFAULT 'STRING',
    description VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ
);

CREATE INDEX idx_system_config_key ON dm_system_config(config_key);

COMMENT ON TABLE dm_system_config IS '시스템 전역 설정 (외부 서비스 URL 등)';
COMMENT ON COLUMN dm_system_config.config_type IS 'STRING, INTEGER, BOOLEAN, JSON';

-- 초기 설정값
INSERT INTO dm_system_config (config_key, config_value, config_type, description) VALUES
-- Neo4j
('neo4j.uri', 'bolt://localhost:7697', 'STRING', 'Neo4j Bolt URI'),
('neo4j.enabled', 'false', 'BOOLEAN', 'Neo4j 활성화 여부'),
-- Ollama
('ollama.base-url', 'http://localhost:11434', 'STRING', 'Ollama 서버 URL'),
('ollama.enabled', 'false', 'BOOLEAN', 'Ollama 활성화 여부'),
-- Embedding 기본값
('embedding.default-provider', 'openai', 'STRING', '기본 임베딩 제공자 (openai/ollama)'),
('embedding.default-model', 'text-embedding-3-small', 'STRING', '기본 임베딩 모델'),
('embedding.default-dimensions', '1536', 'INTEGER', '기본 임베딩 차원');

-- ============================================================
-- 2. 크리덴셜 스코프 및 프로젝트 연결 확장
-- ============================================================

-- user_id를 nullable로 변경 (SYSTEM 스코프는 user_id 없음)
ALTER TABLE dm_credential ALTER COLUMN user_id DROP NOT NULL;

-- 스코프 컬럼 추가
ALTER TABLE dm_credential ADD COLUMN scope VARCHAR(20) NOT NULL DEFAULT 'USER';

-- 프로젝트 연결 (PROJECT 스코프용)
ALTER TABLE dm_credential ADD COLUMN project_id UUID REFERENCES dm_project(id) ON DELETE CASCADE;

-- 기존 유니크 제약조건 제거 및 새로운 제약조건 추가
ALTER TABLE dm_credential DROP CONSTRAINT IF EXISTS uq_credential_user_name;
ALTER TABLE dm_credential ADD CONSTRAINT uq_credential_scope_name
    UNIQUE NULLS NOT DISTINCT (scope, user_id, project_id, name);

-- 인덱스
CREATE INDEX idx_credential_scope ON dm_credential(scope);
CREATE INDEX idx_credential_project_id ON dm_credential(project_id) WHERE project_id IS NOT NULL;
CREATE INDEX idx_credential_type_scope ON dm_credential(type, scope);

-- 스코프 체크 제약조건
ALTER TABLE dm_credential ADD CONSTRAINT chk_credential_scope
    CHECK (scope IN ('USER', 'SYSTEM', 'PROJECT'));

-- 스코프별 필수 필드 검증
ALTER TABLE dm_credential ADD CONSTRAINT chk_credential_scope_fields
    CHECK (
        (scope = 'USER' AND user_id IS NOT NULL AND project_id IS NULL) OR
        (scope = 'SYSTEM' AND user_id IS NULL AND project_id IS NULL) OR
        (scope = 'PROJECT' AND project_id IS NOT NULL)
    );

-- 타입 체크 제약조건 업데이트 (새 타입 추가)
ALTER TABLE dm_credential DROP CONSTRAINT IF EXISTS chk_credential_type;
ALTER TABLE dm_credential ADD CONSTRAINT chk_credential_type
    CHECK (type IN (
        'GITHUB_PAT', 'BASIC_AUTH', 'SSH_KEY',
        'OPENAI_API_KEY', 'NEO4J_AUTH', 'ANTHROPIC_API_KEY', 'CUSTOM_API_KEY'
    ));
```

---

## 백엔드 구현

### 신규 파일 (11개)

| 경로 | 설명 |
|------|------|
| `domain/SystemConfig.java` | 시스템 설정 엔티티 |
| `domain/CredentialScope.java` | 크리덴셜 스코프 열거형 |
| `repository/SystemConfigRepository.java` | 시스템 설정 리포지토리 |
| `service/SystemConfigService.java` | 시스템 설정 서비스 (5분 캐싱) |
| `service/DynamicCredentialResolver.java` | 크리덴셜 우선순위 해결 |
| `embedding/DynamicEmbeddingClientFactory.java` | 동적 임베딩 클라이언트 |
| `config/DynamicNeo4jConfig.java` | 동적 Neo4j Driver |
| `api/AdminConfigController.java` | 시스템 설정 API |
| `api/AdminCredentialController.java` | 시스템/프로젝트 크리덴셜 API |
| `api/ProjectCredentialController.java` | 프로젝트 크리덴셜 API |
| `db/migration/V11__*.sql` | Flyway 마이그레이션 |

### 수정 파일 (8개)

| 경로 | 변경 내용 |
|------|----------|
| `domain/Credential.java` | scope, projectId 필드, CredentialType 확장 |
| `repository/CredentialRepository.java` | 스코프 기반 쿼리 추가 |
| `service/CredentialService.java` | 시스템/프로젝트 크리덴셜 메서드 |
| `rag/config/RagConfigDto.java` | credentialId 필드 (version 1.1) |
| `rag/config/RagConfigService.java` | 크리덴셜 기반 API 키 해결 |
| `api/ApiModels.java` | 시스템 설정/크리덴셜 DTO |
| `embedding/DocstEmbeddingService.java` | DynamicEmbeddingClientFactory 사용 |
| `application.yml` | 외부 서비스 설정 제거 |

---

## 핵심 구현 상세

### 1. Credential 엔티티 확장

```java
// domain/CredentialScope.java
public enum CredentialScope {
    USER,      // 사용자별 (기존)
    SYSTEM,    // 시스템 전역 (user_id = null)
    PROJECT    // 프로젝트별 (project_id 필수)
}

// domain/Credential.java 수정
public enum CredentialType {
    // 기존
    GITHUB_PAT,
    BASIC_AUTH,
    SSH_KEY,
    // 신규
    OPENAI_API_KEY,
    NEO4J_AUTH,           // JSON: {"username":"...", "password":"..."}
    ANTHROPIC_API_KEY,
    CUSTOM_API_KEY
}

// 새 필드
@Enumerated(EnumType.STRING)
@Column(nullable = false)
private CredentialScope scope = CredentialScope.USER;

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "project_id")
private Project project;

// 생성자 추가
public static Credential createSystemCredential(String name, CredentialType type, String encryptedSecret) {
    Credential c = new Credential();
    c.scope = CredentialScope.SYSTEM;
    c.name = name;
    c.type = type;
    c.encryptedSecret = encryptedSecret;
    return c;
}

public static Credential createProjectCredential(Project project, String name, CredentialType type, String encryptedSecret) {
    Credential c = new Credential();
    c.scope = CredentialScope.PROJECT;
    c.project = project;
    c.name = name;
    c.type = type;
    c.encryptedSecret = encryptedSecret;
    return c;
}
```

### 2. SystemConfigService (캐싱)

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class SystemConfigService {

    private final SystemConfigRepository repository;
    private final ConcurrentHashMap<String, CachedValue> cache = new ConcurrentHashMap<>();
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    // 설정 키 상수
    public static final String NEO4J_URI = "neo4j.uri";
    public static final String NEO4J_ENABLED = "neo4j.enabled";
    public static final String OLLAMA_BASE_URL = "ollama.base-url";
    public static final String OLLAMA_ENABLED = "ollama.enabled";
    public static final String EMBEDDING_DEFAULT_PROVIDER = "embedding.default-provider";
    public static final String EMBEDDING_DEFAULT_MODEL = "embedding.default-model";
    public static final String EMBEDDING_DEFAULT_DIMENSIONS = "embedding.default-dimensions";

    public String getString(String key) {
        return getCached(key).orElse(null);
    }

    public String getString(String key, String defaultValue) {
        return getCached(key).orElse(defaultValue);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return getCached(key).map(Boolean::parseBoolean).orElse(defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        return getCached(key).map(Integer::parseInt).orElse(defaultValue);
    }

    @Transactional
    public void setConfig(String key, String value) {
        SystemConfig config = repository.findByConfigKey(key)
            .orElseGet(() -> new SystemConfig(key));
        config.setConfigValue(value);
        config.setUpdatedAt(Instant.now());
        repository.save(config);
        cache.remove(key);  // 캐시 무효화
    }

    public void refreshCache() {
        cache.clear();
        log.info("System config cache cleared");
    }

    private Optional<String> getCached(String key) {
        CachedValue cached = cache.get(key);
        if (cached != null && !cached.isExpired()) {
            return Optional.ofNullable(cached.value);
        }

        return repository.findByConfigKey(key)
            .map(config -> {
                cache.put(key, new CachedValue(config.getConfigValue()));
                return config.getConfigValue();
            });
    }

    private record CachedValue(String value, Instant cachedAt) {
        CachedValue(String value) { this(value, Instant.now()); }
        boolean isExpired() { return Instant.now().isAfter(cachedAt.plus(CACHE_TTL)); }
    }
}
```

### 3. DynamicCredentialResolver

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class DynamicCredentialResolver {

    private final CredentialRepository credentialRepository;
    private final EncryptionService encryptionService;

    /**
     * API 키 해결 (프로젝트 > 시스템 우선순위)
     * @throws IllegalStateException 크리덴셜을 찾을 수 없을 때
     */
    public String resolveApiKey(UUID projectId, CredentialType type) {
        // 1. 프로젝트 레벨 크리덴셜
        if (projectId != null) {
            Optional<Credential> projectCred = credentialRepository
                .findByProjectIdAndTypeAndScopeAndActiveTrue(projectId, type, CredentialScope.PROJECT);
            if (projectCred.isPresent()) {
                log.debug("Using project credential for type {} in project {}", type, projectId);
                return decrypt(projectCred.get());
            }
        }

        // 2. 시스템 레벨 크리덴셜
        Optional<Credential> systemCred = credentialRepository
            .findByScopeAndTypeAndActiveTrue(CredentialScope.SYSTEM, type);
        if (systemCred.isPresent()) {
            log.debug("Using system credential for type {}", type);
            return decrypt(systemCred.get());
        }

        // 3. 없으면 예외 (폴백 없음)
        throw new IllegalStateException(
            "No credential found for type " + type +
            (projectId != null ? " in project " + projectId : " at system level")
        );
    }

    /**
     * API 키 해결 (Optional 반환, 예외 없음)
     */
    public Optional<String> resolveApiKeyOptional(UUID projectId, CredentialType type) {
        try {
            return Optional.of(resolveApiKey(projectId, type));
        } catch (IllegalStateException e) {
            return Optional.empty();
        }
    }

    /**
     * 시스템 크리덴셜만 조회
     */
    public Optional<String> resolveSystemApiKey(CredentialType type) {
        return credentialRepository
            .findByScopeAndTypeAndActiveTrue(CredentialScope.SYSTEM, type)
            .map(this::decrypt);
    }

    private String decrypt(Credential credential) {
        return encryptionService.decrypt(credential.getEncryptedSecret());
    }
}
```

### 4. RagConfigDto v1.1

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record RagConfigDto(
    String version,
    EmbeddingConfig embedding,
    PgVectorConfig pgvector,
    Neo4jConfig neo4j,
    HybridConfig hybrid
) {
    public static final String CURRENT_VERSION = "1.1";

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EmbeddingConfig(
        String provider,
        String model,
        Integer dimensions,
        String credentialId  // 프로젝트 크리덴셜 UUID (선택)
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Neo4jConfig(
        Boolean enabled,
        Integer maxHop,
        String entityExtractionModel,
        String credentialId  // LLM API 키용 크리덴셜 (선택)
    ) {}

    // PgVectorConfig, HybridConfig는 기존과 동일
}
```

### 5. DynamicEmbeddingClientFactory

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class DynamicEmbeddingClientFactory {

    private final DynamicCredentialResolver credentialResolver;
    private final SystemConfigService systemConfigService;

    /**
     * 프로젝트용 EmbeddingModel 생성
     */
    public EmbeddingModel createEmbeddingModel(UUID projectId, ResolvedRagConfig config) {
        String provider = config.getEmbeddingProvider();

        return switch (provider.toLowerCase()) {
            case "openai" -> createOpenAiModel(projectId, config);
            case "ollama" -> createOllamaModel(config);
            default -> throw new IllegalArgumentException("Unknown embedding provider: " + provider);
        };
    }

    private EmbeddingModel createOpenAiModel(UUID projectId, ResolvedRagConfig config) {
        String apiKey = credentialResolver.resolveApiKey(projectId, CredentialType.OPENAI_API_KEY);

        OpenAiApi openAiApi = OpenAiApi.builder()
            .apiKey(apiKey)
            .build();

        return OpenAiEmbeddingModel.builder()
            .openAiApi(openAiApi)
            .model(config.getEmbeddingModel())
            .build();
    }

    private EmbeddingModel createOllamaModel(ResolvedRagConfig config) {
        String baseUrl = systemConfigService.getString(SystemConfigService.OLLAMA_BASE_URL);
        boolean enabled = systemConfigService.getBoolean(SystemConfigService.OLLAMA_ENABLED, false);

        if (!enabled) {
            throw new IllegalStateException("Ollama is not enabled in system configuration");
        }

        OllamaApi ollamaApi = OllamaApi.builder()
            .baseUrl(baseUrl)
            .build();

        return OllamaEmbeddingModel.builder()
            .ollamaApi(ollamaApi)
            .model(config.getEmbeddingModel())
            .build();
    }
}
```

### 6. DynamicNeo4jConfig

```java
@Configuration
@RequiredArgsConstructor
@Slf4j
public class DynamicNeo4jConfig {

    private final SystemConfigService systemConfigService;
    private final DynamicCredentialResolver credentialResolver;
    private final ObjectMapper objectMapper;

    @Bean
    @Lazy  // 필요할 때만 생성
    public Driver neo4jDriver() {
        boolean enabled = systemConfigService.getBoolean(SystemConfigService.NEO4J_ENABLED, false);
        if (!enabled) {
            log.info("Neo4j is disabled in system configuration");
            return null;
        }

        String uri = systemConfigService.getString(SystemConfigService.NEO4J_URI);
        if (uri == null || uri.isBlank()) {
            throw new IllegalStateException("Neo4j URI is not configured");
        }

        // NEO4J_AUTH 크리덴셜에서 인증정보 로드
        String authJson = credentialResolver.resolveApiKey(null, CredentialType.NEO4J_AUTH);
        Neo4jAuth auth = parseNeo4jAuth(authJson);

        log.info("Creating Neo4j driver for URI: {}", uri);
        return GraphDatabase.driver(uri, AuthTokens.basic(auth.username(), auth.password()));
    }

    private Neo4jAuth parseNeo4jAuth(String json) {
        try {
            return objectMapper.readValue(json, Neo4jAuth.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Neo4j auth credentials", e);
        }
    }

    private record Neo4jAuth(String username, String password) {}
}
```

---

## API 명세

### 시스템 설정 API (`/api/admin/config`)

| Method | Path | 설명 | 권한 |
|--------|------|------|------|
| GET | `/api/admin/config` | 전체 설정 목록 | ADMIN |
| GET | `/api/admin/config/{key}` | 특정 설정 조회 | ADMIN |
| PUT | `/api/admin/config/{key}` | 설정 업데이트 | ADMIN |
| POST | `/api/admin/config/refresh` | 캐시 갱신 | ADMIN |

### 시스템 크리덴셜 API (`/api/admin/credentials`)

| Method | Path | 설명 | 권한 |
|--------|------|------|------|
| GET | `/api/admin/credentials` | 시스템 크리덴셜 목록 | ADMIN |
| GET | `/api/admin/credentials/{id}` | 특정 크리덴셜 조회 | ADMIN |
| POST | `/api/admin/credentials` | 시스템 크리덴셜 생성 | ADMIN |
| PUT | `/api/admin/credentials/{id}` | 수정 | ADMIN |
| DELETE | `/api/admin/credentials/{id}` | 삭제 | ADMIN |

### 프로젝트 크리덴셜 API (`/api/projects/{projectId}/credentials`)

| Method | Path | 설명 | 권한 |
|--------|------|------|------|
| GET | `/api/projects/{id}/credentials` | 프로젝트 크리덴셜 목록 | PROJECT_MEMBER |
| POST | `/api/projects/{id}/credentials` | 프로젝트 크리덴셜 생성 | PROJECT_ADMIN |
| PUT | `/api/projects/{id}/credentials/{cid}` | 수정 | PROJECT_ADMIN |
| DELETE | `/api/projects/{id}/credentials/{cid}` | 삭제 | PROJECT_ADMIN |

---

## 프론트엔드 구현

### 신규 파일

```
frontend/
├── app/[locale]/admin/
│   └── settings/
│       ├── page.tsx                    # 관리자 설정 메인
│       └── credentials/
│           └── page.tsx                # 시스템 크리덴셜 관리
├── components/admin/
│   ├── system-config-form.tsx          # 시스템 설정 폼 (Neo4j, Ollama, Embedding)
│   ├── system-credential-list.tsx      # 시스템 크리덴셜 목록
│   ├── credential-form-dialog.tsx      # 크리덴셜 생성/수정 다이얼로그
│   └── service-status-card.tsx         # 서비스 상태 표시 카드
├── hooks/
│   └── use-admin-config.ts             # 관리자 API hooks
└── lib/
    └── admin-api.ts                    # 관리자 API 클라이언트
```

### 수정 파일

| 파일 | 변경 내용 |
|------|----------|
| `lib/types.ts` | SystemConfig, AdminCredential 타입 추가 |
| `messages/en.json` | 관리자 설정 번역 추가 |
| `messages/ko.json` | 관리자 설정 번역 추가 |
| `components/sidebar.tsx` | 관리자 메뉴 추가 |
| `components/rag-config/embedding-config.tsx` | 프로젝트 크리덴셜 선택 추가 |

---

## application.yml 최종 상태

```yaml
spring:
  # ============================================================
  # 부트스트랩 설정 (DB에서 관리하지 않음)
  # ============================================================
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5434}/${DB_NAME:docst}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}

  jpa:
    hibernate:
      ddl-auto: validate

  # ============================================================
  # Spring AI - 부트스트랩만 (실제 API 키는 DB에서 로드)
  # ============================================================
  ai:
    # OpenAI - API 키는 dm_credential에서 동적 로드
    openai:
      api-key: "placeholder-loaded-from-db"  # 실제로는 사용되지 않음
      embedding:
        enabled: false  # DynamicEmbeddingClientFactory가 처리

    # Ollama - URL은 dm_system_config에서 동적 로드
    ollama:
      base-url: "http://placeholder"  # 실제로는 사용되지 않음
      embedding:
        enabled: false
      init:
        pull-model-strategy: never

    # VectorStore - 차원은 프로젝트 설정에서
    vectorstore:
      pgvector:
        index-type: HNSW
        distance-type: COSINE_DISTANCE
        dimensions: 1536  # 기본값, 실제로는 프로젝트 설정 사용

  # ============================================================
  # Neo4j - dm_system_config + dm_credential에서 동적 로드
  # ============================================================
  # neo4j 설정 완전 제거 (DynamicNeo4jConfig가 처리)

# ============================================================
# Docst 설정
# ============================================================
docst:
  jwt:
    secret: ${JWT_SECRET:your-secret-key}
    expiration: 86400

  encryption:
    key: ${DOCST_ENCRYPTION_KEY:}

  # RAG 설정 - 전역 기본값만 (실제 설정은 DB)
  rag:
    # RagGlobalProperties에서 읽는 기본값
    # dm_system_config가 비어있을 때만 사용
```

---

## 구현 순서

### Step 1: 데이터베이스
- [ ] V11 마이그레이션 작성
- [ ] dm_system_config 테이블
- [ ] dm_credential scope/project_id 확장

### Step 2: 도메인 계층
- [ ] CredentialScope 열거형
- [ ] SystemConfig 엔티티
- [ ] Credential 확장 (scope, projectId, 새 타입)
- [ ] RagConfigDto v1.1 (credentialId)

### Step 3: 리포지토리 계층
- [ ] SystemConfigRepository
- [ ] CredentialRepository 스코프 쿼리

### Step 4: 서비스 계층
- [ ] SystemConfigService (캐싱)
- [ ] DynamicCredentialResolver
- [ ] CredentialService 확장

### Step 5: 동적 설정 통합
- [ ] DynamicEmbeddingClientFactory
- [ ] DynamicNeo4jConfig
- [ ] DocstEmbeddingService 수정
- [ ] RagConfigService 수정

### Step 6: API 계층
- [ ] AdminConfigController
- [ ] AdminCredentialController
- [ ] ProjectCredentialController
- [ ] ApiModels DTO 추가

### Step 7: application.yml 정리
- [ ] 외부 서비스 설정 제거
- [ ] 부트스트랩 설정만 유지

### Step 8: 프론트엔드 타입/API
- [ ] lib/types.ts
- [ ] lib/admin-api.ts
- [ ] hooks/use-admin-config.ts

### Step 9: 프론트엔드 컴포넌트
- [ ] system-config-form.tsx
- [ ] system-credential-list.tsx
- [ ] credential-form-dialog.tsx
- [ ] service-status-card.tsx

### Step 10: 프론트엔드 페이지
- [ ] admin/settings/page.tsx
- [ ] admin/settings/credentials/page.tsx
- [ ] 사이드바 관리자 메뉴

### Step 11: 다국어 및 마무리
- [ ] messages/en.json
- [ ] messages/ko.json
- [ ] 테스트 및 검증

---

## 보안 고려사항

1. **접근 제어**
   - 시스템 설정/크리덴셜: ADMIN 권한만
   - 프로젝트 크리덴셜: PROJECT_ADMIN 권한

2. **암호화**
   - 모든 시크릿은 AES-256-GCM으로 암호화
   - DOCST_ENCRYPTION_KEY는 반드시 환경변수로 설정

3. **API 응답**
   - 복호화된 시크릿은 절대 응답에 포함하지 않음
   - 마스킹 처리: `sk-proj-****...****`

4. **감사 로깅**
   - 크리덴셜 CRUD 시 로그 기록
   - 시스템 설정 변경 시 로그 기록

---

## 완료 기준

- [ ] 시스템 설정을 DB에서 관리 (Neo4j URI, Ollama URL 등)
- [ ] OpenAI API 키를 시스템 크리덴셜로 저장 및 사용
- [ ] Neo4j 인증정보를 시스템 크리덴셜로 저장 및 사용
- [ ] 프로젝트별 API 키 오버라이드 동작
- [ ] 설정 우선순위 동작 (프로젝트 > 시스템)
- [ ] 관리자 UI에서 시스템 설정 관리
- [ ] 관리자 UI에서 시스템 크리덴셜 CRUD
- [ ] 프로젝트 UI에서 프로젝트 크리덴셜 CRUD
- [ ] yml에서 외부 서비스 설정 완전 제거
- [ ] 모든 시크릿 암호화 저장