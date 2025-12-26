# Neo4j 하이브리드 RAG 구현 가이드

> Neo4j를 활용하여 **벡터 검색 + 전문 검색(Fulltext) + 그래프 순회**를 결합한 하이브리드 RAG 시스템 구축 가이드

---

## 1. Neo4j 하이브리드 RAG 개요

### 1.1 왜 하이브리드인가?

단일 검색 방식의 한계:

| 검색 방식 | 강점 | 약점 |
|----------|------|------|
| **벡터 검색** | 시맨틱 유사도 | 정확한 키워드/날짜 매칭 어려움 |
| **전문 검색** | 정확한 어휘 매칭 | 의미적 유사성 파악 불가 |
| **그래프 순회** | 관계 추론 | 진입점(entry point) 필요 |

> "벡터 인덱스와 전문 인덱스를 결합하면 벡터 검색만으로는 놓칠 수 있는 정보를 검색하여 성능을 향상시킬 수 있습니다."
> — [Neo4j Developer Blog](https://neo4j.com/developer-blog/hybrid-retrieval-graphrag-python-package/)

### 1.2 Neo4j의 장점

**단일 데이터베이스에서 모든 검색 지원:**

```
Neo4j 5.11+
├── 벡터 인덱스 (Vector Index) - 시맨틱 검색
├── 전문 인덱스 (Fulltext Index) - 키워드 검색
└── 네이티브 그래프 순회 - 관계 탐색
```

별도의 벡터 DB 없이 Neo4j만으로 하이브리드 RAG 구현 가능

---

## 2. 아키텍처

### 2.1 전체 구조

```
┌─────────────────────────────────────────────────────────────────┐
│                    데이터 인제스천                                │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  [원본 문서]                                                     │
│       │                                                         │
│       ▼                                                         │
│  [LLMGraphTransformer] ─ 엔티티/관계 추출                        │
│       │                                                         │
│       ├──────────────────┬──────────────────┐                   │
│       ▼                  ▼                  ▼                   │
│  [노드 생성]         [관계 생성]        [청크 저장]               │
│       │                  │                  │                   │
│       ▼                  ▼                  ▼                   │
│  [벡터 임베딩]       [그래프 구조]      [전문 인덱싱]              │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                    Neo4j 데이터베이스                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   ┌─────────────┐  ┌─────────────┐  ┌─────────────┐            │
│   │ Vector      │  │ Fulltext    │  │ Graph       │            │
│   │ Index       │  │ Index       │  │ Structure   │            │
│   │             │  │             │  │             │            │
│   │ embedding   │  │ title, text │  │ (n)-[r]->(m)│            │
│   │ similarity  │  │ keyword     │  │ traversal   │            │
│   └─────────────┘  └─────────────┘  └─────────────┘            │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                    하이브리드 검색                                │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  [사용자 질문]                                                   │
│       │                                                         │
│       ├─────────────┬─────────────┬─────────────┐               │
│       ▼             ▼             ▼             ▼               │
│  [벡터 검색]   [전문 검색]   [그래프 순회]  [Text2Cypher]         │
│       │             │             │             │               │
│       └─────────────┴──────┬──────┴─────────────┘               │
│                            ▼                                    │
│                   [점수 정규화 & 병합]                            │
│                            ▼                                    │
│                   [통합 컨텍스트]                                 │
│                            ▼                                    │
│                   [LLM 답변 생성]                                │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 핵심 개념: 암시적 + 명시적 시맨틱스

```
[벡터 임베딩]
"삼성전자가 반도체 투자를 발표했다"
→ [0.23, -0.15, 0.87, ...] (암시적 의미)

[그래프 관계]
(삼성전자)-[:ANNOUNCED]->(반도체투자)
→ 명시적 구조화된 의미

[Neo4j 하이브리드의 힘]
HNSW 인덱스로 진입점 찾기 → 그래프 순회로 관계 확장
= 암시적 시맨틱스 + 명시적 시맨틱스 결합
```

---

## 3. Neo4j GraphRAG 검색기(Retriever) 종류

### 3.1 검색기 비교표

| 검색기 | 벡터 | 전문 | 그래프 순회 | 용도 |
|--------|:----:|:----:|:----------:|------|
| **VectorRetriever** | O | X | X | 기본 시맨틱 검색 |
| **VectorCypherRetriever** | O | X | O | 벡터 + 그래프 확장 |
| **HybridRetriever** | O | O | X | 벡터 + 키워드 |
| **HybridCypherRetriever** | O | O | O | **풀 하이브리드** |
| **Text2CypherRetriever** | X | X | O | 자연어 → Cypher |

### 3.2 VectorRetriever

가장 기본적인 벡터 유사도 검색

```python
from neo4j_graphrag.retrievers import VectorRetriever

retriever = VectorRetriever(
    driver=driver,
    index_name="moviePlotsEmbedding",
    embedder=embedder,
    return_properties=["title", "plot"]
)

result = retriever.search(query_text="우주 전쟁 영화", top_k=5)
```

### 3.3 VectorCypherRetriever

벡터 검색 후 **그래프 순회로 확장**

```python
from neo4j_graphrag.retrievers import VectorCypherRetriever

# 벡터로 영화 찾고, 출연 배우까지 확장
retrieval_query = """
MATCH (node)-[:ACTED_IN]-(actor:Person)
RETURN node.title AS title,
       node.plot AS plot,
       collect(actor.name) AS actors
"""

retriever = VectorCypherRetriever(
    driver=driver,
    index_name="moviePlotsEmbedding",
    retrieval_query=retrieval_query,
    embedder=embedder
)

# "node"는 벡터 검색으로 찾은 노드를 참조
result = retriever.search(query_text="톰 행크스가 나오는 전쟁 영화")
```

**핵심:** `node` 변수가 벡터 검색 결과를 참조하여 그래프 순회 가능

### 3.4 HybridRetriever

벡터 + 전문 검색 결합 (그래프 순회 없음)

```python
from neo4j_graphrag.retrievers import HybridRetriever

retriever = HybridRetriever(
    driver=driver,
    vector_index_name="moviePlotsEmbedding",
    fulltext_index_name="movieFulltext",
    embedder=embedder,
    return_properties=["title", "plot"]
)

# 날짜나 고유명사가 포함된 질문에 효과적
result = retriever.search(
    query_text="1375년 중국 황실을 배경으로 한 영화",
    top_k=3
)
```

**작동 방식:**
1. 벡터 인덱스와 전문 인덱스 동시 검색
2. 각 결과의 점수 정규화
3. 병합 후 순위 재정렬
4. Top-K 반환

### 3.5 HybridCypherRetriever (풀 하이브리드)

**벡터 + 전문 + 그래프 순회** 모두 결합

```python
from neo4j_graphrag.retrievers import HybridCypherRetriever

retrieval_query = """
MATCH (node)-[:ACTED_IN]-(actor:Person)
MATCH (node)-[:DIRECTED_BY]-(director:Person)
RETURN node.title AS title,
       node.plot AS plot,
       collect(DISTINCT actor.name) AS actors,
       collect(DISTINCT director.name) AS directors
"""

retriever = HybridCypherRetriever(
    driver=driver,
    vector_index_name="moviePlotsEmbedding",
    fulltext_index_name="movieFulltext",
    retrieval_query=retrieval_query,
    embedder=embedder
)

result = retriever.search(
    query_text="스티븐 스필버그 감독의 2차 세계대전 영화",
    top_k=5
)
```

### 3.6 Text2CypherRetriever

자연어를 Cypher 쿼리로 변환

```python
from neo4j_graphrag.retrievers import Text2CypherRetriever

retriever = Text2CypherRetriever(
    driver=driver,
    llm=llm,
    neo4j_schema=schema,  # 스키마 정보 필수
    examples=[  # Few-shot 예제로 정확도 향상
        {
            "question": "톰 행크스가 출연한 영화는?",
            "cypher": "MATCH (p:Person {name: 'Tom Hanks'})-[:ACTED_IN]->(m:Movie) RETURN m.title"
        }
    ]
)

result = retriever.search(
    query_text="크리스토퍼 놀란이 감독하고 레오나르도 디카프리오가 출연한 영화"
)
```

---

## 4. 인덱스 설정

### 4.1 벡터 인덱스 생성

```cypher
-- Neo4j 5.11+
CREATE VECTOR INDEX moviePlotsEmbedding IF NOT EXISTS
FOR (m:Movie)
ON (m.embedding)
OPTIONS {
  indexConfig: {
    `vector.dimensions`: 1536,
    `vector.similarity_function`: 'cosine'
  }
}
```

### 4.2 전문 인덱스 생성

```cypher
CREATE FULLTEXT INDEX movieFulltext IF NOT EXISTS
FOR (m:Movie)
ON EACH [m.title, m.plot]
```

**벡터 vs 전문 인덱스 차이:**

| 측면 | 벡터 인덱스 | 전문 인덱스 |
|------|------------|------------|
| **매칭 방식** | 시맨틱 유사도 | 어휘적 유사도 (정확한 단어) |
| **강점** | "비슷한 의미" 검색 | 날짜, 이름, 코드 정확 매칭 |
| **예시** | "우주 모험" → "스타워즈" | "1375" → 정확히 1375 포함 문서 |

### 4.3 전문 인덱스 쿼리 방법

```cypher
-- 전문 인덱스는 자동으로 사용되지 않음, 프로시저 호출 필요
CALL db.index.fulltext.queryNodes("movieFulltext", "스필버그 1998")
YIELD node, score
RETURN node.title, score
ORDER BY score DESC
LIMIT 5
```

---

## 5. 데이터 모델링 베스트 프랙티스

### 5.1 임베딩과 텍스트 분리

```cypher
-- 권장 패턴
(:Chunk {
  id: "chunk-001",

  -- 임베딩 생성용 (간결하게)
  summary: "삼성전자 반도체 투자 발표",
  embedding: [0.23, -0.15, ...],

  -- LLM 컨텍스트용 (상세하게)
  full_text: "삼성전자 이재용 회장은 2024년 3월 15일..."
})
```

**이유:** 원시 텍스트의 필러 단어가 임베딩 품질을 희석시킬 수 있음

### 5.2 그래프 스키마 예시

```
(:Document)-[:HAS_CHUNK]->(:Chunk)
(:Chunk)-[:MENTIONS]->(:Entity)
(:Entity)-[:RELATED_TO]->(:Entity)
(:Entity)-[:BELONGS_TO]->(:Category)

-- 예시
(:Document {title: "반도체 보고서"})
  -[:HAS_CHUNK]->
(:Chunk {summary: "삼성 투자 발표", embedding: [...]})
  -[:MENTIONS]->
(:Company {name: "삼성전자"})
  -[:INVESTED_IN]->
(:Technology {name: "AI 반도체"})
```

### 5.3 Multi-hop 지원을 위한 설계

```cypher
-- 2-hop 질문: "삼성전자 CEO가 투자한 기술 분야는?"
-- 지원하려면:

(:Company {name: "삼성전자"})
  -[:HAS_CEO]->
(:Person {name: "이재용"})
  -[:INVESTED_IN]->
(:Technology {name: "AI 반도체"})

-- 또는 직접 관계
(:Company)-[:INVESTS_IN]->(:Technology)
```

---

## 6. LangChain 통합

### 6.1 Neo4jVector (하이브리드 모드)

```python
from langchain_neo4j import Neo4jVector
from langchain_openai import OpenAIEmbeddings

embeddings = OpenAIEmbeddings()

# 하이브리드 검색 활성화
db = Neo4jVector.from_documents(
    documents,
    embeddings,
    url="bolt://localhost:7687",
    username="neo4j",
    password="password",
    search_type="hybrid"  # 핵심 설정
)

# 검색
results = db.similarity_search_with_score(
    "AI 반도체 투자",
    k=5
)
```

### 6.2 LangGraph + Neo4j 워크플로우

```python
from langgraph.graph import StateGraph
from langchain_neo4j import Neo4jGraph

# 상태 정의
class RAGState(TypedDict):
    question: str
    context: str
    answer: str

# 그래프 연결
graph = Neo4jGraph(url=NEO4J_URI, username=NEO4J_USER, password=NEO4J_PASSWORD)

# 워크플로우 정의
workflow = StateGraph(RAGState)

# 노드 추가: 질문 분석 → 검색 전략 선택 → 검색 → 답변 생성
workflow.add_node("analyze", analyze_question)
workflow.add_node("route", route_to_retriever)
workflow.add_node("retrieve", hybrid_retrieve)
workflow.add_node("generate", generate_answer)

# 엣지 연결
workflow.add_edge("analyze", "route")
workflow.add_conditional_edges("route", decide_retriever)
workflow.add_edge("retrieve", "generate")
```

### 6.3 도구 라우팅 패턴

```python
from langchain.agents import Tool, AgentExecutor

# 구조화된 질문용 도구
graph_tool = Tool(
    name="GraphQuery",
    func=lambda q: graph.query(generate_cypher(q)),
    description="기업 관계, 인물 정보 등 구조화된 질문에 사용"
)

# 시맨틱 질문용 도구
vector_tool = Tool(
    name="VectorSearch",
    func=lambda q: vector_store.similarity_search(q),
    description="개념 설명, 유사한 내용 찾기 등 시맨틱 질문에 사용"
)

# 에이전트가 질문 유형에 따라 도구 선택
agent = create_react_agent(llm, [graph_tool, vector_tool])
```

---

## 7. 구현 예제

### 7.1 전체 하이브리드 RAG 파이프라인

```python
from neo4j import GraphDatabase
from neo4j_graphrag.retrievers import HybridCypherRetriever
from neo4j_graphrag.embeddings import OpenAIEmbeddings
from neo4j_graphrag.llm import OpenAILLM
from neo4j_graphrag.generation import GraphRAG

# 1. 연결 설정
driver = GraphDatabase.driver(
    "bolt://localhost:7687",
    auth=("neo4j", "password")
)
embedder = OpenAIEmbeddings(model="text-embedding-3-small")
llm = OpenAILLM(model="gpt-4o")

# 2. 하이브리드 검색기 설정
retrieval_query = """
MATCH (node)-[:MENTIONS]->(entity:Entity)
OPTIONAL MATCH (entity)-[:RELATED_TO]-(related:Entity)
RETURN node.text AS text,
       node.title AS title,
       collect(DISTINCT entity.name) AS entities,
       collect(DISTINCT related.name) AS related_entities
"""

retriever = HybridCypherRetriever(
    driver=driver,
    vector_index_name="chunkEmbedding",
    fulltext_index_name="chunkFulltext",
    retrieval_query=retrieval_query,
    embedder=embedder
)

# 3. GraphRAG 파이프라인 구성
rag = GraphRAG(
    retriever=retriever,
    llm=llm
)

# 4. 질문 응답
response = rag.search(
    query_text="삼성전자의 2024년 반도체 투자 계획과 관련 기업은?",
    retriever_config={"top_k": 5}
)

print(response.answer)
```

### 7.2 점수 정규화 및 융합 로직 (내부 작동)

```python
# HybridRetriever 내부 작동 방식 (개념적)
def hybrid_search(query, vector_index, fulltext_index, top_k):
    # 1. 벡터 검색
    vector_results = vector_index.search(embed(query), top_k * 2)

    # 2. 전문 검색
    fulltext_results = fulltext_index.search(query, top_k * 2)

    # 3. 점수 정규화 (0-1 범위로)
    vector_scores = normalize(vector_results.scores)
    fulltext_scores = normalize(fulltext_results.scores)

    # 4. 결과 병합
    merged = {}
    for node, score in zip(vector_results.nodes, vector_scores):
        merged[node.id] = {"node": node, "score": score}

    for node, score in zip(fulltext_results.nodes, fulltext_scores):
        if node.id in merged:
            merged[node.id]["score"] += score  # 두 검색에 모두 나오면 가산
        else:
            merged[node.id] = {"node": node, "score": score}

    # 5. 정렬 및 Top-K 반환
    sorted_results = sorted(merged.values(), key=lambda x: x["score"], reverse=True)
    return sorted_results[:top_k]
```

---

## 8. 성능 최적화

### 8.1 인덱스 최적화

```cypher
-- 벡터 인덱스 설정 최적화
CREATE VECTOR INDEX chunkEmbedding
FOR (c:Chunk)
ON (c.embedding)
OPTIONS {
  indexConfig: {
    `vector.dimensions`: 1536,
    `vector.similarity_function`: 'cosine',
    `vector.hnsw.m`: 16,           -- 연결 수 (높을수록 정확, 느림)
    `vector.hnsw.efConstruction`: 200  -- 구축 시 탐색 깊이
  }
}
```

### 8.2 Cypher 쿼리 최적화

```cypher
-- 비효율적
MATCH (node)-[*1..5]->(related)  -- 무제한 패턴

-- 효율적
MATCH (node)-[:SPECIFIC_REL]->(related)  -- 관계 유형 명시
WHERE related:SpecificLabel              -- 레이블 필터
LIMIT 10                                  -- 결과 제한
```

### 8.3 k-hop 제한

```python
retrieval_query = """
MATCH path = (node)-[*1..2]->(related)  -- 최대 2 hop으로 제한
WHERE ALL(r IN relationships(path) WHERE type(r) IN ['MENTIONS', 'RELATED_TO'])
RETURN node, collect(DISTINCT related) AS related_nodes
LIMIT 50
"""
```

---

## 9. 실제 사용 사례

### 9.1 바이오메디컬 GraphRAG

> "Cedars-Sinai의 알츠하이머 지식 베이스(AlzKB)는 유전자, 약물, 질병 엔티티를 그래프로 모델링하고 벡터 검색과 결합하여 연구자들이 복잡한 생물학적 관계를 탐색할 수 있게 합니다."

```
사용 패턴:
1. 벡터 검색: "알츠하이머 관련 유전자" → 유사 논문/엔티티
2. 그래프 순회: 유전자 → 관련 단백질 → 잠재적 약물 타겟
3. 결합: 연구자에게 구조화된 경로 + 문헌 근거 제공
```

### 9.2 법률/규정 준수

```
사용 패턴:
1. 전문 검색: 정확한 조항 번호, 날짜 매칭
2. 벡터 검색: 유사 판례, 관련 해석
3. 그래프 순회: 조항 → 관련 규정 → 적용 사례
```

### 9.3 기업 지식 관리

```
사용 패턴:
1. 하이브리드 검색: 프로젝트명 + 기술 개념
2. 그래프 순회: 프로젝트 → 담당자 → 관련 문서 → 의존 시스템
3. 결과: "이 시스템을 변경하면 영향받는 프로젝트와 담당자"
```

---

## 10. 문제 해결

### 10.1 일반적인 이슈

| 문제 | 원인 | 해결책 |
|------|------|--------|
| 벡터 검색 결과 없음 | 임베딩 차원 불일치 | 인덱스와 임베더 차원 확인 |
| 전문 검색 미작동 | 프로시저 미사용 | `db.index.fulltext.queryNodes()` 사용 |
| 그래프 순회 느림 | 무제한 패턴 매칭 | 관계 유형, hop 수 제한 |
| Text2Cypher 오류 | 스키마 정보 부족 | 스키마 + Few-shot 예제 제공 |

### 10.2 디버깅 팁

```python
# 검색 결과 점수 확인
result = retriever.search(query_text="테스트 쿼리", top_k=5)

for item in result.items:
    print(f"Score: {item.score}")
    print(f"Content: {item.content}")
    print(f"Metadata: {item.metadata}")
    print("---")
```

```cypher
-- 인덱스 상태 확인
SHOW INDEXES
YIELD name, type, state, populationPercent
WHERE type IN ['VECTOR', 'FULLTEXT']
```

---

## 11. 버전 요구사항

| 구성 요소 | 최소 버전 | 권장 버전 |
|----------|----------|----------|
| **Neo4j** | 5.11 | 5.15+ |
| **neo4j-graphrag-python** | 0.3.0 | 최신 |
| **Python** | 3.9 | 3.11+ |
| **langchain-neo4j** | 0.1.0 | 최신 |

---

## 출처

### Neo4j 공식 문서
- [Enhancing Hybrid Retrieval With Graph Traversal](https://neo4j.com/blog/developer/enhancing-hybrid-retrieval-graphrag-python-package/)
- [Hybrid Retrieval for GraphRAG Applications](https://neo4j.com/developer-blog/hybrid-retrieval-graphrag-python-package/)
- [Neo4j GraphRAG Python Documentation](https://neo4j.com/docs/neo4j-graphrag-python/current/user_guide_rag.html)
- [Neo4j GraphRAG API Documentation](https://neo4j.com/docs/neo4j-graphrag-python/current/api.html)
- [Create a Neo4j GraphRAG Workflow Using LangChain and LangGraph](https://neo4j.com/blog/developer/neo4j-graphrag-workflow-langchain-langgraph/)
- [LangChain Neo4j Integration](https://neo4j.com/labs/genai-ecosystem/langchain/)
- [Full-text Indexes - Cypher Manual](https://neo4j.com/docs/cypher-manual/current/indexes/semantic-indexes/full-text-indexes/)

### GitHub
- [neo4j-graphrag-python](https://github.com/neo4j/neo4j-graphrag-python)
- [HybridCypherRetriever 예제](https://github.com/neo4j/neo4j-graphrag-python/blob/main/examples/retrieve/hybrid_cypher_retriever.py)

### 튜토리얼 및 블로그
- [GraphRAG Tutorial - Neo4j + LLMs](https://medium.com/@daniel.puenteviejo/graphrag-tutorial-neo4j-llms-47372b71e3fa) (2025)
- [GraphRAG Explained: Building Knowledge-Grounded LLM Systems](https://pub.towardsai.net/graphrag-explained-building-knowledge-grounded-llm-systems-with-neo4j-and-langchain-017a1820763e) (2025)
- [Building a GraphRAG Pipeline Using Neo4j and LangChain](https://www.jellyfishtechnologies.com/building-a-graphrag-pipeline-using-neo4j-and-langchain/) (2025)
- [Building a Biomedical GraphRAG](https://aiechoes.substack.com/p/building-a-biomedical-graphrag-when)
- [GraphRAG with Qdrant and Neo4j](https://qdrant.tech/documentation/examples/graphrag-qdrant-neo4j/)
- [Building a Hybrid RAG Agent with Neo4j and Milvus](https://hackernoon.com/building-a-hybrid-rag-agent-with-neo4j-graphs-and-milvus-vector-search)

### LangChain
- [Enhancing RAG-based Applications with Knowledge Graphs](https://blog.langchain.com/enhancing-rag-based-applications-accuracy-by-constructing-and-leveraging-knowledge-graphs/)
