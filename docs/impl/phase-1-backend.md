# Phase 1 Backend 구현 완료

> 작성일: 2025-12-26

---

## 개요

Phase 1 MVP 백엔드 구현이 완료되었습니다. InMemoryStore 기반 스텁에서 PostgreSQL + JPA 기반 실제 구현으로 전환되었습니다.

---

## 변경된 파일 목록

### 신규 생성

```
backend/src/main/java/com/docst/
├── domain/                          # JPA 엔티티
│   ├── User.java
│   ├── Project.java
│   ├── ProjectRole.java
│   ├── ProjectMember.java
│   ├── Repository.java
│   ├── Document.java
│   ├── DocumentVersion.java
│   └── SyncJob.java
├── repository/                      # Spring Data JPA
│   ├── UserRepository.java
│   ├── ProjectRepository.java
│   ├── ProjectMemberRepository.java
│   ├── RepositoryRepository.java
│   ├── DocumentRepository.java
│   ├── DocumentVersionRepository.java
│   └── SyncJobRepository.java
├── service/                         # 비즈니스 로직
│   ├── UserService.java
│   ├── ProjectService.java
│   ├── RepositoryService.java
│   ├── DocumentService.java
│   ├── SearchService.java
│   ├── SyncService.java
│   └── GitSyncService.java
├── git/                             # JGit 연동
│   ├── GitService.java
│   ├── GitFileScanner.java
│   └── DocumentParser.java
└── mcp/                             # MCP Tools
    ├── McpModels.java
    └── McpController.java

backend/src/main/resources/
└── db/migration/                    # Flyway
    ├── V1__init_schema.sql
    └── V2__add_indexes.sql
```

### 수정된 파일

| 파일 | 변경 내용 |
|------|----------|
| `build.gradle.kts` | JPA, PostgreSQL, Flyway, JGit, Flexmark 의존성 추가 |
| `application.yml` | 데이터소스, Flyway, docst 설정 추가 |
| `AuthController.java` | UserService 주입으로 변경 |
| `ProjectsController.java` | ProjectService 사용으로 리팩토링 |
| `RepositoriesController.java` | RepositoryService 사용으로 리팩토링 |
| `DocumentsController.java` | DocumentService 사용으로 리팩토링 |
| `SearchController.java` | SearchService 사용으로 리팩토링 |
| `SyncController.java` | SyncService 사용으로 리팩토링 |
| `docker-compose.yml` | healthcheck 추가, 환경변수 정리 |

### 삭제된 파일

```
backend/src/main/java/com/docst/store/   # 전체 삭제
├── Project.java          (→ domain/Project.java로 대체)
├── Repository.java       (→ domain/Repository.java로 대체)
├── Document.java         (→ domain/Document.java로 대체)
├── DocumentVersion.java  (→ domain/DocumentVersion.java로 대체)
├── SyncJob.java          (→ domain/SyncJob.java로 대체)
└── InMemoryStore.java    (→ 서비스 레이어로 대체)
```

---

## 상세 구현 내용

### 1. JPA 엔티티

#### User
```java
@Entity
@Table(name = "dm_user")
public class User {
    UUID id;
    AuthProvider provider;  // GITHUB, LOCAL
    String providerUserId;
    String email;
    String displayName;
    Instant createdAt;
}
```

#### Project
```java
@Entity
@Table(name = "dm_project")
public class Project {
    UUID id;
    String name;
    String description;
    boolean active;
    Instant createdAt;
    List<ProjectMember> members;
    List<Repository> repositories;
}
```

#### Repository
```java
@Entity
@Table(name = "dm_repository")
public class Repository {
    UUID id;
    Project project;
    RepoProvider provider;  // GITHUB, LOCAL
    String externalId;
    String owner;
    String name;
    String cloneUrl;
    String defaultBranch;
    String localMirrorPath;
    boolean active;
    Instant createdAt;
}
```

#### Document & DocumentVersion
```java
@Entity
@Table(name = "dm_document")
public class Document {
    UUID id;
    Repository repository;
    String path;
    String title;
    DocType docType;  // MD, ADOC, OPENAPI, ADR, OTHER
    String latestCommitSha;
    boolean deleted;
    Instant createdAt;
    List<DocumentVersion> versions;
}

@Entity
@Table(name = "dm_document_version")
public class DocumentVersion {
    UUID id;
    Document document;
    String commitSha;
    String authorName;
    String authorEmail;
    Instant committedAt;
    String message;
    String contentHash;
    String content;
}
```

#### SyncJob
```java
@Entity
@Table(name = "dm_sync_job")
public class SyncJob {
    UUID id;
    Repository repository;
    SyncStatus status;  // PENDING, RUNNING, SUCCEEDED, FAILED
    String targetBranch;
    String lastSyncedCommit;
    String errorMessage;
    Instant startedAt;
    Instant finishedAt;
}
```

---

### 2. Flyway 마이그레이션

#### V1__init_schema.sql
- `dm_user` - 사용자 테이블
- `dm_project` - 프로젝트 테이블
- `dm_project_member` - 프로젝트 멤버 테이블
- `dm_repository` - 레포지토리 테이블
- `dm_document` - 문서 메타데이터 테이블
- `dm_document_version` - 문서 버전 테이블
- `dm_sync_job` - 동기화 작업 테이블

#### V2__add_indexes.sql
- 외래키 인덱스
- 경로/타입 인덱스
- 시간순 정렬 인덱스

---

### 3. JGit 연동

#### GitService
- `cloneOrOpen()` - 레포 clone 또는 기존 열기
- `fetch()` - 원격에서 fetch
- `checkout()` - 브랜치 체크아웃
- `getLatestCommitSha()` - 최신 커밋 SHA 조회
- `getFileContent()` - 특정 커밋의 파일 내용 조회
- `getCommitInfo()` - 커밋 정보 조회

#### GitFileScanner
문서 파일 탐지 패턴:
- `README.md`, `readme.md`
- `docs/**/*.md`, `docs/**/*.adoc`
- `architecture/**/*.md`
- `adr/**/*.md`, `adrs/**/*.md`
- `*.openapi.yaml`, `*.openapi.json`
- `CHANGELOG.md`, `CONTRIBUTING.md`

#### DocumentParser
Flexmark 기반 Markdown 파싱:
- 제목 추출 (H1)
- 헤딩 목록 추출
- 섹션 분리

---

### 4. MCP Tools

| Tool | 엔드포인트 | 설명 |
|------|-----------|------|
| `list_documents` | `POST /mcp/tools/list_documents` | 문서 목록 조회 |
| `get_document` | `POST /mcp/tools/get_document` | 문서 내용 조회 |
| `list_document_versions` | `POST /mcp/tools/list_document_versions` | 버전 목록 |
| `diff_document` | `POST /mcp/tools/diff_document` | 두 버전 비교 |
| `search_documents` | `POST /mcp/tools/search_documents` | 키워드 검색 |
| `sync_repository` | `POST /mcp/tools/sync_repository` | 동기화 실행 |

---

### 5. 서비스 레이어

| 서비스 | 주요 메서드 |
|--------|-----------|
| `UserService` | createOrUpdateLocalUser, createOrUpdateGitHubUser |
| `ProjectService` | create, update, delete, addMember, findMembers |
| `RepositoryService` | create, update, delete, updateLocalMirrorPath |
| `DocumentService` | upsertDocument, markDeleted, findVersions |
| `SearchService` | searchByKeyword |
| `SyncService` | startSync, findLatestByRepositoryId |
| `GitSyncService` | syncRepository (비동기 실행) |

---

## 설정

### application.yml 주요 설정

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:docst}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}

  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false

  flyway:
    enabled: true
    locations: classpath:db/migration

docst:
  git:
    root-path: ${DOCST_GIT_ROOT:/data/git}
  auth:
    mode: ${DOCST_AUTH_MODE:local}
```

### docker-compose.yml

```yaml
services:
  postgres:
    image: pgvector/pgvector:pg16
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]

  backend:
    environment:
      DB_HOST: postgres
      DOCST_GIT_ROOT: /data/git
    depends_on:
      postgres:
        condition: service_healthy
```

---

## 실행 방법

### Docker Compose
```bash
docker-compose up -d
```

### 로컬 개발 (PostgreSQL 필요)
```bash
cd backend
./gradlew bootRun
```

---

## API 엔드포인트

### REST API
| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/auth/local/login` | 로컬 로그인 |
| GET | `/api/projects` | 프로젝트 목록 |
| POST | `/api/projects` | 프로젝트 생성 |
| GET | `/api/projects/{id}` | 프로젝트 상세 |
| POST | `/api/projects/{id}/repositories` | 레포 연결 |
| POST | `/api/repositories/{id}/sync` | 동기화 실행 |
| GET | `/api/repositories/{id}/sync/status` | 동기화 상태 |
| GET | `/api/repositories/{id}/sync/stream` | SSE 스트림 |
| GET | `/api/repositories/{id}/documents` | 문서 목록 |
| GET | `/api/documents/{id}` | 문서 상세 |
| GET | `/api/documents/{id}/versions` | 버전 목록 |
| GET | `/api/documents/{id}/diff` | Diff |
| GET | `/api/projects/{id}/search` | 검색 |

### MCP Tools
| Tool | Path |
|------|------|
| list_documents | `POST /mcp/tools/list_documents` |
| get_document | `POST /mcp/tools/get_document` |
| list_document_versions | `POST /mcp/tools/list_document_versions` |
| diff_document | `POST /mcp/tools/diff_document` |
| search_documents | `POST /mcp/tools/search_documents` |
| sync_repository | `POST /mcp/tools/sync_repository` |

---

## 다음 단계 (Phase 2)

- [ ] DocChunk, DocEmbedding 엔티티 추가
- [ ] 청킹 시스템 구현
- [ ] pgvector 임베딩 저장
- [ ] 의미 검색 (semantic search) 구현
- [ ] 하이브리드 검색 (keyword + semantic)
