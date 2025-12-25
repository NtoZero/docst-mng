# Phase 1: MVP 구현 계획

> **목표**: GitHub/Local 레포 연결, 문서 조회/버전관리, 키워드 검색, MCP Tools 기초, docker-compose 로컬 실행

---

## 현재 구현 상태

### Backend (완료)
- [x] Spring Boot 3.5.x 기본 스캐폴딩
- [x] InMemoryStore 기반 스텁 CRUD 구현
- [x] REST API 컨트롤러 골격 (Auth, Projects, Repositories, Documents, Search, Sync)
- [x] API 모델 record 정의 (ApiModels.java)
- [x] 엔티티 record 정의 (Project, Repository, Document, DocumentVersion, SyncJob)

### Frontend (완료)
- [x] Next.js 16 App Router 스캐폴딩
- [x] 기본 layout.tsx, page.tsx

### 미구현
- [ ] PostgreSQL + JPA 연동
- [ ] User, ProjectMember 엔티티
- [ ] JGit 기반 실제 Git clone/fetch
- [ ] 실제 문서 파싱 및 인덱싱
- [ ] 키워드 검색 (PostgreSQL tsvector)
- [ ] MCP Server Tools
- [ ] 프론트엔드 UI 구현

---

## Sprint 1-1: 데이터베이스 레이어 구축

### 1.1.1 JPA 엔티티 구현
**위치**: `backend/src/main/java/com/docst/domain/`

| 엔티티 | 파일명 | 설명 |
|--------|--------|------|
| User | `User.java` | 사용자 (provider, email, displayName) |
| Project | `Project.java` | 프로젝트 |
| ProjectMember | `ProjectMember.java` | 프로젝트-사용자 매핑 (role) |
| Repository | `Repository.java` | 레포지토리 |
| Document | `Document.java` | 문서 메타데이터 |
| DocumentVersion | `DocumentVersion.java` | 문서 버전 (commit 기반) |
| SyncJob | `SyncJob.java` | 동기화 작업 상태 |

**구현 상세**:
```java
// 예시: Document.java
@Entity
@Table(name = "dm_document")
public class Document {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false)
    private Repository repository;

    @Column(nullable = false)
    private String path;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false)
    private DocType docType; // MD, ADOC, OPENAPI, ADR, OTHER

    @Column(name = "latest_commit_sha")
    private String latestCommitSha;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
```

### 1.1.2 Spring Data JPA Repository 구현
**위치**: `backend/src/main/java/com/docst/repository/`

| Repository | 메서드 |
|------------|--------|
| `UserRepository` | findByProviderAndProviderUserId() |
| `ProjectRepository` | findByMembersUserId() |
| `ProjectMemberRepository` | findByProjectIdAndUserId() |
| `RepositoryRepository` | findByProjectId() |
| `DocumentRepository` | findByRepositoryId(), findByRepositoryIdAndPath() |
| `DocumentVersionRepository` | findByDocumentIdOrderByCommittedAtDesc() |
| `SyncJobRepository` | findByRepositoryIdOrderByCreatedAtDesc() |

### 1.1.3 Flyway 마이그레이션 설정
**위치**: `backend/src/main/resources/db/migration/`

| 파일명 | 내용 |
|--------|------|
| `V1__init_schema.sql` | 핵심 테이블 생성 (dm_user, dm_project, dm_repository, dm_document, dm_document_version, dm_sync_job) |
| `V2__add_indexes.sql` | 인덱스 추가 |
| `V3__add_tsvector.sql` | tsvector 컬럼 및 GIN 인덱스 (키워드 검색용) |

---

## Sprint 1-2: 서비스 레이어 리팩토링

### 1.2.1 서비스 인터페이스 및 구현
**위치**: `backend/src/main/java/com/docst/service/`

| 서비스 | 책임 |
|--------|------|
| `ProjectService` | 프로젝트 CRUD, 멤버 관리 |
| `RepositoryService` | 레포 CRUD, 연결 검증 |
| `DocumentService` | 문서 조회, 버전 조회 |
| `SyncService` | Git clone/fetch, 문서 스캔, 인덱싱 |
| `SearchService` | 키워드 검색 |

### 1.2.2 컨트롤러 수정
- InMemoryStore 의존성 제거
- 서비스 레이어 주입으로 전환
- 트랜잭션 경계 설정

---

## Sprint 1-3: JGit 기반 Git 동기화

### 1.3.1 Git 서비스 구현
**위치**: `backend/src/main/java/com/docst/git/`

| 클래스 | 책임 |
|--------|------|
| `GitService` | clone, fetch, 브랜치 체크아웃 |
| `GitFileScanner` | 문서 파일 탐지 (경로 규칙 기반) |
| `DocumentParser` | Markdown/AsciiDoc 파싱, 제목 추출 |

**경로 규칙**:
```java
private static final List<String> DOC_PATTERNS = List.of(
    "README.md",
    "readme.md",
    "docs/**/*.md",
    "docs/**/*.adoc",
    "architecture/**/*.md",
    "adr/**/*.md",
    "*.openapi.yaml",
    "*.openapi.json"
);
```

### 1.3.2 동기화 파이프라인
```
1. SyncJob 생성 (PENDING)
2. Git clone/fetch (RUNNING)
3. 대상 브랜치 최신 커밋 조회
4. 문서 파일 스캔 (경로 규칙)
5. 각 문서에 대해:
   - Document 레코드 생성/업데이트
   - DocumentVersion 레코드 생성
   - 내용 저장 (content_hash로 중복 체크)
6. SyncJob 완료 (SUCCEEDED/FAILED)
```

### 1.3.3 SSE 진행 상태 스트리밍
```java
public record SyncEvent(
    UUID jobId,
    String status,    // CLONING, FETCHING, SCANNING, INDEXING, COMPLETED, FAILED
    String message,
    int progress,     // 0-100
    int totalDocs,
    int processedDocs
) {}
```

---

## Sprint 1-4: 키워드 검색 구현

### 1.4.1 PostgreSQL tsvector 활용
```sql
-- DocumentVersion에 tsvector 컬럼 추가 (Flyway V3)
ALTER TABLE dm_document_version
ADD COLUMN content_tsv tsvector
GENERATED ALWAYS AS (to_tsvector('english', coalesce(content, ''))) STORED;

CREATE INDEX idx_docver_content_tsv ON dm_document_version USING GIN (content_tsv);
```

### 1.4.2 검색 쿼리
```java
@Query(value = """
    SELECT dv.* FROM dm_document_version dv
    JOIN dm_document d ON d.id = dv.document_id
    JOIN dm_repository r ON r.id = d.repository_id
    WHERE r.project_id = :projectId
      AND dv.content_tsv @@ plainto_tsquery('english', :query)
    ORDER BY ts_rank(dv.content_tsv, plainto_tsquery('english', :query)) DESC
    LIMIT :topK
    """, nativeQuery = true)
List<DocumentVersion> searchByKeyword(UUID projectId, String query, int topK);
```

---

## Sprint 1-5: 프론트엔드 MVP UI

### 1.5.1 페이지 라우팅 구조
```
frontend/app/
├── layout.tsx           # 공통 레이아웃 (헤더, 사이드바)
├── page.tsx             # 랜딩/대시보드
├── login/
│   └── page.tsx         # 로그인 (Local/GitHub)
├── projects/
│   ├── page.tsx         # 프로젝트 목록
│   ├── new/
│   │   └── page.tsx     # 프로젝트 생성
│   └── [projectId]/
│       ├── page.tsx     # 프로젝트 상세
│       ├── repositories/
│       │   ├── page.tsx         # 레포 목록
│       │   └── new/
│       │       └── page.tsx     # 레포 연결
│       └── search/
│           └── page.tsx         # 검색
└── documents/
    └── [docId]/
        ├── page.tsx             # 문서 뷰어
        ├── versions/
        │   └── page.tsx         # 버전 목록
        └── diff/
            └── page.tsx         # Diff 뷰어
```

### 1.5.2 핵심 컴포넌트
**위치**: `frontend/components/`

| 컴포넌트 | 설명 |
|----------|------|
| `Header` | 상단 네비게이션, 사용자 메뉴 |
| `Sidebar` | 프로젝트/레포/문서 트리 |
| `DocumentTree` | 문서 트리 뷰 |
| `DocumentViewer` | Markdown/코드 렌더링 |
| `DiffViewer` | unified diff 시각화 |
| `SearchBar` | 검색 입력 |
| `SearchResults` | 검색 결과 목록 |
| `SyncProgress` | SSE 기반 진행 표시 |

### 1.5.3 상태 관리
- **TanStack Query (React Query)**: API 데이터 캐싱 및 동기화
- **Zustand**: 클라이언트 상태 (선택된 프로젝트, 테마 등)

### 1.5.4 UI 라이브러리
- **shadcn/ui**: 컴포넌트 기반
- **Tailwind CSS**: 스타일링
- **react-markdown**: Markdown 렌더링
- **react-diff-viewer-continued**: Diff 시각화

---

## Sprint 1-6: docker-compose 완성

### 1.6.1 서비스 구성
```yaml
services:
  postgres:
    image: pgvector/pgvector:pg16
    environment:
      POSTGRES_PASSWORD: postgres
      POSTGRES_USER: postgres
      POSTGRES_DB: docst
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 5s
      timeout: 5s
      retries: 5

  backend:
    build: ./backend
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/docst
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: postgres
      DOCST_GIT_ROOT: /data/git
      DOCST_AUTH_MODE: local
    volumes:
      - gitdata:/data/git
    ports:
      - "8080:8080"
    depends_on:
      postgres:
        condition: service_healthy

  frontend:
    build: ./frontend
    environment:
      NEXT_PUBLIC_API_BASE: http://localhost:8080
    ports:
      - "3000:3000"
    depends_on:
      - backend

volumes:
  pgdata:
  gitdata:
```

### 1.6.2 개발 환경 설정
```yaml
# docker-compose.dev.yml
services:
  postgres:
    ports:
      - "5432:5432"  # 로컬 개발용 포트 노출

  backend:
    volumes:
      - ./backend:/app
      - gitdata:/data/git
    command: ./gradlew bootRun --continuous

  frontend:
    volumes:
      - ./frontend:/app
      - /app/node_modules
    command: npm run dev
```

---

## Sprint 1-7: MCP Server Tools 기초

### 1.7.1 MCP 엔드포인트 설정
**위치**: `backend/src/main/java/com/docst/mcp/`

Spring AI MCP 또는 직접 구현:

```java
@RestController
@RequestMapping("/mcp")
public class McpController {

    @PostMapping("/tools/list_documents")
    public McpResponse<ListDocumentsResult> listDocuments(@RequestBody ListDocumentsInput input) {
        // ...
    }

    @PostMapping("/tools/get_document")
    public McpResponse<GetDocumentResult> getDocument(@RequestBody GetDocumentInput input) {
        // ...
    }

    @PostMapping("/tools/search_documents")
    public McpResponse<SearchDocumentsResult> searchDocuments(@RequestBody SearchDocumentsInput input) {
        // ...
    }
}
```

### 1.7.2 MCP Tools 목록 (Phase 1)
| Tool | 설명 |
|------|------|
| `list_documents` | 레포/프로젝트 내 문서 목록 |
| `get_document` | 문서 내용 조회 (특정 버전 가능) |
| `list_document_versions` | 문서 Git 히스토리 |
| `search_documents` | 키워드 검색 (mode: keyword) |

---

## 완료 기준 (Definition of Done)

### 기능
- [ ] 로컬 로그인 후 프로젝트 생성 가능
- [ ] GitHub/Local 레포 연결 가능
- [ ] 레포 동기화 실행 시 문서 인덱싱 완료
- [ ] 문서 목록/상세 조회 가능
- [ ] 문서 버전 목록/특정 버전 조회 가능
- [ ] 두 버전 간 Diff 표시 가능
- [ ] 키워드 검색 동작
- [ ] MCP Tools로 문서 조회 가능

### 기술
- [ ] docker-compose up 으로 전체 스택 기동
- [ ] PostgreSQL 데이터 영속화
- [ ] Git 미러 볼륨 영속화
- [ ] API 응답 시간 < 500ms (일반 조회)

---

## 파일 구조 (최종)

```
backend/
├── src/main/java/com/docst/
│   ├── DocstApplication.java
│   ├── domain/           # JPA 엔티티
│   │   ├── User.java
│   │   ├── Project.java
│   │   ├── ProjectMember.java
│   │   ├── Repository.java
│   │   ├── Document.java
│   │   ├── DocumentVersion.java
│   │   └── SyncJob.java
│   ├── repository/       # Spring Data JPA
│   │   ├── UserRepository.java
│   │   └── ...
│   ├── service/          # 비즈니스 로직
│   │   ├── ProjectService.java
│   │   ├── RepositoryService.java
│   │   ├── DocumentService.java
│   │   ├── SyncService.java
│   │   └── SearchService.java
│   ├── git/              # JGit 연동
│   │   ├── GitService.java
│   │   ├── GitFileScanner.java
│   │   └── DocumentParser.java
│   ├── api/              # REST 컨트롤러
│   │   └── (기존 유지)
│   └── mcp/              # MCP Tools
│       └── McpController.java
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/
│       ├── V1__init_schema.sql
│       ├── V2__add_indexes.sql
│       └── V3__add_tsvector.sql
└── build.gradle

frontend/
├── app/
│   ├── layout.tsx
│   ├── page.tsx
│   ├── login/
│   ├── projects/
│   └── documents/
├── components/
│   ├── ui/               # shadcn/ui
│   ├── Header.tsx
│   ├── Sidebar.tsx
│   ├── DocumentTree.tsx
│   ├── DocumentViewer.tsx
│   ├── DiffViewer.tsx
│   └── SearchResults.tsx
├── lib/
│   ├── api.ts            # API 클라이언트
│   └── store.ts          # Zustand 스토어
└── package.json
```
