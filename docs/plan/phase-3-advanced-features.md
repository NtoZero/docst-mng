# Phase 3: 고급 기능 구현 계획

> **목표**: Webhook 자동 동기화, 문서 관계 그래프, GitHub OAuth, 멀티 프로젝트 권한 고도화

---

## 선행 조건 (Phase 2 완료)
- [x] 의미 검색 동작
- [x] 청킹/임베딩 파이프라인
- [x] MCP semantic search 지원

---

## Sprint 3-1: GitHub OAuth 연동

### 3.1.1 OAuth 플로우 구현
**위치**: `backend/src/main/java/com/docst/auth/`

```
1. 프론트엔드: /api/auth/github/start 호출
2. 백엔드: GitHub authorize URL 반환 (state 토큰 포함)
3. 사용자: GitHub 로그인 및 권한 승인
4. GitHub: /api/auth/github/callback?code=...&state=... 리다이렉트
5. 백엔드:
   a. code로 access_token 교환
   b. GitHub API로 사용자 정보 조회
   c. User 레코드 생성/업데이트
   d. JWT 발급
6. 프론트엔드: JWT 저장 및 인증 상태 전환
```

### 3.1.2 GitHub OAuth 서비스
```java
@Service
public class GitHubOAuthService {

    @Value("${docst.github.client-id}")
    private String clientId;

    @Value("${docst.github.client-secret}")
    private String clientSecret;

    public String buildAuthorizeUrl(String state, String redirectUri) {
        return UriComponentsBuilder
            .fromUriString("https://github.com/login/oauth/authorize")
            .queryParam("client_id", clientId)
            .queryParam("redirect_uri", redirectUri)
            .queryParam("scope", "read:user user:email repo")
            .queryParam("state", state)
            .build()
            .toUriString();
    }

    public GitHubTokenResponse exchangeCodeForToken(String code) {
        // POST https://github.com/login/oauth/access_token
        // 반환: access_token, token_type, scope
    }

    public GitHubUser getUserInfo(String accessToken) {
        // GET https://api.github.com/user
        // Headers: Authorization: Bearer {accessToken}
    }

    public List<GitHubEmail> getUserEmails(String accessToken) {
        // GET https://api.github.com/user/emails
    }
}
```

### 3.1.3 JWT 인증 필터
```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtService.validateAndParse(token);
                UUID userId = UUID.fromString(claims.getSubject());
                // SecurityContext에 인증 정보 설정
            } catch (JwtException e) {
                // 401 Unauthorized
            }
        }
        chain.doFilter(request, response);
    }
}
```

### 3.1.4 설정
```yaml
docst:
  github:
    client-id: ${GITHUB_CLIENT_ID}
    client-secret: ${GITHUB_CLIENT_SECRET}
    callback-url: http://localhost:3000/api/auth/callback

  jwt:
    secret: ${JWT_SECRET}
    expiration: 86400  # 24시간
```

---

## Sprint 3-2: GitHub Webhook 자동 동기화

### 3.2.1 Webhook 엔드포인트
**위치**: `backend/src/main/java/com/docst/webhook/`

```java
@RestController
@RequestMapping("/webhook/github")
public class GitHubWebhookController {

    @PostMapping
    public ResponseEntity<Void> handleWebhook(
        @RequestHeader("X-GitHub-Event") String event,
        @RequestHeader("X-Hub-Signature-256") String signature,
        @RequestBody String payload
    ) {
        // 1. 시그니처 검증
        if (!webhookService.verifySignature(payload, signature)) {
            return ResponseEntity.status(401).build();
        }

        // 2. 이벤트 타입별 처리
        switch (event) {
            case "push" -> webhookService.handlePush(payload);
            case "repository" -> webhookService.handleRepository(payload);
            default -> log.debug("Ignored event: {}", event);
        }

        return ResponseEntity.ok().build();
    }
}
```

### 3.2.2 Push 이벤트 처리
```java
@Service
public class WebhookService {

    public void handlePush(String payload) {
        GitHubPushEvent event = objectMapper.readValue(payload, GitHubPushEvent.class);

        // 1. 레포지토리 조회 (external_id 또는 clone_url 기준)
        Optional<Repository> repoOpt = repositoryRepository
            .findByProviderAndExternalId("GITHUB", event.repository().id().toString());

        if (repoOpt.isEmpty()) {
            log.debug("Repository not registered: {}", event.repository().fullName());
            return;
        }

        Repository repo = repoOpt.get();

        // 2. 대상 브랜치 확인
        String ref = event.ref();  // refs/heads/main
        String branch = ref.replace("refs/heads/", "");
        if (!branch.equals(repo.getDefaultBranch())) {
            log.debug("Push to non-default branch: {}", branch);
            return;
        }

        // 3. 변경된 문서 파일 확인
        Set<String> changedDocs = event.commits().stream()
            .flatMap(c -> Stream.of(c.added(), c.modified(), c.removed()).flatMap(List::stream))
            .filter(this::isDocumentFile)
            .collect(Collectors.toSet());

        if (changedDocs.isEmpty()) {
            log.debug("No document files changed");
            return;
        }

        // 4. 증분 동기화 실행
        syncService.syncIncremental(repo.getId(), event.after(), changedDocs);
    }

    private boolean isDocumentFile(String path) {
        return path.endsWith(".md") ||
               path.endsWith(".adoc") ||
               path.endsWith(".openapi.yaml") ||
               path.endsWith(".openapi.json");
    }
}
```

### 3.2.3 증분 동기화
```java
@Service
public class SyncService {

    public void syncIncremental(UUID repoId, String commitSha, Set<String> changedFiles) {
        SyncJob job = syncJobRepository.save(new SyncJob(repoId, "RUNNING", commitSha));

        try {
            // 1. Git fetch (clone 없이)
            gitService.fetch(repoId);

            // 2. 변경된 파일만 처리
            for (String filePath : changedFiles) {
                Optional<String> content = gitService.getFileContent(repoId, commitSha, filePath);
                if (content.isPresent()) {
                    // 문서 업데이트 (upsert)
                    documentService.upsertDocument(repoId, filePath, commitSha, content.get());
                } else {
                    // 삭제된 파일
                    documentService.markDeleted(repoId, filePath);
                }
            }

            job.complete(commitSha);
        } catch (Exception e) {
            job.fail(e.getMessage());
        }

        syncJobRepository.save(job);
    }
}
```

### 3.2.4 Webhook 등록 자동화
```java
@Service
public class GitHubWebhookRegistrationService {

    public void registerWebhook(Repository repo, String userAccessToken) {
        // POST /repos/{owner}/{repo}/hooks
        // {
        //   "name": "web",
        //   "active": true,
        //   "events": ["push"],
        //   "config": {
        //     "url": "https://docst.example.com/webhook/github",
        //     "content_type": "json",
        //     "secret": "webhook-secret"
        //   }
        // }
    }
}
```

---

## Sprint 3-3: 문서 관계 그래프

### 3.3.1 문서 링크 추출
**위치**: `backend/src/main/java/com/docst/graph/`

```java
@Service
public class DocumentLinkExtractor {

    private static final Pattern MARKDOWN_LINK = Pattern.compile(
        "\\[([^\\]]+)\\]\\(([^)]+)\\)"
    );

    private static final Pattern REFERENCE_LINK = Pattern.compile(
        "\\[([^\\]]+)\\]:\\s*(\\S+)"
    );

    public List<DocumentLink> extractLinks(String content, String basePath) {
        List<DocumentLink> links = new ArrayList<>();

        // Markdown 링크 [text](url)
        Matcher matcher = MARKDOWN_LINK.matcher(content);
        while (matcher.find()) {
            String text = matcher.group(1);
            String target = matcher.group(2);
            if (isInternalLink(target)) {
                String resolvedPath = resolvePath(basePath, target);
                links.add(new DocumentLink(text, resolvedPath, LinkType.REFERENCE));
            }
        }

        // 참조 링크 [text]: url
        matcher = REFERENCE_LINK.matcher(content);
        while (matcher.find()) {
            String text = matcher.group(1);
            String target = matcher.group(2);
            if (isInternalLink(target)) {
                String resolvedPath = resolvePath(basePath, target);
                links.add(new DocumentLink(text, resolvedPath, LinkType.REFERENCE));
            }
        }

        return links;
    }

    private boolean isInternalLink(String link) {
        return !link.startsWith("http://") &&
               !link.startsWith("https://") &&
               !link.startsWith("mailto:");
    }
}
```

### 3.3.2 DocumentRelation 엔티티
```java
@Entity
@Table(name = "dm_document_relation")
public class DocumentRelation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_document_id", nullable = false)
    private Document sourceDocument;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_document_id")
    private Document targetDocument;

    @Column(name = "target_path")
    private String targetPath;  // targetDocument가 없을 때 (broken link)

    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type", nullable = false)
    private RelationType relationType;  // REFERENCES, IMPORTS, EXTENDS

    @Column(name = "link_text")
    private String linkText;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

public enum RelationType {
    REFERENCES,  // 일반 문서 링크
    IMPORTS,     // OpenAPI $ref 등
    EXTENDS      // ADR supersedes 등
}
```

### 3.3.3 관계 그래프 API
```java
@RestController
@RequestMapping("/api/projects/{projectId}/graph")
public class DocumentGraphController {

    @GetMapping
    public GraphResponse getProjectGraph(@PathVariable UUID projectId) {
        // 프로젝트 내 모든 문서 관계 반환
    }

    @GetMapping("/document/{docId}")
    public GraphResponse getDocumentGraph(
        @PathVariable UUID projectId,
        @PathVariable UUID docId,
        @RequestParam(defaultValue = "2") int depth
    ) {
        // 특정 문서 중심으로 depth 깊이까지의 관계 반환
    }
}

public record GraphResponse(
    List<GraphNode> nodes,
    List<GraphEdge> edges
) {}

public record GraphNode(
    UUID id,
    String path,
    String title,
    String docType,
    int inDegree,   // 이 문서를 참조하는 수
    int outDegree   // 이 문서가 참조하는 수
) {}

public record GraphEdge(
    UUID sourceId,
    UUID targetId,
    String relationType,
    String linkText
) {}
```

### 3.3.4 프론트엔드 그래프 시각화
```tsx
// components/DocumentGraph.tsx
import { ForceGraph2D } from 'react-force-graph';

export function DocumentGraph({ projectId }: { projectId: string }) {
  const { data } = useQuery(['graph', projectId], () => fetchGraph(projectId));

  const graphData = useMemo(() => ({
    nodes: data?.nodes.map(n => ({
      id: n.id,
      name: n.title,
      val: n.inDegree + n.outDegree + 1,
      color: getColorByDocType(n.docType),
    })),
    links: data?.edges.map(e => ({
      source: e.sourceId,
      target: e.targetId,
    })),
  }), [data]);

  return (
    <ForceGraph2D
      graphData={graphData}
      nodeLabel="name"
      linkDirectionalArrowLength={6}
      onNodeClick={(node) => router.push(`/documents/${node.id}`)}
    />
  );
}
```

---

## Sprint 3-4: 영향 분석 (Impact Analysis)

### 3.4.1 역방향 관계 조회
```java
@Service
public class ImpactAnalysisService {

    /**
     * 이 문서를 참조하는 모든 문서 조회 (직접 + 간접)
     */
    public List<ImpactedDocument> findDependents(UUID documentId, int maxDepth) {
        Set<UUID> visited = new HashSet<>();
        List<ImpactedDocument> result = new ArrayList<>();

        Queue<DependentSearch> queue = new LinkedList<>();
        queue.add(new DependentSearch(documentId, 0));

        while (!queue.isEmpty()) {
            DependentSearch current = queue.poll();
            if (current.depth > maxDepth || visited.contains(current.docId)) {
                continue;
            }
            visited.add(current.docId);

            List<DocumentRelation> relations = relationRepository
                .findByTargetDocumentId(current.docId);

            for (DocumentRelation rel : relations) {
                Document sourceDoc = rel.getSourceDocument();
                result.add(new ImpactedDocument(
                    sourceDoc,
                    current.depth + 1,
                    rel.getRelationType()
                ));
                queue.add(new DependentSearch(sourceDoc.getId(), current.depth + 1));
            }
        }

        return result;
    }
}

public record ImpactedDocument(
    Document document,
    int depth,
    RelationType relationType
) {}
```

### 3.4.2 변경 영향 알림
```java
public record DocumentChangeNotification(
    UUID changedDocumentId,
    String changedDocumentPath,
    List<ImpactedDocumentSummary> impactedDocuments,
    String changeType  // MODIFIED, DELETED, RENAMED
) {}

// 문서 수정 시 영향받는 문서 목록 조회 후 프론트엔드에 표시
```

---

## Sprint 3-5: 멀티 프로젝트 권한 고도화

### 3.5.1 역할 기반 권한
```java
public enum ProjectRole {
    OWNER,    // 프로젝트 삭제, 멤버 관리
    ADMIN,    // 레포 관리, 동기화 실행
    EDITOR,   // 문서 수정 (향후)
    VIEWER    // 읽기 전용
}
```

### 3.5.2 권한 체크 AOP
```java
@Aspect
@Component
public class ProjectAuthorizationAspect {

    @Before("@annotation(requiresProjectRole)")
    public void checkProjectRole(JoinPoint joinPoint, RequiresProjectRole requiresProjectRole) {
        UUID projectId = extractProjectId(joinPoint);
        UUID userId = SecurityContextHolder.getContext().getUserId();
        ProjectRole requiredRole = requiresProjectRole.value();

        ProjectMember member = memberRepository.findByProjectIdAndUserId(projectId, userId)
            .orElseThrow(() -> new AccessDeniedException("Not a project member"));

        if (!member.getRole().hasPermission(requiredRole)) {
            throw new AccessDeniedException("Insufficient permissions");
        }
    }
}

// 사용 예시
@RestController
public class RepositoriesController {

    @PostMapping("/projects/{projectId}/repositories")
    @RequiresProjectRole(ProjectRole.ADMIN)
    public ResponseEntity<RepositoryResponse> createRepository(...) {
        // ...
    }
}
```

### 3.5.3 멤버 초대 기능
```java
@RestController
@RequestMapping("/api/projects/{projectId}/members")
public class ProjectMemberController {

    @PostMapping("/invite")
    @RequiresProjectRole(ProjectRole.OWNER)
    public ResponseEntity<InviteResponse> inviteMember(
        @PathVariable UUID projectId,
        @RequestBody InviteRequest request
    ) {
        // 이메일로 초대 링크 발송 또는 GitHub username으로 직접 추가
    }

    @PutMapping("/{memberId}/role")
    @RequiresProjectRole(ProjectRole.OWNER)
    public ResponseEntity<ProjectMemberResponse> updateRole(
        @PathVariable UUID projectId,
        @PathVariable UUID memberId,
        @RequestBody UpdateRoleRequest request
    ) {
        // 역할 변경
    }
}
```

---

## Sprint 3-6: MCP Tools 고도화

### 3.6.1 추가 Tools

| Tool | 설명 |
|------|------|
| `get_document_graph` | 문서 관계 그래프 조회 |
| `analyze_impact` | 문서 변경 영향 분석 |
| `get_related_documents` | 관련 문서 추천 |
| `sync_repository` | 레포 동기화 트리거 |
| `get_sync_status` | 동기화 상태 조회 |

### 3.6.2 get_document_graph
```json
{
  "name": "get_document_graph",
  "description": "문서 관계 그래프 조회",
  "inputSchema": {
    "type": "object",
    "required": ["documentId"],
    "properties": {
      "documentId": { "type": "string", "format": "uuid" },
      "depth": { "type": "integer", "minimum": 1, "maximum": 5, "default": 2 },
      "direction": {
        "type": "string",
        "enum": ["outgoing", "incoming", "both"],
        "default": "both"
      }
    }
  }
}
```

### 3.6.3 analyze_impact
```json
{
  "name": "analyze_impact",
  "description": "문서 변경 시 영향받는 문서 분석",
  "inputSchema": {
    "type": "object",
    "required": ["documentId"],
    "properties": {
      "documentId": { "type": "string", "format": "uuid" },
      "maxDepth": { "type": "integer", "minimum": 1, "maximum": 10, "default": 3 }
    }
  },
  "outputSchema": {
    "type": "object",
    "required": ["impactedDocuments"],
    "properties": {
      "impactedDocuments": {
        "type": "array",
        "items": {
          "type": "object",
          "properties": {
            "documentId": { "type": "string" },
            "path": { "type": "string" },
            "title": { "type": "string" },
            "depth": { "type": "integer" },
            "relationType": { "type": "string" }
          }
        }
      },
      "totalCount": { "type": "integer" }
    }
  }
}
```

---

## Sprint 3-7: 운영 기능

### 3.7.1 관리자 대시보드
- 전체 프로젝트/레포/문서 통계
- 동기화 작업 모니터링
- 임베딩 처리 큐 상태
- 에러 로그 조회

### 3.7.2 메트릭 수집
```java
@Component
public class DocstMetrics {
    private final MeterRegistry registry;

    public void recordSyncDuration(String repoName, long durationMs) {
        registry.timer("docst.sync.duration", "repo", repoName)
            .record(Duration.ofMillis(durationMs));
    }

    public void recordSearchLatency(String mode, long durationMs) {
        registry.timer("docst.search.latency", "mode", mode)
            .record(Duration.ofMillis(durationMs));
    }

    public void recordEmbeddingQueue(int size) {
        registry.gauge("docst.embedding.queue.size", size);
    }
}
```

### 3.7.3 헬스체크
```java
@Component
public class DocstHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        // PostgreSQL 연결 확인
        // Git 볼륨 마운트 확인
        // 임베딩 서비스 연결 확인
        return Health.up()
            .withDetail("database", "connected")
            .withDetail("gitVolume", "mounted")
            .withDetail("embeddingService", "available")
            .build();
    }
}
```

---

## 완료 기준 (Definition of Done)

### 기능
- [ ] GitHub OAuth 로그인 동작
- [ ] GitHub Webhook 등록 및 자동 동기화
- [ ] 문서 관계 그래프 시각화
- [ ] 영향 분석 API 동작
- [ ] 프로젝트 멤버 초대 및 역할 관리
- [ ] MCP 고급 Tools 동작

### 보안
- [ ] JWT 토큰 만료 및 갱신 처리
- [ ] Webhook 시그니처 검증
- [ ] 역할 기반 API 접근 제어

### 운영
- [ ] Prometheus 메트릭 노출
- [ ] 헬스체크 엔드포인트

---

## 파일 구조 (추가)

```
backend/
├── src/main/java/com/docst/
│   ├── auth/                        # 추가
│   │   ├── JwtService.java
│   │   ├── JwtAuthenticationFilter.java
│   │   └── GitHubOAuthService.java
│   ├── webhook/                     # 추가
│   │   ├── GitHubWebhookController.java
│   │   └── WebhookService.java
│   ├── graph/                       # 추가
│   │   ├── DocumentLinkExtractor.java
│   │   ├── DocumentGraphService.java
│   │   └── ImpactAnalysisService.java
│   ├── domain/
│   │   └── DocumentRelation.java    # 추가
│   └── security/                    # 추가
│       ├── ProjectAuthorizationAspect.java
│       └── RequiresProjectRole.java
└── src/main/resources/
    └── db/migration/
        ├── V6__add_document_relation.sql
        └── V7__add_webhook_config.sql

frontend/
├── app/
│   ├── auth/
│   │   └── callback/
│   │       └── page.tsx             # OAuth callback
│   └── projects/[projectId]/
│       ├── graph/
│       │   └── page.tsx             # 그래프 시각화
│       ├── members/
│       │   └── page.tsx             # 멤버 관리
│       └── settings/
│           └── page.tsx             # 프로젝트 설정
└── components/
    ├── DocumentGraph.tsx            # 추가
    └── MemberList.tsx               # 추가
```

---

## 향후 확장 고려사항

### Phase 4 후보
- 실시간 협업 코멘트 (WebSocket)
- 문서 템플릿 및 생성 마법사
- CI/CD 파이프라인 통합 (문서 품질 게이트)
- 다국어 문서 지원
- 문서 변환 (Markdown → PDF/HTML)
- API 문서 자동 생성 (코드 분석)
