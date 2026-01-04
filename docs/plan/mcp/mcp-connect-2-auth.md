# Docst MCP API Key 인증 구현 계획

## 1. 개요

MCP 클라이언트(Claude Desktop, Claude Code)를 위한 API Key 인증 기능 구현.
JWT 토큰의 24시간 만료 문제를 해결하고, 한 번 설정으로 지속 사용 가능한 인증 방식 제공.

### 현재 문제점

| 인증 방식 | 만료 | 문제점 |
|----------|------|--------|
| JWT Token | 24시간 | MCP 클라이언트에서 토큰 갱신 어려움 |

### 해결 방안

| 인증 방식 | 만료 | 용도 |
|----------|------|------|
| **API Key** | 없음 (수동 폐기) | MCP 클라이언트 전용 |
| JWT Token | 24시간 | 웹 UI 전용 |

---

## 2. 설계 결정

### API Key 형식

```
docst_ak_<32 random URL-safe chars>
```

예시: `docst_ak_A1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6`

### 저장 방식

| 항목 | 저장값 | 이유 |
|------|--------|------|
| key_prefix | `docst_ak_A1b2C3d4...` | 사용자가 키 식별용 |
| key_hash | SHA-256 해시 | 보안 (원본 키는 저장 안 함) |

### 인증 헤더

```http
# 방식 1: X-API-Key 헤더 (권장)
X-API-Key: docst_ak_xxxxxxxx...

# 방식 2: Bearer 토큰 형식
Authorization: Bearer docst_ak_xxxxxxxx...

# 방식 3: Query Parameter (SSE용)
GET /mcp/stream?api_key=docst_ak_xxxxxxxx...
```

---

## 3. 데이터베이스 스키마

### 테이블: dm_api_key

```sql
CREATE TABLE dm_api_key (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES dm_user(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    key_prefix VARCHAR(20) NOT NULL,
    key_hash VARCHAR(64) NOT NULL,
    last_used_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT uq_api_key_user_name UNIQUE (user_id, name),
    CONSTRAINT uq_api_key_hash UNIQUE (key_hash)
);

CREATE INDEX idx_api_key_user_id ON dm_api_key(user_id);
CREATE INDEX idx_api_key_hash ON dm_api_key(key_hash);
CREATE INDEX idx_api_key_active ON dm_api_key(active) WHERE active = TRUE;
```

### 컬럼 설명

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | UUID | Primary Key |
| user_id | UUID | 소유자 (FK → dm_user) |
| name | VARCHAR(255) | 키 식별 이름 (예: "Claude Desktop") |
| key_prefix | VARCHAR(20) | 표시용 prefix (예: "docst_ak_A1b2C3d4...") |
| key_hash | VARCHAR(64) | SHA-256 해시 (64 hex chars) |
| last_used_at | TIMESTAMP | 마지막 사용 시간 |
| expires_at | TIMESTAMP | 만료 시간 (null = 무제한) |
| active | BOOLEAN | 활성화 상태 |
| created_at | TIMESTAMP | 생성 시간 |

---

## 4. REST API 설계

### 엔드포인트

| Method | Path | Description | Auth Required |
|--------|------|-------------|---------------|
| POST | `/api/auth/api-keys` | API Key 생성 | JWT |
| GET | `/api/auth/api-keys` | 목록 조회 | JWT |
| DELETE | `/api/auth/api-keys/{id}` | 키 폐기 | JWT |

### Request/Response

#### POST /api/auth/api-keys

**Request:**
```json
{
  "name": "Claude Desktop",
  "expiresInDays": 90
}
```

**Response (201 Created):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Claude Desktop",
  "key": "docst_ak_A1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6",
  "keyPrefix": "docst_ak_A1b2C3d4...",
  "expiresAt": "2025-04-04T12:00:00Z",
  "createdAt": "2025-01-04T12:00:00Z"
}
```

> **Warning**: `key` 필드는 생성 시 한 번만 반환됩니다.

#### GET /api/auth/api-keys

**Response:**
```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "Claude Desktop",
    "keyPrefix": "docst_ak_A1b2C3d4...",
    "lastUsedAt": "2025-01-04T15:30:00Z",
    "expiresAt": "2025-04-04T12:00:00Z",
    "active": true,
    "createdAt": "2025-01-04T12:00:00Z"
  }
]
```

#### DELETE /api/auth/api-keys/{id}

**Response:** `204 No Content`

---

## 5. 인증 필터 흐름

```
HTTP Request
    │
    ▼
┌─────────────────────────────────┐
│  ApiKeyAuthenticationFilter     │
│  - X-API-Key 헤더 확인          │
│  - Bearer docst_ak_xxx 확인     │
│  - Query param api_key 확인     │
└─────────────────────────────────┘
    │
    │ (API Key 없음 또는 무효)
    ▼
┌─────────────────────────────────┐
│  JwtAuthenticationFilter        │
│  - Authorization: Bearer JWT    │
└─────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────┐
│  SecurityConfig                 │
│  - permitAll 또는 authenticated │
└─────────────────────────────────┘
```

---

## 6. 구현 파일 목록

### Backend - 신규 파일 (5개)

| 파일 | 설명 |
|------|------|
| `backend/src/main/resources/db/migration/V14__add_api_key.sql` | DB 마이그레이션 |
| `backend/src/main/java/com/docst/domain/ApiKey.java` | Entity |
| `backend/src/main/java/com/docst/repository/ApiKeyRepository.java` | Repository |
| `backend/src/main/java/com/docst/service/ApiKeyService.java` | Service |
| `backend/src/main/java/com/docst/auth/ApiKeyAuthenticationFilter.java` | Auth Filter |

### Backend - 수정 파일 (2개)

| 파일 | 변경 내용 |
|------|----------|
| `backend/src/main/java/com/docst/config/SecurityConfig.java` | ApiKeyFilter 추가 |
| `backend/src/main/java/com/docst/api/AuthController.java` | API Key 엔드포인트 추가 |

### Frontend - 신규 파일 (3개)

| 파일 | 설명 |
|------|------|
| `frontend/components/settings/api-key-list.tsx` | 키 목록 컴포넌트 |
| `frontend/components/settings/api-key-form-dialog.tsx` | 생성 다이얼로그 |
| `frontend/app/[locale]/settings/api-keys/page.tsx` | 설정 페이지 |

### Frontend - 수정 파일 (3개)

| 파일 | 변경 내용 |
|------|----------|
| `frontend/lib/types.ts` | ApiKey 타입 추가 |
| `frontend/lib/api.ts` | apiKeysApi 추가 |
| `frontend/hooks/use-api.ts` | useApiKeys hooks 추가 |

---

## 7. 구현 순서

### Phase 1: Backend Core (Day 1)

1. **DB Migration**: `V14__add_api_key.sql`
2. **Entity**: `ApiKey.java`
3. **Repository**: `ApiKeyRepository.java`
4. **Service**: `ApiKeyService.java`

### Phase 2: Backend Auth (Day 1)

5. **Filter**: `ApiKeyAuthenticationFilter.java`
6. **SecurityConfig**: Filter chain 수정
7. **Controller**: AuthController에 엔드포인트 추가

### Phase 3: Frontend (Day 2)

8. **Types**: `types.ts` 수정
9. **API Client**: `api.ts` 수정
10. **Hooks**: `use-api.ts` 수정
11. **Components**: api-key-list.tsx, api-key-form-dialog.tsx
12. **Page**: settings/api-keys/page.tsx

### Phase 4: Testing & Documentation (Day 2)

13. **Test**: API Key 인증 테스트
14. **Documentation**: mcp-connect.md 최종 업데이트

---

## 8. 참조 패턴

### Entity 패턴

**참조**: `backend/src/main/java/com/docst/domain/Credential.java`

```java
@Entity
@Table(name = "dm_api_key")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApiKey {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // ... fields
}
```

### Auth Filter 패턴

**참조**: `backend/src/main/java/com/docst/auth/JwtAuthenticationFilter.java`

```java
@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(...) {
        String apiKey = extractApiKey(request);
        if (apiKey != null) {
            Optional<User> user = apiKeyService.authenticateByApiKey(apiKey);
            if (user.isPresent()) {
                // Set SecurityContext
            }
        }
        filterChain.doFilter(request, response);
    }
}
```

### Frontend Hook 패턴

**참조**: `frontend/hooks/use-api.ts`

```typescript
export function useApiKeys() {
  return useQuery({
    queryKey: ['api-keys'],
    queryFn: () => apiKeysApi.list(),
  });
}

export function useCreateApiKey() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateApiKeyRequest) => apiKeysApi.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['api-keys'] });
    },
  });
}
```

---

## 9. 보안 고려사항

### API Key 생성

- `SecureRandom` 사용 (예측 불가능)
- 32 chars = 256 bits 엔트로피
- URL-safe Base64 인코딩

### API Key 저장

- SHA-256 해시만 저장
- 원본 키는 생성 시 한 번만 반환
- DB 유출 시에도 키 복원 불가

### API Key 검증

- Timing attack 방지 (constant-time comparison)
- Rate limiting 권장 (향후 구현)
- 만료 및 활성화 상태 확인

### 감사 로그

- 키 생성/폐기 시 로그 기록
- 인증 시 `last_used_at` 업데이트

---

## 10. 사용 예시

### Claude Desktop 설정

```json
{
  "mcpServers": {
    "docst": {
      "url": "http://localhost:8342/mcp",
      "transport": "http",
      "headers": {
        "X-API-Key": "docst_ak_A1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6"
      }
    }
  }
}
```

### Claude Code 설정

```bash
claude mcp add docst --url http://localhost:8342/mcp --header "X-API-Key: docst_ak_xxx..."
```

### curl 테스트

```bash
# API Key 생성
curl -X POST http://localhost:8342/api/auth/api-keys \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"name": "Test Key", "expiresInDays": 30}'

# API Key로 MCP 호출
curl -X POST http://localhost:8342/mcp \
  -H "X-API-Key: docst_ak_xxx..." \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc": "2.0", "id": "1", "method": "tools/list", "params": {}}'
```
