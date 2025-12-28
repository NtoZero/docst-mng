# Phase 3-C: GitHub Webhook 자동 동기화 구현

## 개요

GitHub webhook을 통해 레포지토리에 새로운 커밋이 푸시되면 자동으로 동기화를 실행하는 기능을 구현했습니다.

### 주요 기능
- **Push Event 처리**: GitHub에서 push 이벤트를 수신하고 자동으로 동기화 실행
- **서명 검증**: HMAC-SHA256 서명을 통한 webhook 요청 인증
- **증분 동기화**: 기본 브랜치에 대한 변경 사항만 자동 동기화
- **보안**: Spring Security와 분리된 webhook 전용 서명 검증

---

## 구현 내용

### 1. GitHubWebhookController

**위치**: `backend/src/main/java/com/docst/webhook/GitHubWebhookController.java`

GitHub webhook 이벤트를 수신하는 REST 컨트롤러입니다.

```java
@PostMapping("/api/webhook/github")
public ResponseEntity<Map<String, String>> handleGitHubWebhook(
        @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
        @RequestHeader(value = "X-GitHub-Event", required = false) String event,
        @RequestHeader(value = "X-GitHub-Delivery", required = false) String delivery,
        @RequestBody String payload) {

    log.info("Received GitHub webhook: event={}, delivery={}", event, delivery);

    // 서명 검증
    if (!webhookService.verifySignature(payload, signature)) {
        log.warn("Invalid webhook signature: delivery={}", delivery);
        return ResponseEntity.status(401).body(Map.of("error", "Invalid signature"));
    }

    // 이벤트 처리
    try {
        webhookService.processWebhookEvent(event, payload);
        return ResponseEntity.ok(Map.of("status", "ok", "message", "Webhook processed"));
    } catch (Exception e) {
        log.error("Failed to process webhook: event={}, delivery={}", event, delivery, e);
        return ResponseEntity.status(500).body(Map.of("error", "Failed to process webhook"));
    }
}
```

**주요 특징**:
- `X-Hub-Signature-256` 헤더로 서명 검증
- `X-GitHub-Event` 헤더로 이벤트 타입 구분
- `X-GitHub-Delivery` 헤더로 요청 추적
- Raw JSON string payload 수신 (서명 검증을 위해)

---

### 2. WebhookService

**위치**: `backend/src/main/java/com/docst/webhook/WebhookService.java`

Webhook 서명 검증 및 이벤트 처리를 담당하는 서비스입니다.

#### 2.1 서명 검증

```java
public boolean verifySignature(String payload, String signature) {
    if (webhookSecret == null || webhookSecret.isEmpty()) {
        log.warn("Webhook secret is not configured. Signature verification disabled.");
        return true; // 개발 환경에서는 검증 생략 가능
    }

    if (signature == null || !signature.startsWith("sha256=")) {
        log.warn("Invalid signature format: {}", signature);
        return false;
    }

    try {
        // HMAC-SHA256으로 서명 계산
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(
                webhookSecret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );
        mac.init(secretKeySpec);
        byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        String expectedSignature = "sha256=" + HexFormat.of().formatHex(hash);

        // 타이밍 공격 방지를 위한 일정 시간 비교
        return java.security.MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
        );
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
        log.error("Failed to verify webhook signature", e);
        return false;
    }
}
```

**보안 특징**:
- HMAC-SHA256 서명 검증
- 타이밍 공격 방지를 위한 `MessageDigest.isEqual()` 사용
- 개발 환경에서는 secret 미설정 시 검증 생략 가능

#### 2.2 Push 이벤트 처리

```java
private void handlePushEvent(String payload) {
    try {
        JsonNode json = objectMapper.readTree(payload);

        // 레포지토리 정보 추출
        JsonNode repoNode = json.path("repository");
        String fullName = repoNode.path("full_name").asText(); // "owner/repo"
        String[] parts = fullName.split("/");
        String owner = parts[0];
        String repoName = parts[1];

        // 브랜치 정보 추출
        String ref = json.path("ref").asText(); // "refs/heads/main"
        String branch = ref.replace("refs/heads/", "");

        // 커밋 정보 추출
        JsonNode headCommitNode = json.path("head_commit");
        String commitSha = headCommitNode.path("id").asText();
        String commitMessage = headCommitNode.path("message").asText();

        log.info("Received push event: repository={}, branch={}, commit={}, message={}",
                fullName, branch, commitSha.substring(0, 7), commitMessage);

        // 해당 레포지토리 찾기
        Optional<Repository> repoOpt = repositoryRepository.findByOwnerAndName(owner, repoName);
        if (repoOpt.isEmpty()) {
            log.warn("Repository not found in database: {}", fullName);
            return;
        }

        Repository repository = repoOpt.get();

        // 기본 브랜치가 아니면 무시
        if (!branch.equals(repository.getDefaultBranch())) {
            log.info("Ignoring push to non-default branch: {} (default: {})",
                    branch, repository.getDefaultBranch());
            return;
        }

        // 비동기 동기화 실행
        log.info("Triggering incremental sync for repository: {}", fullName);
        syncService.startSync(repository.getId(), branch);

    } catch (Exception e) {
        log.error("Failed to handle push event", e);
        throw new RuntimeException("Failed to process push event", e);
    }
}
```

**처리 로직**:
1. Payload에서 레포지토리 정보 추출 (`owner/repo`)
2. 브랜치 정보 추출 (`refs/heads/main` → `main`)
3. 커밋 정보 추출 (SHA, message)
4. DB에서 해당 레포지토리 조회
5. 기본 브랜치가 아니면 무시
6. `SyncService.startSync()` 호출하여 비동기 동기화 실행

---

### 3. RepositoryRepository 확장

**위치**: `backend/src/main/java/com/docst/repository/RepositoryRepository.java`

Webhook 이벤트 처리를 위해 `findByOwnerAndName` 메서드를 추가했습니다.

```java
/**
 * 소유자와 이름으로 레포지토리를 조회한다.
 * Webhook 이벤트 처리 시 사용한다.
 *
 * @param owner 소유자 이름
 * @param name 레포 이름
 * @return 레포지토리 (존재하지 않으면 empty)
 */
Optional<Repository> findByOwnerAndName(String owner, String name);
```

**특징**:
- GitHub webhook payload의 `repository.full_name` (`owner/repo`)을 파싱하여 조회
- 프로젝트 ID 없이 조회 가능 (동일한 레포를 여러 프로젝트에서 사용하는 경우 첫 번째 결과 반환)

---

### 4. SecurityConfig 업데이트

**위치**: `backend/src/main/java/com/docst/config/SecurityConfig.java`

Webhook 엔드포인트를 Spring Security에서 제외했습니다.

```java
.authorizeHttpRequests(auth -> auth
        // Public endpoints
        .requestMatchers(
                "/api/auth/**",
                "/api/webhook/**",  // Webhook 엔드포인트 추가
                "/actuator/health",
                "/error"
        ).permitAll()
        // All other endpoints require authentication
        .anyRequest().authenticated()
)
```

**이유**:
- GitHub webhook은 JWT 토큰이 없으므로 Spring Security 인증에서 제외
- 대신 WebhookService에서 HMAC-SHA256 서명으로 검증

---

### 5. 설정 파일

#### application.yml

```yaml
docst:
  # GitHub Webhook Configuration
  webhook:
    github:
      secret: ${GITHUB_WEBHOOK_SECRET:}
```

#### .env.example

```bash
# GitHub Webhook Configuration
# Generate a random secret for webhook signature verification
# You can generate one with: openssl rand -hex 32
GITHUB_WEBHOOK_SECRET=your-webhook-secret-change-in-production
```

---

## GitHub Webhook 설정 방법

### 1. Webhook Secret 생성

```bash
# Random secret 생성
openssl rand -hex 32

# .env 파일에 추가
echo "GITHUB_WEBHOOK_SECRET=<generated-secret>" >> .env
```

### 2. GitHub 레포지토리에 Webhook 추가

1. GitHub 레포지토리 페이지 접속
2. **Settings** → **Webhooks** → **Add webhook** 클릭
3. Webhook 설정:
   - **Payload URL**: `https://your-domain.com/api/webhook/github`
   - **Content type**: `application/json`
   - **Secret**: `.env` 파일의 `GITHUB_WEBHOOK_SECRET` 값 입력
   - **Which events would you like to trigger this webhook?**:
     - "Just the push event" 선택
   - **Active** 체크박스 활성화
4. **Add webhook** 클릭

### 3. Webhook 테스트

#### Ping 이벤트 확인

Webhook 생성 시 GitHub이 자동으로 ping 이벤트를 전송합니다.

```bash
# 백엔드 로그 확인
docker-compose logs -f backend

# 예상 로그:
# Received GitHub webhook: event=ping, delivery=12345678-1234-1234-1234-123456789abc
# Received GitHub ping: Mind how you go
```

#### Push 이벤트 테스트

```bash
# 레포지토리에 커밋 푸시
git commit --allow-empty -m "Test webhook"
git push

# 백엔드 로그 확인
docker-compose logs -f backend

# 예상 로그:
# Received GitHub webhook: event=push, delivery=...
# Received push event: repository=owner/repo, branch=main, commit=abc1234, message=Test webhook
# Triggering incremental sync for repository: owner/repo
# Starting sync for repository: owner/repo branch: main mode: FULL_SCAN
```

---

## API 명세

### POST /api/webhook/github

GitHub webhook 이벤트를 수신합니다.

**Headers**:
```
X-Hub-Signature-256: sha256=<hmac-sha256-signature>
X-GitHub-Event: push
X-GitHub-Delivery: 12345678-1234-1234-1234-123456789abc
Content-Type: application/json
```

**Request Body** (Push Event):
```json
{
  "ref": "refs/heads/main",
  "repository": {
    "id": 123456,
    "full_name": "owner/repo",
    "name": "repo",
    "owner": {
      "login": "owner"
    }
  },
  "head_commit": {
    "id": "abc1234567890def1234567890abcdef12345678",
    "message": "Fix bug in search",
    "author": {
      "name": "John Doe",
      "email": "john@example.com"
    },
    "timestamp": "2024-01-15T10:30:00Z"
  }
}
```

**Response** (Success):
```json
{
  "status": "ok",
  "message": "Webhook processed"
}
```

**Response** (Invalid Signature):
```json
{
  "error": "Invalid signature"
}
```

### GET /api/webhook/github/ping

Webhook 연결 테스트용 엔드포인트입니다.

**Response**:
```json
{
  "status": "pong"
}
```

---

## 지원하는 이벤트

| Event | 처리 여부 | 설명 |
|-------|----------|------|
| `ping` | ✅ | Webhook 설정 시 전송되는 테스트 이벤트 |
| `push` | ✅ | 레포지토리에 커밋이 푸시될 때 전송 |
| 기타 | ❌ | 무시 (로그만 남김) |

**Push 이벤트 처리 조건**:
- 레포지토리가 Docst에 등록되어 있어야 함 (`dm_repository` 테이블)
- 푸시된 브랜치가 레포지토리의 기본 브랜치여야 함
- 조건을 만족하면 `SyncService.startSync()` 호출하여 자동 동기화

---

## 동작 흐름

```
┌─────────────┐       Push Event        ┌──────────────────┐
│   GitHub    │ ─────────────────────→  │ WebhookController│
│ Repository  │                          │ POST /webhook/   │
└─────────────┘                          │      github      │
                                         └──────────────────┘
                                                  │
                                                  │ verifySignature()
                                                  ▼
                                         ┌──────────────────┐
                                         │ WebhookService   │
                                         │ - verifySignature│
                                         │ - handlePushEvent│
                                         └──────────────────┘
                                                  │
                                                  │ findByOwnerAndName()
                                                  ▼
                                         ┌──────────────────┐
                                         │ Repository       │
                                         │ Repository       │
                                         └──────────────────┘
                                                  │
                                                  │ startSync()
                                                  ▼
                                         ┌──────────────────┐
                                         │ SyncService      │
                                         │ - startSync()    │
                                         │ - executeSync()  │
                                         └──────────────────┘
                                                  │
                                                  ▼
                                         ┌──────────────────┐
                                         │ GitSyncService   │
                                         │ - syncRepository │
                                         │ - updateDocuments│
                                         └──────────────────┘
```

**단계별 설명**:
1. GitHub에서 push 이벤트 발생
2. GitHub이 `POST /api/webhook/github`로 webhook 전송
3. `GitHubWebhookController`가 요청 수신
4. `WebhookService.verifySignature()`로 서명 검증
5. `WebhookService.handlePushEvent()`로 이벤트 처리
6. `RepositoryRepository.findByOwnerAndName()`로 레포지토리 조회
7. `SyncService.startSync()`로 동기화 작업 생성
8. 비동기로 `GitSyncService.syncRepository()` 실행
9. 문서 파싱, 청킹, 임베딩 파이프라인 실행

---

## 보안 고려사항

### 1. 서명 검증

- **HMAC-SHA256**: GitHub이 webhook secret으로 서명한 payload를 검증
- **타이밍 공격 방지**: `MessageDigest.isEqual()`을 사용하여 일정 시간 비교
- **Secret 관리**: 환경 변수로 관리, 코드에 하드코딩하지 않음

### 2. Spring Security 분리

- Webhook 엔드포인트는 JWT 인증에서 제외
- 대신 독립적인 HMAC 서명 검증 사용
- 잘못된 서명은 401 Unauthorized 반환

### 3. 입력 검증

- 이벤트 타입 확인 (`X-GitHub-Event` 헤더)
- Payload 파싱 실패 시 500 에러 반환
- 레포지토리가 DB에 없으면 무시

### 4. Rate Limiting

- **현재 미구현**: 필요 시 Spring Security의 RateLimiter 또는 외부 솔루션 사용 고려
- GitHub webhook은 자체적으로 재시도 로직이 있어 급격한 요청 증가 가능성은 낮음

---

## 트러블슈팅

### Webhook이 전송되지 않음

1. GitHub webhook 설정 확인
   - Settings → Webhooks에서 webhook이 Active 상태인지 확인
   - Recent Deliveries 탭에서 전송 기록 확인

2. Payload URL 확인
   - 공개적으로 접근 가능한 URL이어야 함
   - 로컬 개발 환경에서는 ngrok 등의 터널링 도구 사용

### 서명 검증 실패 (401 Unauthorized)

1. Webhook secret 확인
   - `.env` 파일의 `GITHUB_WEBHOOK_SECRET`이 GitHub 설정과 일치하는지 확인

2. Payload 수정 여부 확인
   - 프록시나 미들웨어가 payload를 수정하면 서명이 일치하지 않음
   - Raw JSON string을 그대로 전달해야 함

### 동기화가 실행되지 않음

1. 레포지토리 등록 확인
   ```sql
   SELECT * FROM dm_repository WHERE owner = 'owner' AND name = 'repo';
   ```

2. 브랜치 확인
   - 푸시된 브랜치가 기본 브랜치와 일치하는지 확인
   - 백엔드 로그에서 "Ignoring push to non-default branch" 메시지 확인

3. 동시 동기화 제한 확인
   - 이미 동기화가 진행 중이면 새로운 동기화가 거부됨
   - `dm_sync_job` 테이블에서 `status = 'RUNNING'`인 작업 확인

---

## 로컬 개발 환경 테스트

### ngrok을 사용한 로컬 테스트

```bash
# ngrok 설치 (https://ngrok.com/)
# ngrok 실행
ngrok http 8342

# 출력된 URL을 GitHub webhook에 설정
# 예: https://abc123.ngrok.io/api/webhook/github
```

### curl을 사용한 수동 테스트

```bash
# Webhook secret
SECRET="your-webhook-secret"

# Payload
PAYLOAD='{"ref":"refs/heads/main","repository":{"full_name":"owner/repo"},"head_commit":{"id":"abc123","message":"Test"}}'

# 서명 생성
SIGNATURE=$(echo -n "$PAYLOAD" | openssl dgst -sha256 -hmac "$SECRET" | sed 's/^.* //')

# Webhook 전송
curl -X POST http://localhost:8342/api/webhook/github \
  -H "Content-Type: application/json" \
  -H "X-GitHub-Event: push" \
  -H "X-Hub-Signature-256: sha256=$SIGNATURE" \
  -H "X-GitHub-Delivery: 12345678-1234-1234-1234-123456789abc" \
  -d "$PAYLOAD"
```

---

## 향후 개선 사항

### 1. 이벤트 타입 확장
- `pull_request`: PR 생성/업데이트 시 임시 동기화
- `delete`: 브랜치 삭제 시 관련 문서 정리
- `repository`: 레포지토리 이름 변경 시 자동 업데이트

### 2. Webhook 이벤트 기록
- `dm_webhook_event` 테이블 추가
- 이벤트 수신 기록, 처리 결과 저장
- 실패한 이벤트 재처리 기능

### 3. 다중 프로젝트 지원
- 동일한 레포지토리가 여러 프로젝트에 등록된 경우 모두 동기화
- `findByOwnerAndName()` 대신 `findAllByOwnerAndName()` 사용

### 4. Rate Limiting
- IP 기반 또는 레포지토리 기반 rate limiting
- Spring Security의 RateLimiter 또는 Redis 기반 솔루션

### 5. Webhook State 검증
- GitHub webhook의 `state` 파라미터 검증 (CSRF 방지)
- 현재는 서명 검증만으로 충분하지만, 추가 보안 레이어로 고려

---

## 파일 변경 사항

### 생성된 파일
- `backend/src/main/java/com/docst/webhook/GitHubWebhookController.java`
- `backend/src/main/java/com/docst/webhook/WebhookService.java`

### 수정된 파일
- `backend/src/main/java/com/docst/repository/RepositoryRepository.java`: `findByOwnerAndName()` 메서드 추가
- `backend/src/main/java/com/docst/config/SecurityConfig.java`: `/api/webhook/**` 추가
- `backend/src/main/resources/application.yml`: webhook 설정 추가
- `.env.example`: `GITHUB_WEBHOOK_SECRET` 추가

---

## 결론

Phase 3-C에서는 GitHub webhook을 통한 자동 동기화 기능을 구현했습니다.

**주요 성과**:
- ✅ HMAC-SHA256 서명 검증을 통한 안전한 webhook 처리
- ✅ Push 이벤트 자동 감지 및 동기화 실행
- ✅ 기본 브랜치만 동기화하여 불필요한 작업 방지
- ✅ 비동기 동기화로 webhook 응답 시간 최소화

**다음 단계**:
- Phase 3-D: 문서 관계 그래프 구현
- Phase 3-E: 권한 체크 AOP 구현
