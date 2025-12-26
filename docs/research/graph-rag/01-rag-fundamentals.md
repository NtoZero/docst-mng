# RAG (Retrieval-Augmented Generation) 기본 개념

## 1. RAG란 무엇인가?

RAG는 **검색(Retrieval)**과 **생성(Generation)**을 결합한 AI 프레임워크입니다.
쉽게 비유하자면, **도서관 사서**와 **이야기꾼**이 협력하는 것과 같습니다:
- 사서가 가장 관련 있는 책을 찾아오고
- 이야기꾼이 그 정보를 바탕으로 답변을 구성합니다

### 핵심 시나리오

```
[사용자 질문] → [검색 시스템에서 관련 문서 찾기] → [LLM이 문서 + 질문으로 답변 생성]
```

**왜 필요한가?**
- LLM은 학습 데이터 이후의 정보를 알지 못함 (지식 단절)
- LLM은 때때로 사실이 아닌 내용을 생성함 (할루시네이션)
- 기업의 내부 데이터는 LLM이 학습하지 않았음

---

## 2. RAG의 핵심 구성요소

### 2.1 문서 준비 단계 (Indexing)

```
[원본 문서] → [청킹(Chunking)] → [임베딩 생성] → [벡터 DB 저장]
```

| 단계 | 설명 | 핵심 포인트 |
|------|------|-------------|
| **청킹** | 문서를 작은 조각으로 분할 | 너무 작으면 맥락 손실, 너무 크면 검색 정확도 저하 |
| **임베딩** | 텍스트를 숫자 벡터로 변환 | 의미적 유사성을 수학적으로 표현 |
| **저장** | 벡터 데이터베이스에 인덱싱 | 빠른 유사도 검색 가능 |

### 2.2 검색 단계 (Retrieval)

```
[사용자 질문] → [질문 임베딩] → [벡터 유사도 검색] → [상위 K개 문서 반환]
```

- **코사인 유사도**: 두 벡터 간 각도를 측정하여 의미적 유사성 판단
- **Top-K 검색**: 가장 유사한 K개의 문서 청크 선택

### 2.3 생성 단계 (Generation)

```
프롬프트 = "다음 컨텍스트를 참고하여 질문에 답하세요:
[검색된 문서들]
질문: [사용자 질문]"
```

LLM은 검색된 문서를 **컨텍스트**로 받아 답변을 생성합니다.

---

## 3. 청킹 전략 (2025 베스트 프랙티스)

### 3.1 주요 청킹 방법

| 전략 | 작동 방식 | 장점 | 단점 |
|------|----------|------|------|
| **고정 크기** | 일정한 토큰/문자 수로 분할 | 구현 간단 | 문맥 경계 무시 |
| **토큰 기반** | tiktoken 등으로 토큰 수 기준 분할 | 모델 제한에 맞춤 | 의미 단위 무시 |
| **시맨틱 청킹** | 의미적 유사도 기반 문장 그룹화 | 높은 일관성 | 계산 비용 높음 |
| **에이전틱 청킹** | LLM이 분할 지점 결정 | 최고 품질 | 가장 비쌈 |

### 3.2 청킹 베스트 프랙티스

**오버랩 권장사항:**
- 10-20% 오버랩이 업계 표준
- 핵심 문장이 청크 경계에서 잘리는 것 방지

**청크 크기 가이드라인:**
- 임베딩 모델 컨텍스트 윈도우에 맞춤 (보통 512~8K 토큰)
- 요약 작업: 큰 청크 (맥락 유지)
- Q&A 작업: 작은 청크 (정밀한 검색)

**표/차트 처리:**
- 분할하지 않고 완전한 단위로 보존
- 별도의 엔티티로 추출하여 무결성 유지

> **핵심 인사이트:** "청킹은 RAG 성능에서 가장 중요한 요소입니다. 완벽한 검색 시스템도 잘못 준비된 데이터 위에서는 실패합니다."
> — [Firecrawl Blog](https://www.firecrawl.dev/blog/best-chunking-strategies-rag-2025)

---

## 4. RAG의 다양한 변형 (2025)

### 4.1 Long RAG
- **문제**: 긴 문서를 작은 청크로 나누면 맥락 손실
- **해결**: 섹션 또는 전체 문서 단위로 처리
- **장점**: 맥락 보존, 검색 효율성 향상

### 4.2 Self-RAG
- **핵심**: 자기 반성(Self-reflection) 메커니즘 내장
- **작동**:
  1. 검색이 필요한지 동적 판단
  2. 검색된 데이터 관련성 평가
  3. 생성된 출력 비판 및 검증

### 4.3 Corrective RAG (CRAG)
- **핵심**: 검색 결과의 품질을 평가하고 수정
- **작동**: 검색 품질이 낮으면 웹 검색 등 대체 소스 활용

### 4.4 Adaptive RAG
- **핵심**: 쿼리 복잡도에 따라 전략 동적 선택
- **예시**: 단순 질문 → 직접 답변, 복잡한 질문 → 다단계 검색

---

## 5. RAG의 한계

### 5.1 단일 홉(Single-hop) 검색의 한계

```
질문: "CEO가 Q4와 Q1 보고서의 인사이트를 바탕으로 도입한 정책은?"

[문제]
- 청크 A: "Q4 보고서에서 비용 절감 필요성 언급"
- 청크 B: "Q1 보고서에서 인력 재배치 제안"
- 청크 C: "CEO가 새 정책 발표"

→ 세 정보가 분리되어 검색됨
→ 관계 추론은 LLM에게 맡겨짐
→ 정확한 답변 어려움
```

### 5.2 맥락 연결 부재
- 관련 정보가 서로 다른 문서에 흩어져 있을 때
- 정보 간의 관계를 파악해야 하는 질문

### 5.3 전역적 이해 부족
- 대규모 데이터셋의 전체적인 테마 파악 어려움
- "이 문서들의 주요 주제는?" 같은 요약 질문에 취약

---

## 6. 평가 지표

| 지표 | 설명 | 측정 대상 |
|------|------|----------|
| **Context Relevancy** | 검색된 정보가 질문과 얼마나 관련있는가 | 검색 품질 |
| **Answer Relevancy** | 생성된 답변이 원래 질문에 얼마나 정확한가 | 답변 품질 |
| **Faithfulness** | 답변이 검색된 컨텍스트에 기반하는가 (할루시네이션 측정) | 신뢰성 |
| **Recall** | 관련 청크를 얼마나 잘 검색하는가 | 검색 완전성 |
| **Precision** | 검색된 청크가 실제로 유용한가 | 검색 정확도 |
| **MRR/NDCG** | 관련 문서의 랭킹 품질 | 순위 품질 |

---

## 7. 업계별 권장사항 (2025)

| 산업 | 권장 접근법 | 이유 |
|------|------------|------|
| **헬스케어** | Long RAG + Self-RAG | 긴 의료 문서의 맥락 + 정확성 중요 |
| **이커머스** | 하이브리드 검색 + 실시간 API | 재고/가격 실시간 반영 필요 |
| **금융** | HybridRAG (그래프 + 벡터) | 복잡한 관계 추론 필요 |
| **엔터프라이즈** | 하이브리드 검색으로 시작 → GraphRAG 확장 | 점진적 복잡도 증가 |

---

## 8. 시장 전망

RAG 시장은 **2035년까지 403.4억 달러** 규모로 성장 예상 (연평균 35% 성장).
이는 AI 할루시네이션 문제 해결과 콘텐츠 관련성 향상에서의 핵심 역할을 반영합니다.

---

## 출처

### 핵심 자료
- [Eden AI - The 2025 Guide to RAG](https://www.edenai.co/post/the-2025-guide-to-retrieval-augmented-generation-rag)
- [Firecrawl - Best Chunking Strategies for RAG 2025](https://www.firecrawl.dev/blog/best-chunking-strategies-rag-2025)
- [Databricks - Mastering Chunking Strategies for RAG](https://community.databricks.com/t5/technical-blog/the-ultimate-guide-to-chunking-strategies-for-rag-applications/ba-p/113089)

### 학술 논문
- [arXiv 2501.07391 - Enhancing RAG: A Study of Best Practices](https://arxiv.org/abs/2501.07391)
- [ACL Anthology - COLING 2025 RAG Best Practices](https://aclanthology.org/2025.coling-main.449.pdf)

### 기업 블로그
- [NVIDIA - Finding the Best Chunking Strategy](https://developer.nvidia.com/blog/finding-the-best-chunking-strategy-for-accurate-ai-responses/)
- [Weaviate - Chunking Strategies for RAG](https://weaviate.io/blog/chunking-strategies-for-rag)
- [Unstructured - Chunking for RAG Best Practices](https://unstructured.io/blog/chunking-for-rag-best-practices)
- [Stack Overflow - Breaking up is hard to do: Chunking in RAG](https://stackoverflow.blog/2024/12/27/breaking-up-is-hard-to-do-chunking-in-rag-applications/)
- [IBM - Chunking Strategies for RAG Tutorial](https://www.ibm.com/think/tutorials/chunking-strategies-for-rag-with-langchain-watsonx-ai)

### 온라인 가이드
- [Prompt Engineering Guide - RAG for LLMs](https://www.promptingguide.ai/research/rag)
- [Analytics Vidhya - 8 Types of Chunking](https://www.analyticsvidhya.com/blog/2025/02/types-of-chunking-for-rag-systems/)
