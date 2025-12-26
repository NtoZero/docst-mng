# Hybrid RAG: 벡터 검색과 지식 그래프의 결합

## 1. Hybrid RAG란?

### 1.1 정의

Hybrid RAG는 **벡터 검색(Vector RAG)**과 **지식 그래프 검색(Graph RAG)**을 결합하여 각각의 장점을 활용하는 접근 방식입니다.

```
[핵심 아이디어]
벡터 검색: "의미적으로 비슷한 것 찾기" (What)
그래프 검색: "관계와 연결 탐색하기" (How/Why)

Hybrid = 두 가지를 결합하여 최적의 검색 결과
```

### 1.2 왜 하이브리드인가?

| 접근법 | 강점 | 약점 |
|--------|------|------|
| **Vector RAG** | 시맨틱 유사도, 비구조화 텍스트 | 관계 추론 어려움 |
| **Graph RAG** | 관계 탐색, Multi-hop 추론 | 구축 비용, 업데이트 어려움 |
| **Hybrid RAG** | 두 강점 결합 | 구현 복잡성 증가 |

> "벡터 검색과 지식 그래프를 결합함으로써, 검색 시스템은 시맨틱 의미와 구조화된 관계를 모두 포착할 수 있어 RAG가 훨씬 더 정확하고 신뢰할 수 있게 됩니다."
> — [HackerNoon](https://hackernoon.com/stop-relying-on-vector-search-alone-build-a-hybrid-rag-system-with-knowledge-graphs-and-local-llms)

---

## 2. Hybrid RAG 아키텍처

### 2.1 기본 파이프라인

```
┌─────────────────────────────────────────────────────────────────┐
│                    데이터 인덱싱                                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  [원본 문서]                                                     │
│       │                                                         │
│       ├──────────────────┬──────────────────┐                   │
│       ▼                  ▼                  ▼                   │
│  [청킹 & 임베딩]    [엔티티 추출]      [관계 추출]                │
│       │                  │                  │                   │
│       ▼                  └────────┬─────────┘                   │
│  [벡터 DB]                        ▼                             │
│                           [지식 그래프 DB]                       │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                    하이브리드 검색                                │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  [사용자 질문]                                                   │
│       │                                                         │
│       ├──────────────────┬──────────────────┐                   │
│       ▼                  ▼                  │                   │
│  [벡터 유사도 검색]  [그래프 순회 검색]       │                   │
│       │                  │                  │                   │
│       └────────┬─────────┘                  │                   │
│                ▼                            │                   │
│       [결과 융합 & 리랭킹]                    │                   │
│                │                            │                   │
│                ▼                            │                   │
│       [통합 컨텍스트]                         │                   │
│                │                            │                   │
│                ▼                            │                   │
│           [LLM 답변 생성]                    │                   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 검색 전략

#### 전략 1: 병렬 검색 후 융합

```
질문: "삼성전자의 AI 반도체 전략과 경쟁사 대비 강점은?"

[벡터 검색]
→ "삼성전자 AI 반도체..." 유사 청크 Top-5

[그래프 검색]
→ 삼성전자 --[개발]--> AI 칩 --[경쟁]--> NVIDIA
→ 삼성전자 --[강점]--> HBM 기술

[융합]
→ 벡터 결과 + 그래프 경로 = 종합 컨텍스트
```

#### 전략 2: 순차적 검색 (넓게 → 좁게)

```
[1단계: 벡터 검색으로 후보군 확보]
질문 임베딩 → 관련 문서 청크 Top-20

[2단계: 그래프 검색으로 정제]
후보 청크의 엔티티 → 그래프에서 관계 탐색 → 관련성 높은 것만 필터

[3단계: 최종 컨텍스트 구성]
정제된 결과 + 그래프 경로 정보
```

#### 전략 3: 동적 전략 선택

```python
def select_strategy(query):
    if is_entity_focused(query):
        # "X의 CEO는?" → 그래프 우선
        return "graph_first"
    elif is_relationship_query(query):
        # "X와 Y의 관계는?" → 그래프 중심 + 벡터 보강
        return "graph_centric"
    elif is_semantic_query(query):
        # "최신 AI 트렌드는?" → 벡터 우선
        return "vector_first"
    else:
        # 병렬 처리
        return "parallel_fusion"
```

---

## 3. 결과 융합 방법

### 3.1 점수 기반 융합

```
최종_점수 = α × 벡터_유사도 + β × 그래프_관련성 + γ × 구조적_중요도

여기서:
- α, β, γ: 가중치 (합 = 1)
- 벡터_유사도: 코사인 유사도 (0~1)
- 그래프_관련성: 쿼리 엔티티와의 홉 거리 역수
- 구조적_중요도: PageRank, 중심성 등
```

### 3.2 리랭킹 기반 융합

```
1. 벡터 검색 결과: [A, B, C, D, E]
2. 그래프 검색 결과: [C, F, A, G]
3. 초기 융합: [A, C, B, F, D, E, G]
4. Cross-encoder로 리랭킹: [C, A, F, B, ...]
```

### 3.3 LLM 기반 융합

```
[프롬프트]
"다음 두 소스의 정보를 종합하여 질문에 답하세요:

벡터 검색 결과:
{vector_results}

그래프 검색 결과 (관계 포함):
{graph_results}

질문: {query}"
```

---

## 4. 실제 사례 연구

### 4.1 Cedars-Sinai 알츠하이머 지식 베이스 (AlzKB)

**배경:**
- 의료 연구 기관의 알츠하이머 연구 데이터 통합
- 유전자, 약물, 질병 간 복잡한 관계 존재

**구현:**
```
[Memgraph 그래프 DB]
- 바이오메디컬 엔티티: 유전자, 약물, 질병
- 관계: "억제한다", "유발한다", "치료한다"

[벡터 DB]
- 연구 논문, 임상 노트 임베딩
- 시맨틱 유사도 검색

[Hybrid 효과]
- 쿼리 정확도 향상
- ML 모델 성능 개선
- 새로운 연구 가설 발견 지원
```

### 4.2 금융 10-K 보고서 분석

**배경:**
- 수천 개의 기업 연례 보고서 분석
- 복잡한 기업 간 관계, 리스크 요인 파악 필요

**구현:**
```
[Graph DB]
- 기업 --[투자]--> 기업
- 기업 --[공급]--> 기업
- 기업 --[리스크]--> 요인

[Vector DB]
- 재무 섹션 텍스트 임베딩
- MD&A 섹션 임베딩

[Hybrid 질문 처리]
Q: "A사의 공급망 리스크와 대응 전략은?"

1. 그래프: A사 → 공급업체 → 리스크 요인 경로
2. 벡터: "리스크 대응" 관련 청크 검색
3. 융합: 구조적 관계 + 세부 설명 통합
```

---

## 5. 기술 스택 옵션

### 5.1 데이터베이스 조합

| 그래프 DB | 벡터 DB | 특징 |
|-----------|---------|------|
| **Neo4j** | Neo4j Vector Index | 단일 DB에서 모두 처리 |
| **Neo4j** | Qdrant | 전문 벡터 검색 성능 |
| **Memgraph** | Pinecone | 실시간 그래프 + 관리형 벡터 |
| **Nebula Graph** | Milvus | 오픈소스 조합 |

### 5.2 프레임워크

```python
# LangChain + Neo4j 예시 (개념적)
from langchain_neo4j import Neo4jGraph, Neo4jVector

# 그래프 연결
graph = Neo4jGraph(url="...", username="...", password="...")

# 벡터 인덱스
vector_store = Neo4jVector.from_documents(documents, embedding)

# 하이브리드 검색기
hybrid_retriever = HybridRetriever(
    graph=graph,
    vector_store=vector_store,
    fusion_strategy="weighted_sum",
    graph_weight=0.4,
    vector_weight=0.6
)
```

### 5.3 Neo4j GraphRAG 패키지

```python
# pip install "neo4j_graphrag[openai]"
from neo4j_graphrag.retrievers import HybridRetriever

retriever = HybridRetriever(
    driver=neo4j_driver,
    vector_index="document_embeddings",
    graph_query_template="...",
    top_k=10
)
```

---

## 6. 장단점 분석

### 6.1 장점

| 장점 | 설명 |
|------|------|
| **최대 커버리지** | 시맨틱 + 구조적 검색으로 누락 최소화 |
| **Multi-hop + 시맨틱** | 관계 추론과 의미 검색 동시 지원 |
| **설명 가능성** | 그래프 경로로 답변 근거 추적 가능 |
| **유연성** | 질문 유형에 따라 전략 조정 가능 |

### 6.2 단점

| 단점 | 설명 |
|------|------|
| **구현 복잡성** | 두 시스템 통합 및 유지보수 |
| **인프라 비용** | 두 종류의 DB 운영 |
| **튜닝 어려움** | 융합 가중치, 전략 최적화 필요 |
| **지연 시간** | 두 검색 결과 대기 필요 |

### 6.3 트레이드오프 결정 가이드

```
[단순 Q&A 시스템]
→ Vector RAG만으로 충분

[관계 중심 도메인 (법률, 의료, 금융)]
→ Hybrid RAG 권장

[실시간 업데이트 중요]
→ Vector RAG 우선, 점진적 Graph 추가

[설명 가능성 필수]
→ Hybrid RAG 또는 Graph RAG
```

---

## 7. 구현 베스트 프랙티스

### 7.1 점진적 접근

```
[Phase 1: Vector RAG 기반 구축]
- 기본 RAG 시스템 구축
- 성능 베이스라인 확립
- 사용 패턴 분석

[Phase 2: 그래프 레이어 추가]
- 핵심 엔티티/관계 모델링
- 그래프 DB 도입
- 하이브리드 검색 실험

[Phase 3: 최적화]
- 융합 전략 튜닝
- 쿼리 라우팅 로직 개선
- A/B 테스트로 검증
```

### 7.2 엔티티 스키마 설계

```
[일반적인 엔티티 유형]
- Person (인물)
- Organization (조직)
- Product (제품/서비스)
- Event (이벤트)
- Concept (개념/용어)
- Location (장소)

[관계 유형 예시]
- WORKS_FOR (소속)
- LEADS (리더십)
- PRODUCES (생산)
- COMPETES_WITH (경쟁)
- DEPENDS_ON (의존)
```

### 7.3 캐싱 전략

```
[자주 쿼리되는 패턴 캐싱]
- 인기 엔티티의 이웃 노드 미리 로드
- 공통 그래프 쿼리 결과 캐싱
- 임베딩 유사도 상위 결과 캐싱
```

---

## 8. 2025년 발전 동향

### 8.1 LightRAG

EMNLP 2025에서 발표된 경량 Graph RAG 시스템:

```
특징:
- 멀티모달 지식 그래프 지원
- 자동 엔티티 추출
- 텍스트 + 멀티모달 하이브리드 검색
- RAG-Anything으로 다양한 문서 처리
```

### 8.2 통합 플랫폼 트렌드

단일 플랫폼에서 벡터 + 그래프 + 전문 검색을 모두 지원:
- Neo4j: 네이티브 벡터 인덱스 추가
- Elasticsearch: 그래프 순회 + 벡터 검색
- PostgreSQL: pgvector + Apache AGE

### 8.3 자동화된 온톨로지 구축

LLM을 활용한 자동 스키마 발견:
- 도메인 문서에서 자동으로 엔티티 유형 추론
- 관계 패턴 자동 식별
- 동적 스키마 진화

---

## 9. 성능 벤치마크

### 9.1 연구 결과 요약

> "하이브리드 RAG 시스템은 전통적인 벡터 기반 RAG 및 KG 기반 RAG 대응물에 비해 검색 정확도와 답변 생성에서 뚜렷한 성능 우위를 보여주었습니다."
> — [arXiv HybridRAG Paper](https://arxiv.org/abs/2408.04948)

### 9.2 비교 메트릭

| 시스템 | 검색 정확도 | 답변 품질 | 설명 가능성 |
|--------|------------|----------|------------|
| Vector RAG | 기준선 | 기준선 | 낮음 |
| Graph RAG | +15-25% (관계 질문) | +20-30% | 높음 |
| Hybrid RAG | +25-35% | +30-40% | 높음 |

---

## 출처

### 학술 논문
- [arXiv - HybridRAG: Integrating Knowledge Graphs and Vector Retrieval](https://arxiv.org/abs/2408.04948)
- [ACM ICAIF - HybridRAG for Financial Information Extraction](https://dl.acm.org/doi/10.1145/3677052.3698671)
- [LightRAG GitHub (EMNLP 2025)](https://github.com/HKUDS/LightRAG)

### 기업 블로그
- [Memgraph - HybridRAG: Why Combine Vector and Knowledge Graphs](https://memgraph.com/blog/why-hybridrag)
- [Neo4j - RAG Tutorial with Knowledge Graphs](https://neo4j.com/blog/developer/rag-tutorial/)
- [Qdrant - GraphRAG with Qdrant and Neo4j](https://qdrant.tech/documentation/examples/graphrag-qdrant-neo4j/)

### 실용 가이드
- [HackerNoon - Build Hybrid RAG with Knowledge Graphs](https://hackernoon.com/stop-relying-on-vector-search-alone-build-a-hybrid-rag-system-with-knowledge-graphs-and-local-llms)
- [Towards AI - Hybrid Graph RAG for Financial Analysis](https://pub.towardsai.net/hybrid-graph-rag-harnessing-graph-and-vector-for-financial-analysis-72c3a9f1a09d)
- [GitHub - HybridRAG Implementation](https://github.com/sarabesh/HybridRAG)

### 공식 문서
- [Neo4j GraphRAG Python Package](https://neo4j.com/developer/genai-ecosystem/graphrag-python/)
- [Neo4j GraphRAG GitHub](https://github.com/neo4j/neo4j-graphrag-python)
