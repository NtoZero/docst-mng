# Phase 6: LLM 통합 및 고급 기능

> **작성일**: 2026-01-01
> **전제 조건**: Phase 5 완료 (MCP Tools, Playground UI)
> **목표**: AI 에이전트가 자연어로 문서를 관리하고, 고급 Git 워크플로우 지원

---

## 개요

Phase 5에서 구축한 MCP Tools와 Playground를 기반으로, 실제 LLM을 통합하여 자연어 대화로 문서를 관리할 수 있는 시스템을 구현합니다.

### 현재 상태 (Phase 5)
- MCP Tools: 9개 도구 (READ 6개 + WRITE 3개)
- JSON-RPC 2.0 Transport (HTTP + SSE)
- Playground UI: 직접 도구 호출 (MVP)
- LLM 통합: 아직 없음 (사용자가 직접 명령어 입력)

### Phase 6 목표
- **LLM 통합 (Spring AI 1.1.0+)**: Provider 독립적 ChatClient API
- **자연어 처리**: "README에 설치 방법 추가해줘"
- **자동 Tool 호출**: LLM이 필요한 MCP Tools 자동 선택
- **대화 컨텍스트**: 이전 대화 기억 및 연속 작업
- **고급 Git**: Branch 관리, Conflict 해결, PR 생성

---

## 핵심 변경사항 (2026-01 업데이트)

### Spring AI 1.1.0+ Core 기반 구현

**기존 계획에서 변경된 사항:**

| 기존 계획 | 변경된 계획 |
|----------|------------|
| OpenAI SDK 직접 호출 | Spring AI `ChatClient` 통합 API |
| Anthropic SDK 직접 호출 | Spring AI `ChatClient` 통합 API |
| 각 Provider별 Client 클래스 | 단일 `ChatClient` + 설정으로 Provider 전환 |
| `FunctionCallback` (레거시) | `@Tool` + `ToolCallback` (최신 API) |
| 프론트엔드 직접 LLM 호출 | 백엔드 프록시 패턴 |

### FunctionCallback → ToolCallback 마이그레이션

Spring AI 1.0.0-M6부터 "Function Calling" 용어가 "Tool Calling"으로 변경되었습니다:

| 레거시 API | 최신 API |
|-----------|----------|
| `FunctionCallback` | `ToolCallback` |
| `FunctionCallback.builder().function()` | `FunctionToolCallback.builder()` |
| `ChatClient.functions()` | `ChatClient.tools()` |
| `FunctionCallingOptions` | `ToolCallingChatOptions` |

---

## 아키텍처

```
Frontend (Next.js)
    │
    │ POST /api/llm/chat (SSE)
    ▼
Backend (Spring Boot + Spring AI 1.1.0+)
    │
    ├── LlmController
    │       │
    │       ▼
    ├── LlmService
    │       │
    │       ▼
    ├── ChatClient (Provider 독립)
    │       │
    │       ├──> OpenAI GPT-4o
    │       ├──> Anthropic Claude
    │       └──> Ollama (Local)
    │
    └── @Tool Classes
            │
            ├── DocumentTools
            ├── SearchTools
            └── GitTools
```

### 백엔드 프록시 패턴

**장점:**
1. **보안**: API Key가 클라이언트에 노출되지 않음
2. **비용 관리**: 서버에서 Rate Limiting, 사용량 추적 가능
3. **Provider 독립**: 설정만으로 OpenAI ↔ Anthropic ↔ Ollama 전환
4. **Tool 실행**: 서버에서 직접 MCP Tools 실행 (네트워크 왕복 감소)

---

## 구현 로드맵

### Week 1-2: 백엔드 기초
- [ ] Spring AI 의존성 추가 (1.1.0+)
- [ ] LlmConfig (ChatClient Bean)
- [ ] LlmService (chat, streamChat)
- [ ] @Tool 어노테이션 클래스 구현
- [ ] LlmController (REST API)

### Week 3-4: 프론트엔드 통합
- [ ] llm-api.ts (백엔드 프록시 클라이언트)
- [ ] use-llm-chat Hook
- [ ] ChatInterface 컴포넌트
- [ ] StreamingMessage 컴포넌트

### Week 5-6: 고급 기능
- [ ] Branch 관리 (List, Create, Switch)
- [ ] 세션 관리 (ChatMemory)
- [ ] 템플릿 시스템
- [ ] Rate Limiting

### Week 7-8: 테스트 & 마무리
- [ ] 단위/통합 테스트
- [ ] E2E 테스트
- [ ] 문서화
- [ ] 성능 최적화

---

## 세부 계획 문서

- [Backend 구현 계획](./backend-plan.md) - Spring AI ChatClient, @Tool, LlmService
- [Frontend 구현 계획](./frontend-plan.md) - 백엔드 프록시 패턴, use-llm-chat Hook

---

## 참고 자료

### Spring AI 공식 문서
- [ChatClient API](https://docs.spring.io/spring-ai/reference/api/chatclient.html)
- [Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html)
- [FunctionCallback → ToolCallback 마이그레이션](https://docs.spring.io/spring-ai/reference/api/tools-migration.html)

### 릴리스 노트
- [Spring AI 1.0 GA (2025-05)](https://spring.io/blog/2025/05/20/spring-ai-1-0-GA-released/)
- [Spring AI 1.1.1 (2025-12)](https://spring.io/blog/2025/12/05/spring-ai-1-1-1-available-now/)

### 관련 Phase
- [Phase 5 구현](../../impl/phase-5-implementation.md) - MCP Tools, Playground MVP

---

## 다음 단계

Phase 6 완료 후:
- **Phase 7**: Multi-tenant 지원, 팀 협업
- **Phase 8**: Advanced RAG (Hybrid Search 고도화, Re-ranking)
- **Phase 9**: 모니터링 & 분석 (사용 패턴, 비용 분석)