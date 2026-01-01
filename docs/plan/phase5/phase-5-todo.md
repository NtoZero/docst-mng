# Phase 5: TODO 및 개선 사항

> **작성일**: 2026-01-01
> **현재 상태**: MVP 완료, 개선 작업 진행 중

---

## 즉시 해결 필요 (Critical)

### 백엔드

- [ ] **사용자 인증 통합**
  - 현재: 더미 사용자 UUID 사용 (`00000000-0000-0000-0000-000000000000`)
  - 목표: SecurityContext에서 실제 JWT 사용자 정보 가져오기
  - 파일: `McpToolDispatcher.java:227, 235`
  - 우선순위: 🔴 High

- [ ] **권한 검사 테스트**
  - 단위 테스트: `PermissionServiceTest`
  - 통합 테스트: EDITOR/VIEWER 역할별 접근 제어
  - SecurityException 발생 시나리오
  - 우선순위: 🔴 High

### 프론트엔드

- [ ] **프로젝트 선택기**
  - Playground에서 프로젝트 선택 UI 추가
  - 현재: 프로젝트 ID 하드코딩 없음
  - 우선순위: 🟠 Medium

---

## Phase 5 개선 사항 (High Priority)

### 백엔드

#### 1. 동시성 제어

- [ ] **Optimistic Locking**
  - Document 엔티티에 `@Version` 추가
  - ETag 기반 충돌 감지
  - 파일: `Document.java`, `DocumentWriteService.java`
  - 우선순위: 🟠 Medium

```java
@Entity
public class Document {
    @Version
    private Long version;

    // ETag 생성
    public String getETag() {
        return String.format("\"%d\"", version);
    }
}
```

#### 2. 테스트 작성

- [ ] **GitWriteService 테스트**
  - `writeFile()`: 파일 생성, 부모 디렉토리 자동 생성
  - `commitFile()`: Git 커밋, author 확인
  - `pushToRemote()`: Mock credential 사용
  - 파일: `GitWriteServiceTest.java`

- [ ] **DocumentWriteService 테스트**
  - `createDocument()`: 권한 검사, 파일 생성, 동기화
  - `updateDocument()`: Chunk & Embedding 업데이트 확인
  - `pushToRemote()`: 성공/실패 케이스
  - 파일: `DocumentWriteServiceTest.java`

- [ ] **통합 테스트**
  - MCP create_document → sync → DB 확인
  - MCP update_document → chunk 재생성 확인
  - MCP push_to_remote → 원격 커밋 확인

#### 3. 에러 처리

- [ ] **사용자 친화적 에러 메시지**
  - 권한 부족: "You don't have permission to edit this document"
  - 파일 존재: "Document already exists at this path"
  - Git 오류: "Failed to push: authentication required"

- [ ] **Retry 로직**
  - Git push 실패 시 재시도 (최대 3회)
  - 네트워크 오류 처리

#### 4. 보안 강화

- [ ] **경로 탐색 공격 방지**
  - 파일 경로 정규화 (`Path.normalize()`)
  - Git 레포지토리 외부 쓰기 차단
  - 파일: `GitWriteService.java:writeFile()`

```java
public void writeFile(Path filePath, String content) throws IOException {
    // 경로 정규화
    Path normalized = filePath.normalize();

    // 레포지토리 외부 쓰기 차단
    if (!normalized.startsWith(getLocalPath(repo))) {
        throw new SecurityException("Path traversal attack detected");
    }

    // ...
}
```

- [ ] **커밋 메시지 Sanitization**
  - XSS 방지 (HTML 태그 제거)
  - 길이 제한 (최대 500자)
  - 파일: `GitWriteService.java:commitFile()`

- [ ] **Rate Limiting**
  - MCP 쓰기 도구 제한 (사용자별, 10 req/min)
  - Spring `@RateLimiter` 사용
  - Redis 기반 분산 Rate Limiting

### 프론트엔드

#### 1. UI/UX 개선

- [ ] **Tool Call 시각화 개선**
  - JSON Syntax Highlighting (react-json-view)
  - 접기/펴기 기능
  - Copy to Clipboard 버튼
  - 파일: `message-item.tsx`

- [ ] **문서 미리보기**
  - 사이드바에 get_document 결과 표시
  - Markdown 렌더링 (react-markdown)
  - 파일: `components/playground/document-preview.tsx`

- [ ] **MCP Connection Status**
  - 연결됨/끊김 표시 (Badge)
  - 재연결 버튼
  - SSE 이벤트 리스너
  - 파일: `components/playground/mcp-connection-status.tsx`

#### 2. 상태 관리

- [ ] **대화 세션 저장**
  - LocalStorage에 메시지 히스토리 저장
  - 새로고침 시에도 유지
  - Clear 버튼으로 삭제
  - 파일: `hooks/use-mcp-tools.ts`

```typescript
useEffect(() => {
  // 저장
  localStorage.setItem('playground-messages', JSON.stringify(messages));
}, [messages]);

useEffect(() => {
  // 복원
  const saved = localStorage.getItem('playground-messages');
  if (saved) setMessages(JSON.parse(saved));
}, []);
```

#### 3. 다국어 지원

- [ ] **Playground i18n**
  - 현재: 영어만
  - 목표: 한국어/영어
  - 파일: `messages/en.json`, `messages/ko.json`

---

## Phase 6: LLM 통합 (Next Priority)

### 1. LLM API 연동

- [ ] **OpenAI API**
  - GPT-4 Function Calling
  - MCP Tools를 Function Schema로 변환
  - 스트리밍 응답 지원

- [ ] **Anthropic API**
  - Claude 3.5 Tool Use
  - MCP Tools 자동 호출
  - 대화 컨텍스트 관리

### 2. 프론트엔드 LLM 통합

- [ ] **use-llm-chat Hook**
  - LLM API 호출
  - Function Call → MCP Tools 매핑
  - 스트리밍 응답 처리
  - 파일: `hooks/use-llm-chat.ts`

- [ ] **실제 대화 시나리오**
  - "README.md에 설치 방법 추가해줘"
  - "architecture 문서 찾아줘"
  - "모든 변경사항을 커밋하고 푸시해줘"

### 3. 고급 기능

- [ ] **Branch 관리 UI**
  - 브랜치 선택/생성/전환
  - PR 생성 (GitHub API)

- [ ] **Conflict 해결**
  - 충돌 감지 UI
  - 3-way merge 인터페이스

- [ ] **대화 세션 관리**
  - 세션 목록
  - 세션 저장/불러오기
  - 세션 공유 (URL)

- [ ] **Tool Call 템플릿**
  - "모든 README 검색"
  - "문서 생성 후 커밋"
  - 자주 사용하는 작업 템플릿

---

## 기술 부채 (Technical Debt)

### 백엔드

- [ ] **McpTool Enum JSON Schema 생성**
  - 현재: 간단한 스키마만 반환
  - 목표: Reflection 기반 자동 생성
  - 또는: Jackson JSON Schema Generator 사용

- [ ] **커밋 메시지 포맷팅**
  - Conventional Commits 지원
  - `feat:`, `fix:`, `docs:` prefix
  - 파일: `GitWriteService.java`

### 프론트엔드

- [ ] **에러 바운더리**
  - Playground 컴포넌트 에러 캐치
  - 사용자 친화적 에러 페이지

- [ ] **로딩 상태 개선**
  - Skeleton UI
  - Progress Indicator

---

## 모니터링 & 디버깅

- [ ] **MCP Tools 대시보드**
  - 도구별 사용 통계
  - 성공/실패율
  - 평균 실행 시간

- [ ] **디버그 모드**
  - Raw JSON-RPC 요청/응답 표시
  - Tool Call 타임라인
  - Network Inspector

- [ ] **로깅 개선**
  - Structured Logging (JSON)
  - MDC에 사용자 ID, 도구 이름 추가
  - ELK Stack 연동

---

## 우선순위 요약

### 🔴 High (즉시)

1. 사용자 인증 통합
2. 권한 검사 테스트
3. 경로 탐색 공격 방지

### 🟠 Medium (1-2주)

1. Optimistic Locking
2. 단위/통합 테스트 작성
3. Tool Call 시각화 개선
4. 문서 미리보기

### 🟡 Low (Phase 6)

1. LLM API 연동
2. Branch 관리 UI
3. 대화 세션 관리
4. 모니터링 대시보드

---

## 참고 자료

- [Phase 5 구현 완료 보고서](../../impl/phase-5-implementation.md)
- [Phase 5 계획서](README.md)
- [Backend 계획](backend-plan.md)
- [Frontend 계획](frontend-plan.md)
