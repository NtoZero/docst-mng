# Phase 6 보완: 동적 데이터소스 관리

> **작성일**: 2026-01-03
> **전제 조건**: Phase 6 기본 구현 완료 (LLM 통합, Tool Calling)
> **목표**: 정적 yml 설정 제거, Admin UI 기반 동적 데이터소스 연결

---

## 1. 배경 및 문제점

### 1.1 현재 상태

Phase 6 기본 구현에서 발견된 문제점:

| 구성요소 | 현재 상태 | 문제점 |
|---------|---------|--------|
| PgVector | `application.yml`에서 정적 DataSource 설정 | 런타임에 연결 정보 변경 불가 |
| VectorStore | 빈 주입 시 고정된 `EmbeddingModel` 사용 | 프로젝트별 Credential 적용 불가 |
| JdbcTemplate | 앱 시작 시 고정 | DB 연결 정보 동적 변경 불가 |

### 1.2 목표 상태

```
Admin Settings UI ─────────────────────────────────────────────────┐
    │                                                               │
    │ PgVector Tab                          Neo4j Tab               │
    │ - host, port, database                - uri                   │
    │ - schema, table, dimensions           - max-hop               │
    │ - PGVECTOR_AUTH credential            - NEO4J_AUTH credential │
    └───────────────┬───────────────────────────────┬───────────────┘
                    │                               │
                    ▼                               ▼
              ┌─────────────┐               ┌─────────────┐
              │ SystemConfig │               │ Credential  │
              │  (dm_system_ │               │  (dm_       │
              │   config)    │               │  credential)│
              └──────┬──────┘               └──────┬──────┘
                     │                             │
                     ▼                             ▼
              ┌─────────────────────────────────────────┐
              │      Dynamic Connection Manager         │
              │  - PgVectorDataSourceManager            │
              │  - Neo4jDriverManager (기존)             │
              └─────────────────────────────────────────┘
```

---

## 2. 아키텍처 설계

### 2.1 전체 흐름

```
┌──────────────────────────────────────────────────────────────────────────┐
│                           Admin Settings UI                               │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐     │
│  │Configuration│  │ Credentials │  │   Neo4j     │  │  PgVector   │     │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘     │
└──────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                        Backend REST API                                   │
│  POST /api/admin/config/{key}     POST /api/admin/credentials            │
└──────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                         Storage Layer                                     │
│  ┌─────────────────────────┐  ┌─────────────────────────────────────┐   │
│  │     dm_system_config    │  │          dm_credential              │   │
│  │  - pgvector.host        │  │  - PGVECTOR_AUTH (username/pwd)     │   │
│  │  - pgvector.port        │  │  - NEO4J_AUTH (username/pwd)        │   │
│  │  - pgvector.database    │  │  - OPENAI_API_KEY                   │   │
│  │  - pgvector.enabled     │  │  - ANTHROPIC_API_KEY                │   │
│  │  - neo4j.uri            │  │                                     │   │
│  │  - neo4j.enabled        │  │  * AES-256 암호화 저장               │   │
│  └─────────────────────────┘  └─────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                    Dynamic Connection Managers                            │
│  ┌─────────────────────────┐  ┌─────────────────────────────────────┐   │
│  │ PgVectorDataSourceManager│  │      Neo4jDriverManager            │   │
│  │  - getOrCreateJdbcTmpl() │  │  - getOrCreateDriver()             │   │
│  │  - refreshConnection()   │  │  - refreshDriver()                 │   │
│  │  - testConnection()      │  │  - 기존 구현 완료                    │   │
│  └────────────┬────────────┘  └─────────────────────────────────────┘   │
│               │                                                          │
│               ▼                                                          │
│  ┌─────────────────────────┐                                            │
│  │  SemanticSearchService  │                                            │
│  │  - 동적 JdbcTemplate     │                                            │
│  │  - 동적 EmbeddingModel   │                                            │
│  │  - VectorStore 캐싱      │                                            │
│  └─────────────────────────┘                                            │
└──────────────────────────────────────────────────────────────────────────┘
```

### 2.2 컴포넌트 관계도

```
                    ┌─────────────────────┐
                    │  SystemConfigService │
                    │  - getString()       │
                    │  - getInt()          │
                    │  - getBoolean()      │
                    └──────────┬──────────┘
                               │
              ┌────────────────┼────────────────┐
              │                │                │
              ▼                ▼                ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│PgVectorDataSource│ │Neo4jDriverManager│ │DynamicCredential│
│    Manager       │ │    (기존)        │ │   Resolver      │
└────────┬────────┘ └─────────────────┘ └────────┬────────┘
         │                                        │
         │         ┌─────────────────────────────┘
         │         │
         ▼         ▼
┌─────────────────────────────────────┐
│      SemanticSearchService          │
│  - PgVectorDataSourceManager        │
│  - DynamicEmbeddingClientFactory    │
│  - RagConfigService                 │
│  - getOrCreateVectorStore()         │
└─────────────────────────────────────┘
```

---

## 3. 상세 설계

### 3.1 SystemConfig 키 정의

#### PgVector 설정 키

| 키 | 타입 | 기본값 | 설명 |
|---|-----|-------|-----|
| `pgvector.enabled` | BOOLEAN | `false` | PgVector 활성화 여부 |
| `pgvector.host` | STRING | `localhost` | PostgreSQL 호스트 |
| `pgvector.port` | INTEGER | `5432` | PostgreSQL 포트 |
| `pgvector.database` | STRING | `docst_vector` | 데이터베이스명 |
| `pgvector.schema` | STRING | `public` | 스키마명 |
| `pgvector.table` | STRING | `vector_store` | 벡터 저장 테이블명 |
| `pgvector.dimensions` | INTEGER | `1536` | 벡터 차원 (OpenAI: 1536) |
| `pgvector.distance-type` | STRING | `COSINE_DISTANCE` | 거리 측정 방식 |
| `pgvector.index-type` | STRING | `HNSW` | 인덱스 타입 |

#### 기존 Neo4j 설정 키 (참조)

| 키 | 타입 | 설명 |
|---|-----|-----|
| `neo4j.enabled` | BOOLEAN | Neo4j 활성화 여부 |
| `neo4j.uri` | STRING | Neo4j bolt URI |
| `neo4j.max-hop` | INTEGER | 그래프 탐색 최대 깊이 |

### 3.2 Credential 타입 추가

```java
public enum CredentialType {
    // 기존
    OPENAI_API_KEY,
    ANTHROPIC_API_KEY,
    GITHUB_PAT,
    NEO4J_AUTH,
    CUSTOM_API_KEY,

    // 신규 추가
    PGVECTOR_AUTH  // PostgreSQL 인증정보
}
```

#### PGVECTOR_AUTH 형식

```json
{
  "username": "postgres",
  "password": "your-secure-password"
}
```

### 3.3 PgVectorDataSourceManager

```java
/**
 * PgVector 동적 DataSource 관리.
 * Neo4jDriverManager 패턴과 동일한 구조로 구현.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PgVectorDataSourceManager {

    private final SystemConfigService systemConfigService;
    private final DynamicCredentialResolver credentialResolver;
    private final CredentialRepository credentialRepository;
    private final ObjectMapper objectMapper;

    // 캐싱된 DataSource 및 JdbcTemplate
    private volatile HikariDataSource cachedDataSource;
    private volatile JdbcTemplate cachedJdbcTemplate;
    private volatile String cachedCredentialId;
    private volatile String cachedConnectionString;

    // ============================================================
    // Public API
    // ============================================================

    /**
     * JdbcTemplate 가져오기 (캐싱).
     * 설정 또는 크리덴셜 변경 시 자동 재생성.
     *
     * @return JdbcTemplate (설정 없으면 null)
     */
    public JdbcTemplate getOrCreateJdbcTemplate() {
        if (!isEnabled()) {
            closeDataSourceIfExists();
            return null;
        }

        String connectionString = buildConnectionString();
        String currentCredentialId = getCurrentCredentialId();

        if (connectionString == null || currentCredentialId == null) {
            closeDataSourceIfExists();
            return null;
        }

        // 캐시 유효성 검사
        if (cachedJdbcTemplate != null
            && Objects.equals(cachedConnectionString, connectionString)
            && Objects.equals(cachedCredentialId, currentCredentialId)) {
            return cachedJdbcTemplate;
        }

        // 설정 변경 시 재생성
        synchronized (this) {
            if (cachedJdbcTemplate == null
                || !Objects.equals(cachedConnectionString, connectionString)
                || !Objects.equals(cachedCredentialId, currentCredentialId)) {

                closeDataSourceIfExists();

                try {
                    cachedDataSource = createDataSource(connectionString, currentCredentialId);
                    cachedJdbcTemplate = new JdbcTemplate(cachedDataSource);
                    cachedConnectionString = connectionString;
                    cachedCredentialId = currentCredentialId;

                    log.info("PgVector DataSource created: {}", connectionString);
                } catch (Exception e) {
                    log.error("Failed to create PgVector DataSource", e);
                    return null;
                }
            }
        }

        return cachedJdbcTemplate;
    }

    /**
     * 연결 테스트.
     *
     * @return 연결 성공 여부
     */
    public ConnectionTestResult testConnection() {
        try {
            JdbcTemplate jdbcTemplate = getOrCreateJdbcTemplate();
            if (jdbcTemplate == null) {
                return new ConnectionTestResult(false, "PgVector is not configured");
            }

            // 간단한 쿼리로 연결 테스트
            String version = jdbcTemplate.queryForObject(
                "SELECT version()", String.class);

            // pgvector 확장 확인
            Boolean hasVector = jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM pg_extension WHERE extname = 'vector')",
                Boolean.class);

            if (Boolean.TRUE.equals(hasVector)) {
                return new ConnectionTestResult(true, "Connected: " + version);
            } else {
                return new ConnectionTestResult(false, "pgvector extension not installed");
            }
        } catch (Exception e) {
            return new ConnectionTestResult(false, "Connection failed: " + e.getMessage());
        }
    }

    /**
     * 캐시 강제 새로고침.
     */
    public void refreshConnection() {
        synchronized (this) {
            closeDataSourceIfExists();
        }
        getOrCreateJdbcTemplate();
    }

    // ============================================================
    // Internal Methods
    // ============================================================

    private boolean isEnabled() {
        return systemConfigService.getBoolean("pgvector.enabled", false);
    }

    private String buildConnectionString() {
        String host = systemConfigService.getString("pgvector.host", "localhost");
        int port = systemConfigService.getInt("pgvector.port", 5432);
        String database = systemConfigService.getString("pgvector.database", "docst_vector");

        if (host == null || host.isBlank() || database == null || database.isBlank()) {
            return null;
        }

        return String.format("jdbc:postgresql://%s:%d/%s", host, port, database);
    }

    private String getCurrentCredentialId() {
        return credentialRepository
            .findByScopeAndTypeAndActiveTrue(CredentialScope.SYSTEM, CredentialType.PGVECTOR_AUTH)
            .map(c -> c.getId().toString())
            .orElse(null);
    }

    private HikariDataSource createDataSource(String connectionString, String credentialId) {
        PgVectorAuth auth = getCredentialAuth();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(connectionString);
        config.setUsername(auth.username());
        config.setPassword(auth.password());
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setPoolName("pgvector-pool");

        return new HikariDataSource(config);
    }

    private PgVectorAuth getCredentialAuth() {
        String authJson = credentialResolver
            .resolveSystemApiKey(CredentialType.PGVECTOR_AUTH)
            .orElseThrow(() -> new IllegalStateException("PGVECTOR_AUTH credential not found"));

        try {
            return objectMapper.readValue(authJson, PgVectorAuth.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse PGVECTOR_AUTH", e);
        }
    }

    private void closeDataSourceIfExists() {
        if (cachedDataSource != null) {
            try {
                cachedDataSource.close();
            } catch (Exception e) {
                log.warn("Failed to close PgVector DataSource", e);
            }
            cachedDataSource = null;
            cachedJdbcTemplate = null;
            cachedConnectionString = null;
            cachedCredentialId = null;
        }
    }

    // ============================================================
    // DTOs
    // ============================================================

    public record ConnectionTestResult(boolean success, String message) {}
    private record PgVectorAuth(String username, String password) {}
}
```

### 3.4 SemanticSearchService 수정

```java
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class SemanticSearchService {

    // 변경: 정적 JdbcTemplate → 동적 Manager
    private final PgVectorDataSourceManager dataSourceManager;
    private final DynamicEmbeddingClientFactory embeddingClientFactory;
    private final RagConfigService ragConfigService;
    private final DocChunkRepository docChunkRepository;

    // 프로젝트별 VectorStore 캐시
    private final ConcurrentHashMap<UUID, VectorStore> vectorStoreCache = new ConcurrentHashMap<>();

    /**
     * 프로젝트별 VectorStore 생성.
     * 동적 JdbcTemplate + 동적 EmbeddingModel 조합.
     */
    private VectorStore createVectorStore(UUID projectId) {
        // 1. 동적 JdbcTemplate 획득
        JdbcTemplate jdbcTemplate = dataSourceManager.getOrCreateJdbcTemplate();
        if (jdbcTemplate == null) {
            throw new IllegalStateException("PgVector is not configured or disabled");
        }

        // 2. 프로젝트별 RAG 설정 조회
        ResolvedRagConfig config = ragConfigService.resolve(projectId, null);

        // 3. 프로젝트별 EmbeddingModel 생성 (Credential 기반)
        EmbeddingModel embeddingModel = embeddingClientFactory.createEmbeddingModel(projectId, config);

        // 4. Spring AI 1.1.0+ 표준 VectorStore 생성
        String schema = systemConfigService.getString("pgvector.schema", "public");
        String table = systemConfigService.getString("pgvector.table", "vector_store");

        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
            .dimensions(config.getEmbeddingDimensions())
            .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
            .indexType(PgVectorStore.PgIndexType.HNSW)
            .schemaName(schema)
            .vectorTableName(table)
            .initializeSchema(false)
            .build();
    }

    /**
     * PgVector 연결 변경 시 캐시 무효화.
     * PgVectorDataSourceManager.refreshConnection() 호출 후 실행.
     */
    public void invalidateAllVectorStores() {
        vectorStoreCache.clear();
        log.info("All VectorStore caches invalidated due to connection change");
    }
}
```

### 3.5 VectorStoreConfig 수정 (불필요 제거)

```java
/**
 * VectorStore 설정.
 *
 * Phase 6 보완:
 * - 정적 VectorStore 빈 제거
 * - 동적 생성으로 전환 (SemanticSearchService에서 처리)
 * - Placeholder EmbeddingModel 제거
 */
@Configuration
public class VectorStoreConfig {

    // 제거: placeholderEmbeddingModel()
    // 제거: vectorStore()

    // 스키마 초기화만 담당 (선택적)
    // 또는 Flyway 마이그레이션으로 이동
}
```

---

## 4. Frontend 설계

### 4.1 Admin Settings 탭 구조

```
Admin Settings
├── Configuration (기존)
├── Credentials (기존)
├── Neo4j (기존)
├── PgVector (신규)     ← 추가
└── Health (기존)
```

### 4.2 PgVector Config 컴포넌트

```tsx
// components/admin/pgvector-config.tsx

export function PgVectorConfig() {
  return (
    <div className="space-y-6">
      {/* Connection Status Card */}
      <Card>
        <CardHeader>
          <CardTitle>PgVector Connection Status</CardTitle>
        </CardHeader>
        <CardContent>
          {/* Health Status Badge */}
          {/* Active Credential Info */}
          {/* Test Connection Button */}
        </CardContent>
      </Card>

      {/* Configuration Card */}
      <Card>
        <CardHeader>
          <CardTitle>PgVector Configuration</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          {/* Enable Toggle: pgvector.enabled */}
          {/* Host Input: pgvector.host */}
          {/* Port Input: pgvector.port */}
          {/* Database Input: pgvector.database */}
          {/* Schema Input: pgvector.schema */}
          {/* Table Input: pgvector.table */}
          {/* Dimensions Input: pgvector.dimensions */}
        </CardContent>
      </Card>

      {/* Credential Info Card */}
      <Card>
        <CardHeader>
          <CardTitle>Database Credential</CardTitle>
        </CardHeader>
        <CardContent>
          {/* PGVECTOR_AUTH Credentials List */}
          {/* Link to Credentials Tab */}
        </CardContent>
      </Card>
    </div>
  );
}
```

### 4.3 API Endpoints

#### 연결 테스트 API

```
POST /api/admin/pgvector/test-connection
Response: { "success": true, "message": "Connected: PostgreSQL 16.1" }
```

#### 캐시 새로고침 API

```
POST /api/admin/pgvector/refresh
Response: { "success": true }
```

---

## 5. 구현 순서

### 5.1 Backend (우선순위 순)

| # | 작업 | 파일 | 예상 시간 |
|---|-----|-----|---------|
| 1 | CredentialType에 PGVECTOR_AUTH 추가 | `Credential.java` | 5분 |
| 2 | SystemConfigService에 pgvector 키 상수 추가 | `SystemConfigService.java` | 10분 |
| 3 | PgVectorDataSourceManager 생성 | `PgVectorDataSourceManager.java` | 1시간 |
| 4 | SemanticSearchService 수정 | `SemanticSearchService.java` | 30분 |
| 5 | VectorStoreConfig 정리 | `VectorStoreConfig.java` | 15분 |
| 6 | AdminPgVectorController 생성 | `AdminPgVectorController.java` | 30분 |
| 7 | HealthCheck에 pgvector 추가 | `AdminHealthController.java` | 20분 |

### 5.2 Frontend (우선순위 순)

| # | 작업 | 파일 | 예상 시간 |
|---|-----|-----|---------|
| 1 | PgVectorConfig 컴포넌트 생성 | `pgvector-config.tsx` | 1시간 |
| 2 | Admin Settings 페이지에 탭 추가 | `page.tsx` | 15분 |
| 3 | API 클라이언트 추가 | `admin-api.ts` | 20분 |
| 4 | types 추가 | `types.ts` | 10분 |

### 5.3 Database Migration

```sql
-- V20__add_pgvector_config_keys.sql

INSERT INTO dm_system_config (id, config_key, config_value, config_type, description, created_at, updated_at)
VALUES
  (gen_random_uuid(), 'pgvector.enabled', 'false', 'BOOLEAN', 'Enable PgVector semantic search', NOW(), NOW()),
  (gen_random_uuid(), 'pgvector.host', 'localhost', 'STRING', 'PostgreSQL host for vector store', NOW(), NOW()),
  (gen_random_uuid(), 'pgvector.port', '5432', 'INTEGER', 'PostgreSQL port', NOW(), NOW()),
  (gen_random_uuid(), 'pgvector.database', 'docst_vector', 'STRING', 'Database name for vector store', NOW(), NOW()),
  (gen_random_uuid(), 'pgvector.schema', 'public', 'STRING', 'Schema name', NOW(), NOW()),
  (gen_random_uuid(), 'pgvector.table', 'vector_store', 'STRING', 'Vector store table name', NOW(), NOW()),
  (gen_random_uuid(), 'pgvector.dimensions', '1536', 'INTEGER', 'Embedding dimensions', NOW(), NOW())
ON CONFLICT (config_key) DO NOTHING;
```

---

## 6. 테스트 계획

### 6.1 단위 테스트

```java
@SpringBootTest
class PgVectorDataSourceManagerTest {

    @Test
    void shouldReturnNullWhenDisabled() {
        // pgvector.enabled = false
        assertThat(manager.getOrCreateJdbcTemplate()).isNull();
    }

    @Test
    void shouldCreateJdbcTemplateWhenConfigured() {
        // 모든 설정 완료
        JdbcTemplate template = manager.getOrCreateJdbcTemplate();
        assertThat(template).isNotNull();
    }

    @Test
    void shouldRecreateOnCredentialChange() {
        // 첫 번째 호출
        JdbcTemplate first = manager.getOrCreateJdbcTemplate();

        // Credential 변경
        changeCredential();

        // 두 번째 호출 - 새 인스턴스
        JdbcTemplate second = manager.getOrCreateJdbcTemplate();
        assertThat(second).isNotSameAs(first);
    }
}
```

### 6.2 통합 테스트

```java
@SpringBootTest
@Testcontainers
class SemanticSearchIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @Test
    void shouldSearchWithDynamicVectorStore() {
        // Given: Admin UI에서 pgvector 설정
        setupPgVectorConfig(postgres);

        // When: Semantic Search 실행
        List<SearchResult> results = searchService.searchSemantic(projectId, "test query", 10);

        // Then: 결과 반환
        assertThat(results).isNotEmpty();
    }
}
```

### 6.3 E2E 테스트

1. Admin Settings → PgVector 탭 이동
2. Host, Port, Database 입력
3. PGVECTOR_AUTH Credential 생성
4. "Test Connection" 버튼 클릭
5. 연결 성공 확인
6. AI Playground에서 Semantic Search 테스트

---

## 7. 마이그레이션 가이드

### 7.1 기존 환경에서 업그레이드

1. **Backend 업데이트**
   ```bash
   ./gradlew build
   ```

2. **Flyway 마이그레이션 실행**
   ```bash
   ./gradlew flywayMigrate
   ```

3. **Admin Settings에서 PgVector 설정**
   - http://localhost:3000/admin/settings → PgVector 탭
   - Host, Port, Database 입력
   - Credentials 탭에서 PGVECTOR_AUTH 생성
   - PgVector 탭에서 Enable 토글 ON

4. **연결 테스트**
   - "Test Connection" 버튼 클릭
   - Health 탭에서 상태 확인

### 7.2 application.yml 정리

기존 정적 설정 제거:

```yaml
# 제거할 설정들
spring:
  datasource:
    # 메인 DB만 유지, vector DB 설정 제거
  ai:
    vectorstore:
      pgvector:
        # 모든 설정 제거 (Admin UI에서 관리)
```

---

## 8. 참고 자료

### 8.1 관련 파일

- `Neo4jDriverManager.java` - 동일 패턴 참조
- `DynamicEmbeddingClientFactory.java` - Credential 기반 동적 생성
- `SystemConfigService.java` - 시스템 설정 관리

### 8.2 Spring AI 문서

- [PgVectorStore Configuration](https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html)
- [EmbeddingModel](https://docs.spring.io/spring-ai/reference/api/embeddings.html)

### 8.3 HikariCP 문서

- [HikariCP Configuration](https://github.com/brettwooldridge/HikariCP#configuration-knobs-baby)
