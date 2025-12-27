# Credential 관리 시스템 구현

Private Git 레포지토리 접근을 위한 자격증명 관리 시스템입니다.

---

## 개요

GitHub Personal Access Token (PAT) 등의 인증 정보를 암호화하여 DB에 저장하고, Git 동기화 시 자동으로 인증에 사용합니다.

### 주요 기능

- AES-256-GCM 암호화로 토큰 안전 저장
- GitHub PAT, Basic Auth, SSH Key 타입 지원
- Repository별 Credential 연결
- JGit CredentialsProvider 자동 연동

---

## 아키텍처

```
┌─────────────────────────────────────────────────────────────┐
│                     Frontend UI                              │
│  /credentials - 자격증명 CRUD                                │
│  /projects/[id] - Repository에 Credential 연결               │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                     REST API                                 │
│  CredentialController: /api/credentials                      │
│  RepositoriesController: /api/repositories/{id}/credential   │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   Service Layer                              │
│  CredentialService: CRUD + 복호화                            │
│  EncryptionService: AES-256-GCM 암복호화                     │
│  GitService: CredentialsProvider 생성                        │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   Database                                   │
│  dm_credential: 암호화된 토큰 저장                           │
│  dm_repository: credential_id FK 참조                        │
└─────────────────────────────────────────────────────────────┘
```

---

## Backend 구현

### 1. EncryptionService

AES-256-GCM 암호화 서비스입니다.

**파일**: `backend/src/main/java/com/docst/service/EncryptionService.java`

```java
@Service
public class EncryptionService {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final SecretKey secretKey;

    public EncryptionService(@Value("${docst.encryption.key:}") String keyBase64) {
        // 키가 없으면 랜덤 생성 (개발용)
        this.secretKey = initKey(keyBase64);
    }

    public String encrypt(String plainText) { ... }
    public String decrypt(String encryptedText) { ... }
}
```

**암호화 형식**: `Base64(IV + CipherText + AuthTag)`

### 2. Credential Entity

자격증명 엔티티입니다.

**파일**: `backend/src/main/java/com/docst/domain/Credential.java`

```java
@Entity
@Table(name = "dm_credential")
public class Credential {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String name;

    @Enumerated(EnumType.STRING)
    private CredentialType type;

    private String username;

    @Column(name = "encrypted_secret")
    private String encryptedSecret;

    private String description;
    private boolean active = true;

    public enum CredentialType {
        GITHUB_PAT,    // GitHub Personal Access Token
        BASIC_AUTH,    // Username + Password
        SSH_KEY        // SSH Private Key (미구현)
    }
}
```

### 3. CredentialService

자격증명 비즈니스 로직입니다.

**파일**: `backend/src/main/java/com/docst/service/CredentialService.java`

```java
@Service
@Transactional(readOnly = true)
public class CredentialService {
    private final CredentialRepository credentialRepository;
    private final EncryptionService encryptionService;
    private final UserService userService;

    // 생성 시 암호화
    @Transactional
    public Credential create(UUID userId, String name, CredentialType type,
                            String username, String secret, String description) {
        String encryptedSecret = encryptionService.encrypt(secret);
        // ...
    }

    // 복호화하여 반환
    public String getDecryptedSecret(UUID credentialId) {
        Credential credential = credentialRepository.findById(credentialId)
            .orElseThrow();
        return encryptionService.decrypt(credential.getEncryptedSecret());
    }
}
```

### 4. GitService 연동

JGit에서 Credential 사용합니다.

**파일**: `backend/src/main/java/com/docst/git/GitService.java`

```java
@Service
public class GitService {
    private final CredentialService credentialService;

    private CredentialsProvider getCredentialsProvider(Repository repo) {
        Credential credential = repo.getCredential();
        if (credential == null || !credential.isActive()) {
            return null;
        }

        String secret = credentialService.getDecryptedSecret(credential.getId());

        switch (credential.getType()) {
            case GITHUB_PAT:
                // PAT는 username 대신 토큰 사용
                return new UsernamePasswordCredentialsProvider(secret, "");
            case BASIC_AUTH:
                return new UsernamePasswordCredentialsProvider(
                    credential.getUsername(), secret);
            default:
                return null;
        }
    }

    public void cloneOrUpdate(Repository repo, String targetBranch) {
        CredentialsProvider cp = getCredentialsProvider(repo);
        // clone/fetch 시 setCredentialsProvider(cp) 사용
    }
}
```

### 5. REST API

**파일**: `backend/src/main/java/com/docst/api/CredentialController.java`

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/credentials` | 현재 사용자의 모든 자격증명 조회 |
| GET | `/api/credentials/{id}` | 자격증명 상세 조회 |
| POST | `/api/credentials` | 새 자격증명 생성 |
| PUT | `/api/credentials/{id}` | 자격증명 수정 |
| DELETE | `/api/credentials/{id}` | 자격증명 삭제 |

**파일**: `backend/src/main/java/com/docst/api/RepositoriesController.java`

| Method | Path | Description |
|--------|------|-------------|
| PUT | `/api/repositories/{id}/credential` | 레포지토리에 자격증명 연결/해제 |

### 6. Database Migration

**파일**: `backend/src/main/resources/db/migration/V3__add_credential.sql`

```sql
CREATE TABLE dm_credential (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES dm_user(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    username VARCHAR(255),
    encrypted_secret TEXT NOT NULL,
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_credential_user_name UNIQUE (user_id, name)
);

ALTER TABLE dm_repository
    ADD COLUMN credential_id UUID REFERENCES dm_credential(id) ON DELETE SET NULL;
```

---

## Frontend 구현

### 1. Types

**파일**: `frontend/lib/types.ts`

```typescript
export type CredentialType = 'GITHUB_PAT' | 'BASIC_AUTH' | 'SSH_KEY';

export interface Credential {
  id: string;
  name: string;
  type: CredentialType;
  username: string | null;
  description: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string | null;
}

export interface CreateCredentialRequest {
  name: string;
  type: CredentialType;
  username?: string;
  secret: string;
  description?: string;
}
```

### 2. API Client

**파일**: `frontend/lib/api.ts`

```typescript
export const credentialsApi = {
  list: (): Promise<Credential[]> => request('/api/credentials'),
  get: (id: string): Promise<Credential> => request(`/api/credentials/${id}`),
  create: (data: CreateCredentialRequest): Promise<Credential> => ...,
  update: (id: string, data: UpdateCredentialRequest): Promise<Credential> => ...,
  delete: (id: string): Promise<void> => ...,
};

export const repositoriesApi = {
  // ...기존 메서드
  setCredential: (id: string, data: SetCredentialRequest): Promise<Repository> => ...,
};
```

### 3. React Hooks

**파일**: `frontend/hooks/use-api.ts`

```typescript
export function useCredentials() { ... }
export function useCredential(id: string) { ... }
export function useCreateCredential() { ... }
export function useUpdateCredential() { ... }
export function useDeleteCredential() { ... }
export function useSetRepositoryCredential() { ... }
```

### 4. Credentials 관리 페이지

**파일**: `frontend/app/credentials/page.tsx`

- 자격증명 목록 표시 (카드 형태)
- 새 자격증명 생성 폼
- 수정/삭제 기능
- 토큰 입력 시 보기/숨기기 토글

### 5. Repository 카드 개선

**파일**: `frontend/app/projects/[projectId]/page.tsx`

- 설정 버튼 추가 (⚙️)
- Credential 선택 드롭다운
- 현재 연결된 Credential 배지 표시

---

## 사용 방법

### 1. Credential 생성

```bash
# API 직접 호출
curl -X POST http://localhost:8080/api/credentials \
  -H "Authorization: Bearer dev-token-{userId}" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "my-github-token",
    "type": "GITHUB_PAT",
    "secret": "ghp_xxxxxxxxxxxx",
    "description": "Private repo access"
  }'
```

### 2. Repository에 Credential 연결

```bash
curl -X PUT http://localhost:8080/api/repositories/{repoId}/credential \
  -H "Authorization: Bearer dev-token-{userId}" \
  -H "Content-Type: application/json" \
  -d '{"credentialId": "{credentialId}"}'
```

### 3. UI 사용

1. 사이드바에서 **Credentials** 클릭
2. **Add Credential** 버튼으로 GitHub PAT 등록
3. 프로젝트 페이지에서 Repository 카드의 **⚙️** 클릭
4. 드롭다운에서 Credential 선택
5. **Sync** 버튼으로 private repository 동기화

---

## 보안 고려사항

### 암호화

- AES-256-GCM 사용 (인증 암호화)
- 각 암호화마다 랜덤 IV 생성
- 암호화 키는 환경변수로 설정 권장

```yaml
# application.yml
docst:
  encryption:
    key: ${DOCST_ENCRYPTION_KEY}  # Base64 encoded 32-byte key
```

### 키 생성 예시

```bash
# 32바이트 랜덤 키 생성
openssl rand -base64 32
```

### 주의사항

- 암호화 키 분실 시 저장된 토큰 복구 불가
- 프로덕션에서는 반드시 환경변수로 키 설정
- 토큰은 응답에서 제외 (encryptedSecret 필드 미노출)

---

## 파일 목록

### Backend

| 파일 | 설명 |
|------|------|
| `service/EncryptionService.java` | AES-256-GCM 암호화 서비스 |
| `domain/Credential.java` | 자격증명 엔티티 |
| `repository/CredentialRepository.java` | JPA Repository |
| `service/CredentialService.java` | 자격증명 비즈니스 로직 |
| `api/CredentialController.java` | REST API 컨트롤러 |
| `api/ApiModels.java` | DTO 추가 (Credential 관련) |
| `git/GitService.java` | CredentialsProvider 연동 |
| `domain/Repository.java` | credential 연관관계 추가 |
| `db/migration/V3__add_credential.sql` | DB 마이그레이션 |

### Frontend

| 파일 | 설명 |
|------|------|
| `lib/types.ts` | Credential 타입 정의 |
| `lib/api.ts` | API 클라이언트 함수 |
| `hooks/use-api.ts` | TanStack Query hooks |
| `app/credentials/page.tsx` | Credentials 관리 페이지 |
| `components/sidebar.tsx` | Credentials 메뉴 추가 |
| `app/projects/[projectId]/page.tsx` | Repository Credential 연결 UI |
