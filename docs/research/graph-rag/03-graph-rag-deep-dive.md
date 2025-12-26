# Graph RAG 심층 분석

## 1. Graph RAG 개요

### 1.1 정의

Graph RAG는 **지식 그래프(Knowledge Graph)**를 활용하여 검색 단계를 수행하는 RAG 시스템입니다. 단순히 유사한 텍스트를 찾는 대신, 엔티티 간의 **관계를 탐색**하여 더 깊은 맥락적 이해를 제공합니다.

> "GraphRAG는 RAG에 대한 구조화되고 계층적인 접근 방식으로, 단순한 텍스트 스니펫을 사용하는 순진한 시맨틱 검색 방식과 대조됩니다."
> — [Microsoft GraphRAG](https://microsoft.github.io/graphrag/)

### 1.2 핵심 아이디어

```
[핵심 질문]
"여러 문서에 흩어진 정보를 어떻게 연결해서 이해할 수 있을까?"

[해결책]
텍스트에서 엔티티(사람, 조직, 개념 등)와 관계를 추출하여
지식 그래프로 구조화 → 그래프 탐색으로 연결된 정보 검색
```

---

## 2. Microsoft GraphRAG 아키텍처

### 2.1 전체 파이프라인

```
┌─────────────────────────────────────────────────────────────────┐
│                    INDEXING PHASE                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  [원본 문서]                                                     │
│       ↓                                                         │
│  [TextUnits 분할] ─ 분석 가능한 단위로 문서 분할                   │
│       ↓                                                         │
│  [엔티티/관계 추출] ─ LLM이 엔티티, 관계, 주요 클레임 추출          │
│       ↓                                                         │
│  [지식 그래프 구축] ─ 노드(엔티티)와 엣지(관계)로 그래프 생성       │
│       ↓                                                         │
│  [커뮤니티 탐지] ─ 관련 엔티티들을 그룹으로 클러스터링             │
│       ↓                                                         │
│  [계층적 요약] ─ 각 커뮤니티에 대한 요약 생성                     │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                    QUERY PHASE                                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  [사용자 질문]                                                   │
│       ↓                                                         │
│  [로컬 검색] 또는 [글로벌 검색]                                   │
│       ↓                                                         │
│  [관련 엔티티/커뮤니티 탐색]                                      │
│       ↓                                                         │
│  [구조화된 컨텍스트 구성]                                         │
│       ↓                                                         │
│  [LLM 답변 생성]                                                 │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 각 단계 상세 설명

#### 1단계: TextUnits 생성

```
[원본 문서]
"삼성전자 이재용 회장은 2024년 반도체 투자를 발표했다.
 이 투자는 AI 칩 생산 확대를 목표로 한다."

[TextUnits]
├── TextUnit 1: "삼성전자 이재용 회장은 2024년 반도체 투자를 발표했다."
└── TextUnit 2: "이 투자는 AI 칩 생산 확대를 목표로 한다."
```

- 분석 가능한 최소 단위로 분할
- 나중에 답변의 세밀한 참조(출처)로 사용

#### 2단계: 엔티티 및 관계 추출

```
[LLM 프롬프트 예시]
"다음 텍스트에서 엔티티와 관계를 추출하세요:
 - 엔티티: 사람, 조직, 장소, 이벤트, 개념
 - 관계: 두 엔티티 간의 연결"

[추출 결과]
엔티티:
├── 삼성전자 (조직)
├── 이재용 (인물)
├── 반도체 투자 (이벤트)
└── AI 칩 (제품)

관계:
├── 이재용 --[회장]--> 삼성전자
├── 삼성전자 --[발표]--> 반도체 투자
└── 반도체 투자 --[목표]--> AI 칩 생산
```

#### 3단계: 지식 그래프 구축

```
                    ┌──────────────┐
                    │   삼성전자    │
                    │  (Organization)│
                    └───────┬──────┘
                            │
              ┌─────────────┼─────────────┐
              │ 회장         │ 발표         │ 사업
              ▼             ▼             ▼
        ┌──────────┐  ┌──────────┐  ┌──────────┐
        │  이재용   │  │반도체 투자│  │ AI 칩    │
        │ (Person) │  │ (Event)  │  │(Product) │
        └──────────┘  └────┬─────┘  └──────────┘
                           │
                           │ 목표
                           ▼
                     ┌──────────┐
                     │생산 확대  │
                     │ (Goal)   │
                     └──────────┘
```

#### 4단계: 커뮤니티 탐지

```
[Leiden 알고리즘 등을 사용한 클러스터링]

Level 0 (최상위 테마):
├── 커뮤니티 A: "한국 대기업 반도체 전략"
│   └── 삼성전자, 이재용, SK하이닉스, 최태원...
└── 커뮤니티 B: "글로벌 AI 하드웨어 경쟁"
    └── NVIDIA, AMD, AI 칩, GPU...

Level 1 (세부 주제):
├── 커뮤니티 A-1: "삼성 반도체 사업"
├── 커뮤니티 A-2: "SK하이닉스 HBM"
└── ...
```

#### 5단계: 커뮤니티 요약 생성

```
[커뮤니티 A 요약]
"이 커뮤니티는 한국 대기업의 반도체 전략을 다룹니다.
 삼성전자 이재용 회장과 SK하이닉스가 주요 엔티티이며,
 AI 칩 생산 확대가 공통 목표로 나타납니다..."
```

---

## 3. 검색 모드: Local vs Global

### 3.1 Local Search (로컬 검색)

**용도:** 특정 엔티티에 대한 구체적인 질문

```
질문: "이재용 회장이 발표한 투자 계획은?"

[프로세스]
1. 질문에서 핵심 엔티티 식별: "이재용"
2. 그래프에서 "이재용" 노드 탐색
3. 연결된 엔티티와 관계 수집 (1-2 hop)
4. 관련 TextUnits 매핑
5. 컨텍스트 구성 → LLM 답변

[결과 컨텍스트]
- 이재용 --[회장]--> 삼성전자
- 삼성전자 --[발표]--> 반도체 투자
- 반도체 투자 --[규모]--> 300조원
- 관련 TextUnit 참조
```

### 3.2 Global Search (글로벌 검색)

**용도:** 데이터셋 전체에 대한 요약/분석 질문

```
질문: "이 문서들의 주요 테마와 트렌드는?"

[프로세스]
1. 최상위 레벨 커뮤니티 요약 수집
2. 각 커뮤니티의 핵심 테마 통합
3. 계층적 요약을 컨텍스트로 구성
4. LLM이 전역적 답변 생성

[결과]
"문서 컬렉션에서 세 가지 주요 테마가 식별됩니다:
1. 한국 대기업의 반도체 투자 전략
2. 글로벌 AI 하드웨어 경쟁
3. 공급망 재편..."
```

### 3.3 비교 요약

| 측면 | Local Search | Global Search |
|------|--------------|---------------|
| **질문 유형** | 특정 엔티티/관계 | 전체 요약/테마 |
| **탐색 범위** | 관련 노드 주변 | 전체 커뮤니티 계층 |
| **비용** | 상대적 낮음 | 상대적 높음 |
| **예시** | "X의 CEO는?" | "주요 트렌드는?" |

---

## 4. 엔티티 추출 방법론

### 4.1 LLM 기반 추출

```python
# 개념적 예시
prompt = """
다음 텍스트에서 엔티티와 관계를 JSON 형식으로 추출하세요:

텍스트: {text}

출력 형식:
{
  "entities": [
    {"name": "...", "type": "...", "description": "..."}
  ],
  "relationships": [
    {"source": "...", "target": "...", "type": "...", "description": "..."}
  ]
}
"""
```

**장점:**
- 범용적 추출 가능
- 복잡한 관계도 이해

**단점:**
- 비용이 높음
- 일관성 문제 (같은 텍스트에서 다른 결과)

### 4.2 전문 NLP 도구

| 도구 | 용도 | 특징 |
|------|------|------|
| **GliNER** | 엔티티 추출 | 경량, 도메인 특화 가능 |
| **ReLiK** | 관계 추출 | 빠른 속도 |
| **spaCy NER** | 명명 엔티티 인식 | 안정적, 다국어 지원 |

### 4.3 하이브리드 접근

```
1. 먼저 NLP 도구로 기본 엔티티 추출
2. LLM으로 복잡한 관계 및 암시적 연결 보강
3. 후처리로 중복 제거 및 정규화
```

---

## 5. 도전과제와 해결책

### 5.1 엔티티 해소 (Entity Resolution)

**문제:** 같은 이름, 다른 엔티티

```
"Apple은 혁신적인 제품을 만든다" (Apple Inc.? 과일?)
"김철수 대표가 발표했다" (어느 김철수?)
```

**현재 한계:**
- Microsoft GraphRAG는 주로 이름 기반 매칭
- 동명이인/동음이의어 구분 어려움

**해결 방향:**
- 컨텍스트 기반 disambiguation
- 도메인 특화 온톨로지 활용
- 임베딩 기반 유사도 + 규칙 결합

### 5.2 인덱싱 비용

**문제:**
- 엔티티 추출, 임베딩 생성, 커뮤니티 탐지, 요약 생성에 상당한 토큰 소모
- 대규모 문서에서 비용 폭증

**해결책: LazyGraphRAG**

```
[기존 GraphRAG]
모든 문서 → 전체 사전 처리 → 고비용 인덱싱 → 쿼리 시 빠른 검색

[LazyGraphRAG]
최소 인덱싱 → 쿼리 시 필요한 부분만 동적 처리
- 인덱싱 비용: Vector RAG와 동일
- 전체 GraphRAG 대비 0.1% 비용
```

### 5.3 업데이트 문제

**문제:** 새 문서 추가 시 전체 리인덱싱 필요할 수 있음

**해결 방향:**
- 증분 업데이트 지원
- 영향받는 커뮤니티만 재계산
- 동적 그래프 구조 활용

---

## 6. Neo4j GraphRAG 패키지 (2025)

### 6.1 핵심 기능

Neo4j의 공식 GraphRAG Python 패키지는 다음을 지원합니다:

```python
# 설치
pip install "neo4j_graphrag[openai]"

# 주요 기능
- 지식 그래프 생성 파이프라인
- 다양한 검색기 (그래프, 벡터, 하이브리드)
- Text-to-Cypher 쿼리 생성
- 전체 RAG 파이프라인 구축
```

### 6.2 검색 방법

| 검색 유형 | 설명 | 사용 시기 |
|----------|------|----------|
| **그래프 순회** | 노드 간 관계 따라 탐색 | 관계 기반 질문 |
| **벡터 검색** | 임베딩 유사도 검색 | 시맨틱 유사도 필요 |
| **Text-to-Cypher** | 자연어를 Cypher 쿼리로 변환 | 구조화된 질문 |
| **하이브리드** | 벡터 + 그래프 결합 | 최대 커버리지 |

### 6.3 성능 최적화

- **k-hop 제한:** 탐색 깊이 제한으로 관련 이웃만 검색
- **경로 제한:** 최단 경로 또는 특정 관계 유형만 탐색
- **벡터 + 구조 점수:** 시맨틱 유사도와 그래프 구조 결합

---

## 7. 구현 시나리오

### 7.1 금융 문서 분석

```
[시나리오]
수천 개의 10-K 보고서에서 기업 간 관계 및 리스크 분석

[Graph RAG 접근]
1. 기업, 임원, 제품, 리스크 요인을 엔티티로 추출
2. "공급한다", "경쟁한다", "투자했다" 등 관계 매핑
3. 산업별 커뮤니티 형성
4. "X사의 공급망 리스크 요인은?" 같은 질문에 Multi-hop 답변

[효과]
- 관계 추적 가능한 분석
- 숨겨진 의존성 발견
- 규제 준수 감사 추적
```

### 7.2 기술 문서 지식 베이스

```
[시나리오]
대규모 기술 문서에서 개념 간 관계 이해

[Graph RAG 접근]
1. API, 함수, 모듈, 의존성을 엔티티로 추출
2. "호출한다", "상속한다", "의존한다" 관계 매핑
3. "X 모듈을 변경하면 영향받는 곳은?" 질문 처리

[효과]
- 의존성 분석 자동화
- 변경 영향 범위 파악
- 온보딩 시간 단축
```

---

## 8. 2025년 발전 동향

### 8.1 LazyGraphRAG

Microsoft의 새로운 접근법으로, 사전 요약 없이 쿼리 시점에 처리:
- 초기 비용 대폭 절감
- 실시간 업데이트 용이
- 기존 품질 유지

### 8.2 RAKG (Document-level RAG + KG)

- 문서 수준에서 사전 엔티티 추출
- RAG를 활용해 코어퍼런스 해결
- 장문맥 망각 문제 해결

### 8.3 LightRAG

- 경량화된 Graph RAG 구현
- 멀티모달 지식 그래프 지원
- 자동 엔티티 추출
- 하이브리드 지능형 검색

---

## 출처

### 공식 문서
- [Microsoft GraphRAG 공식 문서](https://microsoft.github.io/graphrag/)
- [Microsoft GraphRAG GitHub](https://github.com/microsoft/graphrag)
- [Neo4j GraphRAG Python 문서](https://neo4j.com/docs/neo4j-graphrag-python/current/user_guide_rag.html)

### Microsoft Research
- [GraphRAG: Unlocking LLM Discovery](https://www.microsoft.com/en-us/research/blog/graphrag-unlocking-llm-discovery-on-narrative-private-data/)
- [LazyGraphRAG: New Standard for Quality and Cost](https://www.microsoft.com/en-us/research/blog/lazygraphrag-setting-a-new-standard-for-quality-and-cost/)
- [GraphRAG: New Tool for Complex Data Discovery](https://www.microsoft.com/en-us/research/blog/graphrag-new-tool-for-complex-data-discovery-now-on-github/)

### 기업 블로그
- [Neo4j - Knowledge Graph Extraction Challenges](https://neo4j.com/blog/developer/knowledge-graph-extraction-challenges/)
- [Neo4j - RAG Tutorial](https://neo4j.com/blog/developer/rag-tutorial/)
- [Weaviate - Exploring RAG and GraphRAG](https://weaviate.io/blog/graph-rag)
- [Elastic - Graph RAG & Elasticsearch](https://www.elastic.co/search-labs/blog/rag-graph-traversal)
- [Memgraph - Microsoft GraphRAG with Graph Databases](https://memgraph.com/blog/how-microsoft-graphrag-works-with-graph-databases)
- [Kuzu - LLMs in Each Stage of Graph RAG](https://blog.kuzudb.com/post/llms-in-each-stage-of-a-graph-rag-chatbot/)
- [Zep - LLM RAG Knowledge Graphs](https://blog.getzep.com/llm-rag-knowledge-graphs-faster-and-more-dynamic/)

### 학술 자료
- [arXiv - RAKG: Document-level RAG KG Construction](https://arxiv.org/html/2504.09823v1)
- [arXiv - LLM-Powered Knowledge Graphs](https://arxiv.org/html/2503.07993v1)
- [LightRAG GitHub (EMNLP 2025)](https://github.com/HKUDS/LightRAG)

### 튜토리얼
- [LlamaIndex - Knowledge Graph RAG Query Engine](https://docs.llamaindex.ai/en/stable/examples/query_engine/knowledge_graph_rag_query_engine/)
- [DataCamp - Knowledge Graph RAG Tutorial](https://www.datacamp.com/tutorial/knowledge-graph-rag)
- [Medium - GraphRAG Tutorial Neo4j + LLMs](https://medium.com/@daniel.puenteviejo/graphrag-tutorial-neo4j-llms-47372b71e3fa)
