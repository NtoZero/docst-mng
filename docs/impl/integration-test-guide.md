# 통합 테스트 가이드

## 시맨틱 서치 통합 테스트

### 개요

`SemanticSearchIntegrationTest`는 실제 OpenAI API를 사용하여 전체 임베딩 및 검색 파이프라인을 테스트합니다.

### 테스트 시나리오

1. **문서 청킹 및 임베딩 생성**
   - 마크다운 문서를 청크로 분할
   - OpenAI API를 사용하여 벡터 임베딩 생성
   - PostgreSQL vector_store에 저장

2. **시맨틱 서치**
   - "How does JWT authentication work?" 쿼리
   - 벡터 유사도 기반 검색
   - 관련성 높은 문서 반환 검증

3. **하이브리드 서치**
   - RRF 알고리즘으로 키워드 + 시맨틱 결과 융합
   - 점수 계산 검증

4. **검색 방법 비교**
   - 키워드 vs 시맨틱 vs 하이브리드
   - 각 방법의 장단점 확인

5. **유사도 임계값 테스트**
   - threshold 0.3, 0.5, 0.7 비교
   - 필터링 정확도 검증

### 사전 요구사항

#### 1. PostgreSQL + pgvector 실행

```bash
# Docker Compose로 PostgreSQL 시작
docker-compose up -d postgres

# pgvector 테이블 생성 확인
docker exec -i docst-mng-postgres-1 psql -U postgres -d docst -c "\d vector_store"
```

#### 2. OpenAI API Key 설정

**옵션 A: 환경 변수 (권장)**
```bash
# Linux/Mac
export OPENAI_API_KEY=sk-proj-your-api-key-here

# Windows (PowerShell)
$env:OPENAI_API_KEY="sk-proj-your-api-key-here"

# Windows (CMD)
set OPENAI_API_KEY=sk-proj-your-api-key-here
```

**옵션 B: .env 파일**
```bash
# backend/.env 파일 생성
OPENAI_API_KEY=sk-proj-your-api-key-here
```

**옵션 C: IDE 설정**
- IntelliJ IDEA: Run Configuration > Environment Variables
- Eclipse: Run Configurations > Environment

### 테스트 실행

#### Gradle 명령어로 실행

```bash
cd backend

# 통합 테스트만 실행
./gradlew test --tests "com.docst.integration.SemanticSearchIntegrationTest"

# 특정 테스트 메서드만 실행
./gradlew test --tests "com.docst.integration.SemanticSearchIntegrationTest.testSemanticSearch_Authentication"

# 모든 통합 테스트 실행 (Tag 기반)
./gradlew test -Dgroups=integration
```

#### IDE에서 실행

1. **IntelliJ IDEA**
   - `SemanticSearchIntegrationTest.java` 파일 열기
   - 클래스 옆 초록색 화살표 클릭 → "Run SemanticSearchIntegrationTest"
   - 또는 `Ctrl+Shift+F10` (Windows) / `Cmd+Shift+R` (Mac)

2. **Eclipse**
   - 파일 우클릭 → Run As → JUnit Test

### 예상 비용

OpenAI API 사용 비용 (text-embedding-3-small):
- **테스트당**: 약 $0.0001 - $0.001 (1,000-10,000 tokens)
- **전체 테스트 스위트**: 약 $0.001 - $0.005

> 💡 **참고**: 비용은 문서 크기에 따라 다릅니다. 테스트용 문서는 작게 설계되어 있습니다.

### 테스트 출력 예시

```
✓ Document 1: 4 chunks, 4 embeddings
✓ Document 2: 3 chunks, 3 embeddings

=== Semantic Search Results for: "How does JWT authentication work?" ===
#1 (score: 0.8234) docs/authentication.md
  Heading: # Authentication Guide > ## How it Works
  Snippet: Our system uses JWT (JSON Web Token) for authentication. This provides a secure and stateless...

#2 (score: 0.7891) docs/authentication.md
  Heading: # Authentication Guide > ## JWT Structure
  Snippet: A JWT token consists of three parts: Header, Payload, and Signature...

=== Hybrid Search Results (RRF) for: "JWT token security" ===
#1 (RRF score: 0.032787) docs/authentication.md
  Heading: # Authentication Guide > ## Security Best Practices
  ...
```

### 테스트 비활성화

CI/CD 환경에서 자동 실행을 방지하려면:

```java
@Disabled("OpenAI API 키가 필요하며 비용이 발생합니다.")
class SemanticSearchIntegrationTest {
    // ...
}
```

또는 Gradle에서 Tag로 제외:

```bash
# integration 태그 제외하고 실행
./gradlew test -Dexcluded.groups=integration,openai
```

### 트러블슈팅

#### 1. "OPENAI_API_KEY 환경 변수가 설정되지 않았습니다"

**원인**: API 키가 설정되지 않음

**해결**:
```bash
export OPENAI_API_KEY=sk-proj-your-actual-key
./gradlew test --tests "com.docst.integration.*"
```

#### 2. "Connection refused: localhost:5434"

**원인**: PostgreSQL이 실행 중이지 않음

**해결**:
```bash
docker-compose up -d postgres
docker ps | grep postgres  # 확인
```

#### 3. "Table 'vector_store' doesn't exist"

**원인**: Flyway 마이그레이션이 실행되지 않음

**해결**:
```bash
# 백엔드 한 번 실행하여 마이그레이션 적용
./gradlew bootRun

# 또는 수동으로 마이그레이션 실행
./gradlew flywayMigrate
```

#### 4. "OpenAI API error: 401 Unauthorized"

**원인**: 잘못된 API 키 또는 만료된 키

**해결**:
- https://platform.openai.com/api-keys 에서 키 확인
- 새 키 생성
- 환경 변수 재설정

#### 5. "OpenAI API error: 429 Too Many Requests"

**원인**: Rate limit 초과

**해결**:
- 잠시 대기 후 재시도 (1분)
- API plan 업그레이드 고려
- Tier limits 확인: https://platform.openai.com/settings/organization/limits

### Best Practices

1. **로컬 개발 시에만 실행**
   - CI/CD에서는 `@Disabled` 활성화
   - 수동 트리거로만 실행

2. **API 키 보안**
   - `.env` 파일은 `.gitignore`에 추가
   - 환경 변수 사용 권장
   - 키를 코드에 직접 작성 금지

3. **비용 관리**
   - 테스트 문서 크기 최소화
   - 불필요한 반복 실행 자제
   - OpenAI 사용량 모니터링: https://platform.openai.com/usage

4. **데이터 정리**
   - 테스트 후 vector_store 데이터 자동 정리
   - `@Transactional` 사용으로 롤백 가능
   - 필요 시 `@DirtiesContext` 추가

### 참고 자료

- [OpenAI Embeddings API](https://platform.openai.com/docs/guides/embeddings)
- [Spring AI Documentation](https://docs.spring.io/spring-ai/reference/)
- [pgvector GitHub](https://github.com/pgvector/pgvector)
- [RRF Algorithm](https://plg.uwaterloo.ca/~gvcormac/cormacksigir09-rrf.pdf)
