# Graph RAG & Neo4j 하이브리드 RAG FAQ

> 05-graph-rag-fundamentals.md, 06-neo4j-hybrid-rag.md 문서 기반 자주 묻는 질문과 답변

---

## 목차

1. [임베딩과 벡터 검색](#1-임베딩과-벡터-검색)
2. [Text-to-Cypher](#2-text-to-cypher)
3. [그래프 스키마 설계](#3-그래프-스키마-설계)
4. [검색기 선택](#4-검색기-선택)
5. [성능과 비용](#5-성능과-비용)
6. [구현 전략](#6-구현-전략)
7. [일반적인 오해](#7-일반적인-오해)
8. [트러블슈팅](#8-트러블슈팅)
9. [프레임워크 선택 (Python vs Java)](#9-프레임워크-선택-python-vs-java)

---

## 1. 임베딩과 벡터 검색

### Q1.1: Graph RAG에서도 결국 벡터 임베딩을 Neo4j에 저장해야 하는 건가요?

**A:** **반드시 그런 것은 아닙니다.** Graph RAG의 검색 방식에 따라 다릅니다:

| 검색 방식 | 임베딩 필요 여부 |
|----------|-----------------|
| **순수 그래프 순회** | X (불필요) |
| **Text-to-Cypher** | X (불필요) |
| **벡터 검색** | O (필요) |
| **하이브리드** | O (필요) |

```
[순수 Graph RAG - 임베딩 없음]
질문 → 엔티티 추출 → Cypher 쿼리 → 그래프 순회 → 결과

[하이브리드 RAG - 임베딩 필요]
질문 → 벡터 검색 (진입점) → 그래프 순회 (확장) → 결과
```

**권장:** 하이브리드 접근이 가장 효과적이므로, 실무에서는 **벡터 임베딩 + 그래프 구조 둘 다 저장**하는 것이 일반적입니다.

---

### Q1.2: 임베딩은 어떤 노드에 저장해야 하나요? 모든 노드에 다 저장해야 하나요?

**A:** **아니요, 검색 진입점이 될 노드에만 저장하면 됩니다.**

```
[권장 패턴]
(:Chunk {
  text: "원본 텍스트",
  embedding: [0.1, 0.2, ...]  ← 임베딩 저장
})
  -[:MENTIONS]->
(:Entity {
  name: "삼성전자"
  // 임베딩 없음 - 그래프 순회로 접근
})
```

**이유:**
- 벡터 검색은 "진입점 찾기" 용도
- 진입점을 찾은 후에는 그래프 순회로 관련 정보 탐색
- 모든 노드에 임베딩을 저장하면 저장 공간 낭비

**예외:** 엔티티 자체를 직접 검색해야 하는 경우 (예: "삼성전자와 비슷한 회사") 엔티티 노드에도 임베딩 저장 가능

---

### Q1.3: 임베딩용 텍스트와 LLM 컨텍스트용 텍스트를 왜 분리하나요?

**A:** **검색 정확도와 답변 품질을 동시에 높이기 위해서입니다.**

```cypher
(:Chunk {
  // 임베딩용 - 짧고 핵심적 (노이즈 제거)
  summary: "삼성전자 300조 반도체 투자 발표",
  embedding: [...],

  // LLM 컨텍스트용 - 상세하고 맥락 풍부
  full_text: "삼성전자 이재용 회장은 2024년 3월 15일 기자회견에서..."
})
```

**이유:**
1. **임베딩 품질:** 원시 텍스트의 필러 단어("그리고", "또한" 등)가 임베딩 벡터를 희석시킴
2. **검색 정밀도:** 핵심 키워드만 있으면 유사도 매칭이 더 정확함
3. **답변 품질:** LLM에게는 상세한 맥락이 필요

---

### Q1.4: 임베딩 모델은 어떤 것을 사용해야 하나요?

**A:** 용도에 따라 선택하세요:

| 모델 | 차원 | 특징 | 용도 |
|------|------|------|------|
| `text-embedding-3-small` | 1536 | 빠름, 저렴 | 일반 용도 |
| `text-embedding-3-large` | 3072 | 높은 정확도 | 정밀 검색 필요 시 |
| `sentence-transformers` | 다양 | 오픈소스, 로컬 | 비용 절감, 프라이버시 |

**주의:** Neo4j 벡터 인덱스 생성 시 차원 수를 모델과 일치시켜야 합니다:

```cypher
CREATE VECTOR INDEX chunkEmbedding
FOR (c:Chunk) ON (c.embedding)
OPTIONS { indexConfig: { `vector.dimensions`: 1536 } }  -- 모델 차원과 일치!
```

---

## 2. Text-to-Cypher

### Q2.1: Text-to-Cypher를 위해 LLM에게 그래프 스키마를 어떻게 전달하나요?

**A:** **Neo4j GraphRAG 패키지가 자동으로 스키마를 추출하여 프롬프트에 포함시킵니다.**

```python
from neo4j_graphrag.retrievers import Text2CypherRetriever

# 스키마는 Neo4j에서 자동 추출됨
retriever = Text2CypherRetriever(
    driver=driver,
    llm=llm,
    neo4j_schema=None  # None이면 자동 추출
)
```

**수동으로 스키마를 지정하려면:**

```python
schema = """
Node labels: Movie, Person, Genre
Relationship types: ACTED_IN, DIRECTED, IN_GENRE
Node properties:
  - Movie: title, year, plot
  - Person: name, born
Relationship properties:
  - ACTED_IN: role
"""

retriever = Text2CypherRetriever(
    driver=driver,
    llm=llm,
    neo4j_schema=schema  # 명시적 지정
)
```

**LLM에게 전달되는 프롬프트 예시:**

```
당신은 Neo4j Cypher 전문가입니다.
다음 스키마를 가진 그래프 데이터베이스에 대한 Cypher 쿼리를 생성하세요:

[스키마]
Node labels: Movie, Person
Relationships: (Person)-[:ACTED_IN]->(Movie)
Properties: Movie.title, Person.name

[질문]
톰 행크스가 출연한 영화는?

[Cypher 쿼리]
```

---

### Q2.2: Text-to-Cypher가 잘못된 쿼리를 생성하면 어떻게 하나요?

**A:** 여러 가지 신뢰성 향상 방법이 있습니다:

**1. Few-shot 예제 제공**
```python
retriever = Text2CypherRetriever(
    driver=driver,
    llm=llm,
    examples=[
        {
            "question": "톰 행크스가 출연한 영화는?",
            "cypher": "MATCH (p:Person {name: 'Tom Hanks'})-[:ACTED_IN]->(m:Movie) RETURN m.title"
        },
        {
            "question": "2020년 이후 개봉한 영화 수는?",
            "cypher": "MATCH (m:Movie) WHERE m.year >= 2020 RETURN count(m)"
        }
    ]
)
```

**2. Self-healing (자동 재시도)**
```python
retriever = Text2CypherRetriever(
    driver=driver,
    llm=llm,
    retry_on_error=True,   # 오류 시 재시도
    max_retries=3          # 최대 3번
)
```

**3. 쿼리 검증 단계 추가**
```python
def validate_and_execute(query):
    try:
        # EXPLAIN으로 쿼리 유효성 검사 (실행하지 않음)
        driver.execute_query(f"EXPLAIN {query}")
        return driver.execute_query(query)
    except Exception as e:
        # 오류 메시지와 함께 LLM에게 재생성 요청
        return regenerate_query(query, error=str(e))
```

---

### Q2.3: Text-to-Cypher vs 그래프 순회, 언제 뭘 써야 하나요?

**A:**

| 상황 | 권장 방식 |
|------|----------|
| 사전 정의된 쿼리 패턴 | **그래프 순회** (VectorCypherRetriever) |
| 예측 불가능한 다양한 질문 | **Text-to-Cypher** |
| 정확도가 매우 중요 | **그래프 순회** |
| 빠른 프로토타이핑 | **Text-to-Cypher** |

**그래프 순회 (권장 시나리오):**
```python
# 질문 패턴이 예측 가능: "X의 관련 제품은?"
retrieval_query = """
MATCH (node)-[:PRODUCES]->(product)
RETURN node.name, collect(product.name)
"""
```

**Text-to-Cypher (권장 시나리오):**
```
# 다양한 질문 유형
- "2020년 이후 개봉한 SF 영화 중 평점 8점 이상인 것은?"
- "크리스토퍼 놀란과 협업한 배우 중 오스카 수상자는?"
```

---

## 3. 그래프 스키마 설계

### Q3.1: 지식 그래프 스키마는 어떻게 설계해야 하나요?

**A:** 질문 유형을 먼저 정의하고, 그에 맞는 경로가 가능하도록 설계하세요.

**Step 1: 예상 질문 나열**
```
- "삼성전자의 CEO는?"
- "이재용이 투자한 기술 분야는?"
- "AI 반도체에 투자한 회사들은?"
```

**Step 2: 질문을 그래프 경로로 변환**
```
Q: "삼성전자의 CEO는?"
→ (Company)-[:HAS_CEO]->(Person)

Q: "이재용이 투자한 기술 분야는?"
→ (Person)-[:INVESTED_IN]->(Technology)

Q: "AI 반도체에 투자한 회사들은?"
→ (Company)-[:INVESTS_IN]->(Technology)
```

**Step 3: 스키마 통합**
```
(:Company)-[:HAS_CEO]->(:Person)
(:Person)-[:INVESTED_IN]->(:Technology)
(:Company)-[:INVESTS_IN]->(:Technology)
(:Document)-[:HAS_CHUNK]->(:Chunk)
(:Chunk)-[:MENTIONS]->(:Company|Person|Technology)
```

---

### Q3.2: 엔티티 추출은 어떻게 하나요? 수동으로 해야 하나요?

**A:** **LLM으로 자동 추출할 수 있습니다.**

**LangChain LLMGraphTransformer 사용:**
```python
from langchain_experimental.graph_transformers import LLMGraphTransformer
from langchain_openai import ChatOpenAI

llm = ChatOpenAI(model="gpt-4o")
transformer = LLMGraphTransformer(llm=llm)

# 문서에서 그래프 추출
documents = [Document(page_content="이재용은 삼성전자의 회장입니다.")]
graph_documents = transformer.convert_to_graph_documents(documents)

# 결과
# Nodes: [이재용:Person, 삼성전자:Company]
# Relationships: [(이재용)-[:CHAIRMAN_OF]->(삼성전자)]
```

**Neo4j GraphRAG 패키지 사용:**
```python
from neo4j_graphrag.experimental.components.entity_relation_extractor import (
    LLMEntityRelationExtractor
)

extractor = LLMEntityRelationExtractor(llm=llm)
result = await extractor.run(text="이재용은 삼성전자의 회장입니다.")
```

**주의사항:**
- LLM 추출은 100% 정확하지 않음
- 중요한 도메인은 수동 검토 필요
- 일관성을 위해 엔티티 유형을 사전 정의하는 것이 좋음

---

### Q3.3: Document → Chunk → Entity 구조가 꼭 필요한가요?

**A:** **아니요, 필수는 아니지만 권장됩니다.**

**왜 권장되는가:**

```
[3단계 구조의 장점]

1. 출처 추적
   Entity ← Chunk ← Document
   "이 정보가 어느 문서의 어느 부분에서 왔는가?"

2. 세밀한 검색
   - 벡터 검색 → Chunk 단위 (세밀)
   - 그래프 순회 → Entity 단위 (구조적)

3. 중복 제거
   - 같은 엔티티가 여러 문서에 언급되어도
   - 하나의 Entity 노드에 여러 Chunk가 연결됨
```

**간단한 프로젝트라면:**
```
(:Document {
  text: "...",
  embedding: [...],
  entities: ["삼성전자", "이재용"]  // 속성으로 저장
})
```

---

## 4. 검색기 선택

### Q4.1: 5가지 검색기 중 어떤 것을 선택해야 하나요?

**A:** 다음 의사결정 트리를 따르세요:

```
시맨틱 검색만 필요?
├── Yes → VectorRetriever
└── No
    │
    정확한 키워드/날짜 매칭 필요?
    ├── No
    │   │
    │   그래프 관계 탐색 필요?
    │   ├── Yes → VectorCypherRetriever
    │   └── No → VectorRetriever
    │
    └── Yes
        │
        그래프 관계 탐색 필요?
        ├── Yes → HybridCypherRetriever (풀 하이브리드)
        └── No → HybridRetriever
```

**실무 권장:**
- **시작:** `HybridRetriever`로 시작
- **관계 필요 시:** `HybridCypherRetriever`로 업그레이드
- **동적 쿼리 필요 시:** `Text2CypherRetriever` 추가

---

### Q4.2: VectorCypherRetriever에서 retrieval_query는 어떻게 작성하나요?

**A:** `node` 변수가 벡터 검색 결과를 참조합니다.

```python
# 벡터 검색으로 Movie 노드를 찾은 후
# 관련 배우와 감독까지 확장

retrieval_query = """
MATCH (node)  // 'node'는 벡터 검색 결과
OPTIONAL MATCH (node)<-[:ACTED_IN]-(actor:Person)
OPTIONAL MATCH (node)<-[:DIRECTED]-(director:Person)
RETURN node.title AS title,
       node.plot AS plot,
       collect(DISTINCT actor.name) AS actors,
       collect(DISTINCT director.name) AS directors
"""

retriever = VectorCypherRetriever(
    driver=driver,
    index_name="movieEmbedding",
    retrieval_query=retrieval_query,
    embedder=embedder
)
```

**핵심 규칙:**
- `node`는 예약 변수명 (변경 불가)
- `RETURN`에서 반환할 속성 명시
- `OPTIONAL MATCH`로 관계 없는 경우 처리

---

### Q4.3: 하이브리드 검색에서 벡터와 전문 검색 결과는 어떻게 합쳐지나요?

**A:** **점수 정규화 후 합산**됩니다.

```python
# 내부 로직 (개념적)
def hybrid_merge(vector_results, fulltext_results):
    # 1. 각 결과 점수를 0-1 범위로 정규화
    v_scores = normalize(vector_results.scores)  # [0.9, 0.7, 0.5]
    f_scores = normalize(fulltext_results.scores)  # [0.8, 0.6]

    # 2. 같은 노드가 양쪽에 있으면 점수 합산
    merged = {}
    for node, score in vector_results:
        merged[node.id] = score

    for node, score in fulltext_results:
        if node.id in merged:
            merged[node.id] += score  # 가산!
        else:
            merged[node.id] = score

    # 3. 점수 높은 순 정렬
    return sorted(merged, key=lambda x: x.score, reverse=True)
```

**결과:**
- 벡터 검색에만 나온 노드: 벡터 점수만
- 전문 검색에만 나온 노드: 전문 점수만
- **둘 다 나온 노드: 점수 합산 → 상위 랭크**

---

## 5. 성능과 비용

### Q5.1: Graph RAG가 일반 RAG보다 비용이 더 드나요?

**A:** **인덱싱 비용은 높지만, 쿼리 비용은 비슷하거나 낮을 수 있습니다.**

| 단계 | Vector RAG | Graph RAG |
|------|-----------|-----------|
| **인덱싱** | 임베딩 생성 | 임베딩 + 엔티티 추출 + 관계 매핑 |
| **쿼리** | 벡터 검색 | 그래프 순회 (벡터 검색 선택적) |
| **LLM 호출** | 동일 | 동일 |

**비용 절감 팁:**
1. 엔티티 추출에 저렴한 모델 사용 (GPT-4o-mini)
2. 배치 처리로 임베딩 생성
3. 점진적 인덱싱 (새 문서만)

---

### Q5.2: Multi-hop 순회가 너무 느린데 어떻게 최적화하나요?

**A:** 다음 최적화 기법을 적용하세요:

**1. hop 수 제한**
```cypher
-- 비효율적
MATCH (a)-[*]->(b)  -- 무제한!

-- 효율적
MATCH (a)-[*1..3]->(b)  -- 최대 3 hop
```

**2. 관계 유형 필터링**
```cypher
-- 비효율적
MATCH (a)-[*1..3]->(b)

-- 효율적
MATCH (a)-[:CEO_OF|WORKS_AT|INVESTED_IN*1..3]->(b)
```

**3. 레이블 필터**
```cypher
MATCH (a:Company)-[*1..2]->(b:Technology)
WHERE b.category = 'AI'
```

**4. 결과 제한**
```cypher
MATCH path = (a)-[*1..3]->(b)
RETURN path
LIMIT 50
```

---

### Q5.3: Neo4j 무료 버전으로도 Graph RAG를 구현할 수 있나요?

**A:** **네, Neo4j AuraDB Free 또는 Community Edition으로 가능합니다.**

| 버전 | 벡터 인덱스 | 전문 인덱스 | 제한 |
|------|:---------:|:---------:|------|
| **AuraDB Free** | O | O | 200K 노드, 400K 관계 |
| **Community** | O | O | 기능 제한 없음 (자체 호스팅) |
| **Enterprise** | O | O | 클러스터링, 보안 기능 |

**시작하기:**
```bash
# Docker로 Community Edition 실행
docker run \
  --publish=7474:7474 --publish=7687:7687 \
  -e NEO4J_AUTH=neo4j/password \
  neo4j:5.15
```

---

## 6. 구현 전략

### Q6.1: Graph RAG를 처음 도입할 때 어디서 시작해야 하나요?

**A:** **점진적 접근을 권장합니다.**

```
[Phase 1] 기본 Vector RAG (1-2주)
├── 청킹, 임베딩, 벡터 검색 구현
├── 베이스라인 성능 측정
└── 어떤 질문에서 실패하는지 파악

[Phase 2] 그래프 추가 (2-3주)
├── 실패 케이스 분석 → 필요한 관계 도출
├── 핵심 엔티티/관계 스키마 정의
├── LLM으로 엔티티 추출
└── VectorCypherRetriever로 전환

[Phase 3] 하이브리드 (1-2주)
├── 전문 인덱스 추가
├── HybridCypherRetriever 도입
└── A/B 테스트

[Phase 4] 최적화 (지속)
├── Text-to-Cypher 실험
├── 쿼리 성능 튜닝
└── 도메인 특화
```

---

### Q6.2: 기존 Vector RAG에 그래프를 추가하려면?

**A:** 다음 단계를 따르세요:

**Step 1: 현재 청크에서 엔티티 추출**
```python
# 기존 청크에서 엔티티 추출 (배치)
for chunk in existing_chunks:
    entities = extract_entities(chunk.text)
    for entity in entities:
        # 엔티티 노드 생성 (없으면)
        create_entity_if_not_exists(entity)
        # Chunk → Entity 관계 생성
        create_mentions_relationship(chunk.id, entity.id)
```

**Step 2: 엔티티 간 관계 추가**
```python
# 같은 청크에 언급된 엔티티들 연결
for chunk in chunks:
    entities = get_entities_mentioned_in(chunk)
    for e1, e2 in combinations(entities, 2):
        create_relationship(e1, e2, "CO_MENTIONED")
```

**Step 3: 검색기 업그레이드**
```python
# Before: VectorRetriever
# After: VectorCypherRetriever

retrieval_query = """
MATCH (node)-[:MENTIONS]->(entity)
RETURN node.text, collect(entity.name) as entities
"""

retriever = VectorCypherRetriever(
    driver=driver,
    index_name="chunkEmbedding",
    retrieval_query=retrieval_query,
    embedder=embedder
)
```

---

### Q6.3: Graph RAG를 도입하지 말아야 할 때는?

**A:** 다음 경우에는 Vector RAG로 충분합니다:

1. **단순 Q&A**: "X란 무엇인가?" 같은 정의 질문
2. **관계 추론 불필요**: 문서 내 정보로 충분
3. **데이터 변경 빈번**: 그래프 유지 비용 > 이점
4. **하이브리드 검색으로 충분**: 벡터 + 키워드로 해결됨

**도입 필요 신호:**
- "A와 B의 관계는?" 질문 빈번
- 여러 문서 정보 연결 필요
- 답변의 근거 추적 필요 (설명 가능성)
- 정확한 매칭 필수 (법률, 의료, 금융)

---

## 7. 일반적인 오해

### Q7.1: Graph RAG는 벡터 검색을 대체하나요?

**A:** **아니요, 보완합니다.**

```
[잘못된 이해]
Vector RAG ──대체──> Graph RAG

[올바른 이해]
Vector RAG ──결합──> Hybrid Graph RAG
    │                    │
    │                    ├── 벡터 검색 (시맨틱)
    │                    ├── 전문 검색 (키워드)
    │                    └── 그래프 순회 (관계)
```

**실제 사용:**
- 벡터 검색: "진입점 찾기"
- 그래프 순회: "관계 확장"

---

### Q7.2: Microsoft GraphRAG를 꼭 써야 Graph RAG인가요?

**A:** **아니요, MS GraphRAG는 하나의 구현 방식일 뿐입니다.**

| 측면 | MS GraphRAG | 범용 Graph RAG |
|------|-------------|----------------|
| **커뮤니티 탐지** | 필수 | 선택적 |
| **계층적 요약** | 필수 | 불필요 |
| **Local/Global Search** | 고유 기능 | 직접 쿼리 |

**"Graph RAG"의 넓은 정의:**
> 지식 그래프를 검색 소스로 활용하는 모든 RAG 시스템

Neo4j + LangChain, LlamaIndex 등으로 MS GraphRAG 없이도 구현 가능

---

### Q7.3: 지식 그래프를 수동으로 구축해야 하나요?

**A:** **아니요, LLM으로 자동 추출이 가능합니다. 단, 품질 검토는 필요합니다.**

| 방법 | 정확도 | 비용 | 적합한 경우 |
|------|--------|------|------------|
| **LLM 자동 추출** | 중간 | 낮음 | 프로토타이핑, 대규모 데이터 |
| **규칙 기반 추출** | 높음 | 중간 | 정형화된 문서 |
| **수동 큐레이션** | 최고 | 높음 | 핵심 도메인 지식 |

**권장 전략:**
1. LLM으로 초기 추출
2. 샘플 검토로 품질 확인
3. 핵심 엔티티/관계만 수동 보정
4. 피드백 루프로 추출 품질 개선

---

## 8. 트러블슈팅

### Q8.1: 벡터 검색 결과가 없어요

**A:** 다음을 확인하세요:

```python
# 1. 인덱스 상태 확인
SHOW INDEXES YIELD name, type, state, populationPercent
WHERE type = 'VECTOR'

# 2. 임베딩 차원 확인
# 인덱스 생성 시 차원 = 임베더 차원
CREATE VECTOR INDEX ... OPTIONS { `vector.dimensions`: 1536 }
embedder = OpenAIEmbeddings(model="text-embedding-3-small")  # 1536차원

# 3. 노드에 임베딩이 있는지 확인
MATCH (n:Chunk) WHERE n.embedding IS NOT NULL RETURN count(n)

# 4. 인덱스가 해당 노드를 커버하는지
MATCH (n:Chunk) RETURN count(n)  # 전체
```

---

### Q8.2: Text-to-Cypher가 틀린 쿼리를 생성해요

**A:**

**1. 스키마 정보 확인**
```python
# 스키마가 LLM에게 제대로 전달되는지
print(retriever.neo4j_schema)
```

**2. Few-shot 예제 추가**
```python
examples = [
    {"question": "...", "cypher": "..."},
    # 실패한 케이스와 유사한 예제 추가
]
```

**3. 더 강력한 LLM 사용**
```python
# GPT-4o, Claude Sonnet 등 Cypher 이해도 높은 모델
llm = ChatOpenAI(model="gpt-4o")
```

**4. 오류 메시지 분석**
```python
try:
    result = retriever.search(query)
except Exception as e:
    print(f"생성된 쿼리: {retriever.last_query}")
    print(f"오류: {e}")
```

---

### Q8.3: 그래프 순회가 너무 많은 결과를 반환해요

**A:**

```cypher
-- 문제: 무제한 순회
MATCH (a)-[*]->(b) RETURN b

-- 해결 1: hop 제한
MATCH (a)-[*1..2]->(b) RETURN b

-- 해결 2: 관계 유형 제한
MATCH (a)-[:SPECIFIC_REL*1..2]->(b) RETURN b

-- 해결 3: 레이블 제한
MATCH (a)-[*1..2]->(b:TargetLabel) RETURN b

-- 해결 4: 결과 제한
MATCH (a)-[*1..2]->(b) RETURN b LIMIT 50

-- 해결 5: 조건 필터
MATCH (a)-[*1..2]->(b)
WHERE b.importance > 0.5
RETURN b
```

---

### Q8.4: 하이브리드 검색에서 전문 검색이 작동 안 해요

**A:** 전문 인덱스는 **프로시저 호출**이 필요합니다:

```cypher
-- 전문 인덱스 생성 확인
SHOW INDEXES WHERE type = 'FULLTEXT'

-- 전문 인덱스는 자동 사용되지 않음!
-- 프로시저로 호출해야 함
CALL db.index.fulltext.queryNodes("indexName", "검색어")
YIELD node, score
RETURN node, score
```

**HybridRetriever는 자동 처리:**
```python
# 패키지가 내부적으로 프로시저 호출
retriever = HybridRetriever(
    driver=driver,
    vector_index_name="vectorIndex",
    fulltext_index_name="fulltextIndex",  # 자동으로 프로시저 사용
    embedder=embedder
)
```

---

## 9. 프레임워크 선택 (Python vs Java)

### Q9.1: Neo4j GraphRAG는 Python(LangChain)만 지원하나요? Java/Spring은 안 되나요?

**A:** **아니요, Spring AI도 공식 지원됩니다.**

Neo4j GraphRAG를 지원하는 프레임워크:

| 프레임워크 | 언어 | 특징 |
|-----------|------|------|
| **neo4j-graphrag-python** | Python | Neo4j 공식, 풍부한 Retriever |
| **LangChain** | Python | 생태계 풍부, 빠른 프로토타이핑 |
| **LlamaIndex** | Python | 데이터 인덱싱 특화 |
| **Spring AI** | Java | Spring 생태계 통합, 엔터프라이즈 |
| **LangChain4j** | Java | LangChain의 Java 포트 |
| **Semantic Kernel** | C#/Python | Microsoft 지원 |

> "Spring AI는 Java 세계를 위한 LangChain과 같은 프로젝트입니다."
> — [Neo4j Labs](https://neo4j.com/labs/genai-ecosystem/spring-ai/)

---

### Q9.2: Spring AI로 Neo4j GraphRAG를 어떻게 구현하나요?

**A:** Spring AI는 Neo4j Vector Store를 공식 지원합니다.

**1. 의존성 추가 (Maven)**

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-vector-store-neo4j</artifactId>
</dependency>
```

**2. application.yml 설정**

```yaml
spring:
  neo4j:
    uri: bolt://localhost:7687
    authentication:
      username: neo4j
      password: password
  ai:
    vectorstore:
      neo4j:
        database-name: neo4j
        index-name: document-embeddings
        embedding-dimension: 1536      # 임베딩 모델 차원과 일치
        distance-type: cosine
        initialize-schema: true        # 스키마 자동 생성
        label: Document                # 문서 노드 레이블
        embedding-property: embedding  # 임베딩 속성명
```

**3. 벡터 검색 사용**

```java
@Autowired
private VectorStore vectorStore;

// 문서 추가
List<Document> documents = List.of(
    new Document("삼성전자가 반도체 투자를 발표했습니다.",
        Map.of("source", "news", "date", "2024-03-15")),
    new Document("AI 칩 시장이 급성장하고 있습니다.")
);
vectorStore.add(documents);

// 유사 검색
List<Document> results = vectorStore.similaritySearch(
    SearchRequest.builder()
        .query("반도체 투자")
        .topK(5)
        .build()
);
```

---

### Q9.3: Spring AI에서 벡터 검색 + 그래프 순회(GraphRAG)를 어떻게 결합하나요?

**A:** **3단계 프로세스**로 구현합니다.

```java
@Service
public class GraphRAGService {

    @Autowired private VectorStore vectorStore;
    @Autowired private ChunkRepository chunkRepository;  // Spring Data Neo4j
    @Autowired private ChatClient chatClient;

    public String query(String question) {
        // 1단계: 벡터 유사성 검색
        List<Document> similarDocs = vectorStore.similaritySearch(
            SearchRequest.builder()
                .query(question)
                .topK(5)
                .build()
        );

        // 2단계: 그래프 순회로 관련 엔티티 확장
        List<String> docIds = similarDocs.stream()
            .map(Document::getId)
            .collect(Collectors.toList());

        List<ChunkWithEntities> enrichedResults =
            chunkRepository.findRelatedEntitiesForChunks(docIds);

        // 3단계: LLM에 전달하여 답변 생성
        String context = formatContext(enrichedResults);
        return chatClient.prompt()
            .user(u -> u.text("Context: {context}\n\nQuestion: {question}")
                .param("context", context)
                .param("question", question))
            .call()
            .content();
    }
}
```

**Spring Data Neo4j Repository:**

```java
public interface ChunkRepository extends Neo4jRepository<Chunk, String> {

    @Query("""
        MATCH (c:Chunk) WHERE c.id IN $chunkIds
        OPTIONAL MATCH (c)-[:MENTIONS]->(e:Entity)
        OPTIONAL MATCH (e)-[:RELATED_TO]-(related:Entity)
        RETURN c, collect(DISTINCT e) as entities,
               collect(DISTINCT related) as relatedEntities
    """)
    List<ChunkWithEntities> findRelatedEntitiesForChunks(
        @Param("chunkIds") List<String> chunkIds
    );
}
```

---

### Q9.4: Spring AI의 Neo4j 설정 속성은 무엇이 있나요?

**A:** 주요 설정 속성:

| 속성 | 설명 | 기본값 |
|------|------|--------|
| `spring.neo4j.uri` | Neo4j 연결 URI | `bolt://localhost:7687` |
| `spring.neo4j.authentication.username` | 사용자명 | `neo4j` |
| `spring.neo4j.authentication.password` | 비밀번호 | - |
| `spring.ai.vectorstore.neo4j.database-name` | DB 이름 | `neo4j` |
| `spring.ai.vectorstore.neo4j.index-name` | 벡터 인덱스명 | `spring-ai-document-index` |
| `spring.ai.vectorstore.neo4j.embedding-dimension` | 벡터 차원 | `1536` |
| `spring.ai.vectorstore.neo4j.distance-type` | 거리 함수 | `cosine` |
| `spring.ai.vectorstore.neo4j.label` | 노드 레이블 | `Document` |
| `spring.ai.vectorstore.neo4j.embedding-property` | 임베딩 속성 | `embedding` |
| `spring.ai.vectorstore.neo4j.initialize-schema` | 스키마 자동 생성 | `false` |

**거리 함수 옵션:**
- `cosine` (기본): 코사인 유사도
- `euclidean`: 유클리드 거리

---

### Q9.5: Spring AI에서 메타데이터 필터링은 어떻게 하나요?

**A:** 텍스트 표현식 또는 DSL 방식 지원:

**텍스트 표현식:**

```java
List<Document> results = vectorStore.similaritySearch(
    SearchRequest.builder()
        .query("반도체 투자")
        .topK(5)
        .similarityThreshold(0.7)
        .filterExpression("source == 'news' && year >= 2024")
        .build()
);
```

**DSL 방식:**

```java
FilterExpressionBuilder b = new FilterExpressionBuilder();

List<Document> results = vectorStore.similaritySearch(
    SearchRequest.builder()
        .query("반도체 투자")
        .topK(5)
        .filterExpression(b.and(
            b.eq("source", "news"),
            b.gte("year", 2024)
        ).build())
        .build()
);
```

---

### Q9.6: Python(LangChain) vs Java(Spring AI), 어떤 것을 선택해야 하나요?

**A:** 팀 스택과 요구사항에 따라 선택:

| 기준 | Python (LangChain) | Java (Spring AI) |
|------|-------------------|------------------|
| **프로토타이핑 속도** | 빠름 | 보통 |
| **생태계** | 풍부 (커뮤니티 대형) | 성장 중 |
| **엔터프라이즈** | 보통 | 강점 (Spring 생태계) |
| **Retriever 종류** | 5가지+ | VectorStore 중심 |
| **Text-to-Cypher** | 공식 지원 | 직접 구현 필요 |
| **Spring Data 통합** | X | 네이티브 지원 |
| **타입 안정성** | 약함 | 강함 |

**Python 권장:**
- 빠른 프로토타이핑
- 다양한 검색 패턴 실험
- AI/ML 팀

**Spring AI 권장:**
- 기존 Spring Boot 프로젝트
- 엔터프라이즈 환경
- Java 중심 팀
- Spring Data Neo4j 활용

---

### Q9.7: Spring AI에서 Text-to-Cypher를 구현하려면?

**A:** Spring AI에는 Text2CypherRetriever가 없어 **직접 구현**해야 합니다.

```java
@Service
public class Text2CypherService {

    @Autowired private ChatClient chatClient;
    @Autowired private Neo4jClient neo4jClient;

    private static final String SCHEMA = """
        Node labels: Company, Person, Technology
        Relationships: HAS_CEO, INVESTED_IN, PRODUCES
        Properties:
          - Company: name, founded
          - Person: name, role
          - Technology: name, category
        """;

    public List<Map<String, Object>> query(String naturalLanguageQuery) {
        // 1. LLM으로 Cypher 생성
        String cypher = chatClient.prompt()
            .system(s -> s.text("""
                You are a Neo4j Cypher expert. Generate a Cypher query for:
                {schema}
                Return ONLY the Cypher query, no explanation.
                """).param("schema", SCHEMA))
            .user(naturalLanguageQuery)
            .call()
            .content();

        // 2. Cypher 실행
        try {
            return neo4jClient.query(cypher)
                .fetch()
                .all()
                .stream()
                .collect(Collectors.toList());
        } catch (Exception e) {
            // 3. 오류 시 재생성 (Self-healing)
            return retryWithError(naturalLanguageQuery, cypher, e.getMessage());
        }
    }
}
```

---

### Q9.8: Spring AI + Neo4j에서 필요한 버전은?

**A:** **Neo4j 5.15 이상** 권장

| 구성 요소 | 최소 버전 | 권장 버전 |
|----------|----------|----------|
| **Neo4j** | 5.11 | 5.15+ |
| **Spring Boot** | 3.2 | 3.3+ |
| **Spring AI** | 1.0.0-M1 | 최신 마일스톤 |
| **Java** | 17 | 21 |

**주의:** Spring AI는 아직 GA(정식 릴리스)가 아니므로 **스냅샷/마일스톤 저장소**를 추가해야 합니다:

```xml
<repositories>
    <repository>
        <id>spring-milestones</id>
        <url>https://repo.spring.io/milestone</url>
    </repository>
    <repository>
        <id>spring-snapshots</id>
        <url>https://repo.spring.io/snapshot</url>
    </repository>
</repositories>
```

---

### Q9.9: Spring AI GraphRAG 예제 프로젝트가 있나요?

**A:** 네, 공식 및 커뮤니티 예제가 있습니다:

| 프로젝트 | 설명 | 링크 |
|---------|------|------|
| **Spring AI Starter Kit** | Neo4j 공식 스타터 | [GitHub](https://github.com/neo4j-examples/spring-ai-starter-kit/) |
| **vector-graph-rag** | VectorRAG + GraphRAG 데모 | [GitHub](https://github.com/JMHReif/vector-graph-rag) |
| **GenAI Starter Kit** | Spring AI + Neo4j 가이드 | [Neo4j Blog](https://neo4j.com/blog/developer/genai-starter-kit-spring-java/) |

---

## 참고 문서

### 내부 문서
- [05-graph-rag-fundamentals.md](./05-graph-rag-fundamentals.md) - 범용 Graph RAG 원론
- [06-neo4j-hybrid-rag.md](./06-neo4j-hybrid-rag.md) - Neo4j 하이브리드 RAG 구현

### Python (LangChain/neo4j-graphrag)
- [Neo4j GraphRAG Python 문서](https://neo4j.com/docs/neo4j-graphrag-python/current/user_guide_rag.html)
- [Neo4j GraphAcademy](https://graphacademy.neo4j.com/knowledge-graph-rag/)

### Java (Spring AI)
- [Spring AI Neo4j 공식 문서](https://docs.spring.io/spring-ai/reference/api/vectordbs/neo4j.html)
- [Neo4j Labs - Spring AI Guide](https://neo4j.com/labs/genai-ecosystem/spring-ai/)
- [Neo4j Developer - Spring AI](https://neo4j.com/developer/genai-ecosystem/spring-ai/)
- [Spring AI Starter Kit GitHub](https://github.com/neo4j-examples/spring-ai-starter-kit/)
