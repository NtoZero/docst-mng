package com.docst.integration;

import com.docst.chunking.ChunkingService;
import com.docst.domain.*;
import com.docst.domain.Document.DocType;
import com.docst.domain.Repository.RepoProvider;
import com.docst.domain.User.AuthProvider;
import com.docst.embedding.DocstEmbeddingService;
import com.docst.repository.*;
import com.docst.service.HybridSearchService;
import com.docst.service.SearchService;
import com.docst.service.SemanticSearchService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 시맨틱 서치 통합 테스트.
 * 실제 OpenAI API를 사용한 E2E 테스트
 *
 * <p><b>실행 조건</b>:
 * - PostgreSQL + pgvector 실행 중
 * - OPENAI_API_KEY 환경 변수 설정
 * - 실제 API 호출로 인한 비용 발생 (소량)
 *
 * <p><b>실행 방법</b>:
 * ./gradlew test --tests "com.docst.integration.SemanticSearchIntegrationTest"
 *
 * <p><b>비활성화</b>:
 * CI/CD에서는 @Disabled 주석 해제하여 비활성화 가능
 */
@SpringBootTest
@ActiveProfiles("default")  // H2가 아닌 실제 PostgreSQL 사용
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tag("integration")
@Tag("openai")
@Disabled("Phase 2+ 기능 (시맨틱 검색) - Phase 6 LLM 구현 중에는 비활성화")
class SemanticSearchIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentVersionRepository documentVersionRepository;

    @Autowired
    private DocChunkRepository docChunkRepository;

    @Autowired
    private ChunkingService chunkingService;

    @Autowired
    private DocstEmbeddingService embeddingService;

    @Autowired
    private SemanticSearchService semanticSearchService;

    @Autowired
    private SearchService searchService;

    @Autowired
    private HybridSearchService hybridSearchService;

    private static User testUser;
    private static Project testProject;
    private static Repository testRepository;
    private static Document testDocument1;
    private static Document testDocument2;
    private static DocumentVersion testVersion1;
    private static DocumentVersion testVersion2;

    @BeforeAll
    static void checkApiKey() {
        String apiKey = System.getenv("OPENAI_API_KEY");
        Assumptions.assumeTrue(
            apiKey != null && !apiKey.isEmpty(),
            "OPENAI_API_KEY 환경 변수가 설정되지 않았습니다. 테스트를 건너뜁니다."
        );
    }

    @BeforeEach
    void setUp() {
        if (testUser == null) {
            // 기존 테스트 데이터가 있으면 조회, 없으면 생성
            testUser = userRepository.findByProviderAndProviderUserId(AuthProvider.LOCAL, "test-user-id")
                .orElseGet(() -> userRepository.save(
                    new User(AuthProvider.LOCAL, "test-user-id", "test@example.com", "Test User")));

            testProject = projectRepository.findByName("Test Project")
                .orElseGet(() -> projectRepository.save(
                    new Project("Test Project", "Integration test project")));

            testRepository = repositoryRepository.findByProjectIdAndProviderAndOwnerAndName(
                    testProject.getId(), RepoProvider.LOCAL, "testowner", "test-repo")
                .orElseGet(() -> {
                    Repository repo = new Repository(
                        testProject,
                        RepoProvider.LOCAL,
                        "testowner",
                        "test-repo"
                    );
                    repo.setCloneUrl("file:///test/repo");
                    repo.setDefaultBranch("main");
                    repo.setLocalMirrorPath("/tmp/test-repo");
                    return repositoryRepository.save(repo);
                });

            String authContent = """
                # Authentication Guide

                ## Overview
                Our system uses JWT (JSON Web Token) for authentication. This provides a secure and stateless way to authenticate users.

                ## How it Works
                1. User provides credentials (email and password)
                2. Server validates credentials
                3. Server generates a JWT token
                4. Client stores the token (usually in localStorage)
                5. Client sends token in Authorization header for subsequent requests

                ## JWT Structure
                A JWT token consists of three parts:
                - Header: Contains token type and hashing algorithm
                - Payload: Contains user claims and metadata
                - Signature: Ensures token integrity

                ## Security Best Practices
                - Use HTTPS to prevent token interception
                - Set appropriate token expiration times
                - Store tokens securely
                - Implement token refresh mechanism
                - Validate tokens on every request

                ## Example Code
                ```java
                String token = jwtService.generateToken(user);
                response.setHeader("Authorization", "Bearer " + token);
                ```
                """;

            String dbContent = """
                # Database Configuration

                ## PostgreSQL Setup
                We use PostgreSQL as our primary database with pgvector extension for vector similarity search.

                ## Connection Settings
                Configure your database connection in application.yml:
                ```yaml
                spring:
                  datasource:
                    url: jdbc:postgresql://localhost:5432/docst
                    username: postgres
                    password: postgres
                ```

                ## Migrations
                Database schema is managed using Flyway migrations. All migration scripts are located in `db/migration/` directory.

                ## Vector Store
                We use pgvector for storing document embeddings. The vector_store table has the following structure:
                - id: UUID primary key
                - content: Text content of the chunk
                - metadata: JSONB for filtering (project_id, document_id, etc.)
                - embedding: Vector with 1536 dimensions (OpenAI text-embedding-3-small)

                ## Performance Optimization
                - HNSW index for fast similarity search
                - Connection pooling with HikariCP
                - Proper indexing on frequently queried columns
                """;

            // 테스트 문서 1: 인증 관련
            testDocument1 = documentRepository.findByRepositoryIdAndPath(testRepository.getId(), "docs/authentication.md")
                .orElseGet(() -> {
                    Document doc = new Document(testRepository, "docs/authentication.md", "Authentication Guide", DocType.MD);
                    doc.setLatestCommitSha("abc123");
                    return documentRepository.save(doc);
                });

            testVersion1 = documentVersionRepository.findByDocumentIdAndCommitSha(testDocument1.getId(), "abc123")
                .orElseGet(() -> {
                    DocumentVersion ver = new DocumentVersion(testDocument1, "abc123");
                    ver.setAuthorName("Test Author");
                    ver.setAuthorEmail("author@example.com");
                    ver.setCommittedAt(Instant.now());
                    ver.setMessage("Add authentication guide");
                    ver.setContent(authContent);
                    ver.setContentHash("hash1");
                    return documentVersionRepository.save(ver);
                });

            // 테스트 문서 2: 데이터베이스 관련
            testDocument2 = documentRepository.findByRepositoryIdAndPath(testRepository.getId(), "docs/database.md")
                .orElseGet(() -> {
                    Document doc = new Document(testRepository, "docs/database.md", "Database Configuration", DocType.MD);
                    doc.setLatestCommitSha("def456");
                    return documentRepository.save(doc);
                });

            testVersion2 = documentVersionRepository.findByDocumentIdAndCommitSha(testDocument2.getId(), "def456")
                .orElseGet(() -> {
                    DocumentVersion ver = new DocumentVersion(testDocument2, "def456");
                    ver.setAuthorName("Test Author");
                    ver.setAuthorEmail("author@example.com");
                    ver.setCommittedAt(Instant.now());
                    ver.setMessage("Add database configuration");
                    ver.setContent(dbContent);
                    ver.setContentHash("hash2");
                    return documentVersionRepository.save(ver);
                });
        }

        // 각 테스트 전에 기존 chunk 삭제 (중복 방지)
        if (testVersion1 != null) {
            docChunkRepository.deleteByDocumentVersionId(testVersion1.getId());
        }
        if (testVersion2 != null) {
            docChunkRepository.deleteByDocumentVersionId(testVersion2.getId());
        }
    }

    /**
     * 문서 청킹 및 임베딩 생성 테스트.
     *
     * <p>이 테스트는 다음을 검증합니다:
     * <ul>
     *   <li>마크다운 문서가 헤딩 기반으로 청크로 분할됨</li>
     *   <li>OpenAI API를 통해 각 청크의 벡터 임베딩이 생성됨</li>
     *   <li>임베딩이 PostgreSQL pgvector에 저장됨</li>
     * </ul>
     *
     * <p><b>사용 모델</b>: text-embedding-3-small (1536 dimensions)
     */
    @Test
    @Order(1)
    @DisplayName("문서 청킹 및 OpenAI 임베딩 생성")
    void testChunkingAndEmbedding() {
        // Given: 두 개의 테스트 문서 (authentication.md, database.md)

        // When: 문서를 청킹하고 OpenAI API로 임베딩 생성
        List<DocChunk> chunks1 = chunkingService.chunkAndSave(testVersion1);
        int embeddedCount1 = embeddingService.embedDocumentVersion(testVersion1);

        List<DocChunk> chunks2 = chunkingService.chunkAndSave(testVersion2);
        int embeddedCount2 = embeddingService.embedDocumentVersion(testVersion2);

        // Then: 청킹 및 임베딩이 성공적으로 생성됨
        assertTrue(chunks1.size() > 0, "Document 1 should be chunked");
        assertTrue(chunks2.size() > 0, "Document 2 should be chunked");

        assertEquals(chunks1.size(), embeddedCount1, "All chunks should be embedded for doc1");
        assertEquals(chunks2.size(), embeddedCount2, "All chunks should be embedded for doc2");

        System.out.println("✓ Document 1: " + chunks1.size() + " chunks, " + embeddedCount1 + " embeddings");
        System.out.println("✓ Document 2: " + chunks2.size() + " chunks, " + embeddedCount2 + " embeddings");
    }

    /**
     * JWT 인증 관련 시맨틱 검색 테스트.
     *
     * <p>이 테스트는 다음을 검증합니다:
     * <ul>
     *   <li>"How does JWT authentication work?" 쿼리로 시맨틱 검색 수행</li>
     *   <li>authentication.md 문서가 가장 높은 유사도로 검색됨</li>
     *   <li>코사인 유사도 점수가 0.5 이상임</li>
     * </ul>
     *
     * <p><b>검증 포인트</b>: 의미적으로 관련된 문서가 정확히 검색되는지 확인
     */
    @Test
    @Order(2)
    @DisplayName("시맨틱 검색: JWT 인증 쿼리 → authentication.md 검색")
    void testSemanticSearch_Authentication() {
        // Given: 임베딩이 생성된 두 개의 문서
        chunkingService.chunkAndSave(testVersion1);
        embeddingService.embedDocumentVersion(testVersion1);
        chunkingService.chunkAndSave(testVersion2);
        embeddingService.embedDocumentVersion(testVersion2);

        // When: "JWT 토큰 인증" 관련 시맨틱 검색
        String query = "How does JWT authentication work?";
        List<SearchService.SearchResult> results = semanticSearchService.searchSemantic(
            testProject.getId(),
            query,
            5
        );

        // Then: 인증 문서가 높은 순위로 검색됨
        assertFalse(results.isEmpty(), "Should find relevant results");

        System.out.println("\n=== Semantic Search Results for: \"" + query + "\" ===");
        for (int i = 0; i < results.size(); i++) {
            SearchService.SearchResult result = results.get(i);
            System.out.printf("#%d (score: %.4f) %s\n",
                i + 1,
                result.score(),
                result.path()
            );
            System.out.println("  Heading: " + result.headingPath());
            System.out.println("  Snippet: " + result.snippet().substring(0, Math.min(100, result.snippet().length())) + "...");
            System.out.println();
        }

        // 첫 번째 결과는 authentication.md 문서여야 함
        assertTrue(
            results.get(0).path().contains("authentication.md"),
            "Top result should be from authentication document"
        );
        assertTrue(
            results.get(0).score() > 0.5,
            "Top result should have high similarity score"
        );
    }

    /**
     * PostgreSQL/pgvector 관련 시맨틱 검색 테스트.
     *
     * <p>이 테스트는 다음을 검증합니다:
     * <ul>
     *   <li>"How to configure PostgreSQL with pgvector?" 쿼리로 시맨틱 검색 수행</li>
     *   <li>database.md 문서가 가장 높은 유사도로 검색됨</li>
     *   <li>기술 스택 관련 쿼리가 올바른 문서를 찾음</li>
     * </ul>
     *
     * <p><b>검증 포인트</b>: 기술 용어가 포함된 쿼리의 시맨틱 검색 정확도
     */
    @Test
    @Order(3)
    @DisplayName("시맨틱 검색: PostgreSQL/pgvector 쿼리 → database.md 검색")
    void testSemanticSearch_Database() {
        // Given: 임베딩이 생성된 두 개의 문서
        chunkingService.chunkAndSave(testVersion1);
        embeddingService.embedDocumentVersion(testVersion1);
        chunkingService.chunkAndSave(testVersion2);
        embeddingService.embedDocumentVersion(testVersion2);

        // When: "pgvector 설정" 관련 시맨틱 검색
        String query = "How to configure PostgreSQL with pgvector?";
        List<SearchService.SearchResult> results = semanticSearchService.searchSemantic(
            testProject.getId(),
            query,
            5
        );

        // Then: 데이터베이스 문서가 높은 순위로 검색됨
        assertFalse(results.isEmpty(), "Should find relevant results");

        System.out.println("\n=== Semantic Search Results for: \"" + query + "\" ===");
        for (int i = 0; i < results.size(); i++) {
            SearchService.SearchResult result = results.get(i);
            System.out.printf("#%d (score: %.4f) %s\n",
                i + 1,
                result.score(),
                result.path()
            );
            System.out.println("  Heading: " + result.headingPath());
            System.out.println("  Snippet: " + result.snippet().substring(0, Math.min(100, result.snippet().length())) + "...");
            System.out.println();
        }

        // 첫 번째 결과는 database.md 문서여야 함
        assertTrue(
            results.get(0).path().contains("database.md"),
            "Top result should be from database document"
        );
    }

    /**
     * 하이브리드 검색(RRF) 테스트.
     *
     * <p>이 테스트는 다음을 검증합니다:
     * <ul>
     *   <li>키워드 검색과 시맨틱 검색 결과를 RRF(Reciprocal Rank Fusion)로 융합</li>
     *   <li>"JWT token security" 쿼리로 하이브리드 검색 수행</li>
     *   <li>결과가 RRF 점수로 올바르게 정렬됨</li>
     * </ul>
     *
     * <p><b>RRF 알고리즘</b>: score = Σ(1 / (k + rank_i)), k=60 (기본값)
     *
     * @see <a href="https://plg.uwaterloo.ca/~gvcormac/cormacksigir09-rrf.pdf">RRF Paper</a>
     */
    @Test
    @Order(4)
    @DisplayName("하이브리드 검색: RRF 알고리즘으로 키워드 + 시맨틱 결과 융합")
    void testHybridSearch() {
        // Given: 임베딩이 생성된 두 개의 문서
        chunkingService.chunkAndSave(testVersion1);
        embeddingService.embedDocumentVersion(testVersion1);
        chunkingService.chunkAndSave(testVersion2);
        embeddingService.embedDocumentVersion(testVersion2);

        // When: 하이브리드 검색 (키워드 + 시맨틱)
        String query = "JWT token security";
        List<SearchService.SearchResult> hybridResults = hybridSearchService.hybridSearch(
            testProject.getId(),
            query,
            5
        );

        // Then: RRF로 융합된 결과 반환
        assertFalse(hybridResults.isEmpty(), "Should find results with hybrid search");

        System.out.println("\n=== Hybrid Search Results (RRF) for: \"" + query + "\" ===");
        for (int i = 0; i < hybridResults.size(); i++) {
            SearchService.SearchResult result = hybridResults.get(i);
            System.out.printf("#%d (RRF score: %.6f) %s\n",
                i + 1,
                result.score(),
                result.path()
            );
            System.out.println("  Heading: " + result.headingPath());
            System.out.println("  Snippet: " + result.snippet().substring(0, Math.min(100, result.snippet().length())) + "...");
            System.out.println();
        }

        // RRF 점수가 올바르게 계산되었는지 확인
        for (int i = 0; i < hybridResults.size() - 1; i++) {
            assertTrue(
                hybridResults.get(i).score() >= hybridResults.get(i + 1).score(),
                "Results should be sorted by RRF score descending"
            );
        }
    }

    /**
     * 세 가지 검색 방법 비교 테스트.
     *
     * <p>이 테스트는 다음을 검증합니다:
     * <ul>
     *   <li>동일한 쿼리("security best practices")로 세 가지 검색 방법 비교</li>
     *   <li>키워드 검색: PostgreSQL ILIKE 기반 텍스트 매칭</li>
     *   <li>시맨틱 검색: 벡터 코사인 유사도 기반</li>
     *   <li>하이브리드 검색: RRF로 두 결과 융합</li>
     * </ul>
     *
     * <p><b>기대 결과</b>: 각 방법이 다른 관점의 결과를 제공할 수 있음
     */
    @Test
    @Order(5)
    @DisplayName("검색 방법 비교: 키워드 vs 시맨틱 vs 하이브리드")
    void testSearchComparison() {
        // Given: 임베딩이 생성된 두 개의 문서
        chunkingService.chunkAndSave(testVersion1);
        embeddingService.embedDocumentVersion(testVersion1);
        chunkingService.chunkAndSave(testVersion2);
        embeddingService.embedDocumentVersion(testVersion2);

        // When: 동일한 쿼리로 세 가지 검색 방법 비교
        String query = "security best practices";

        List<SearchService.SearchResult> keywordResults = searchService.searchByKeyword(
            testProject.getId(), query, 5
        );

        List<SearchService.SearchResult> semanticResults = semanticSearchService.searchSemantic(
            testProject.getId(), query, 5
        );

        List<SearchService.SearchResult> hybridResults = hybridSearchService.hybridSearch(
            testProject.getId(), query, 5
        );

        // Then: 세 가지 방법 모두 결과 반환
        System.out.println("\n=== Search Methods Comparison for: \"" + query + "\" ===");
        System.out.println("\nKeyword Search (" + keywordResults.size() + " results):");
        keywordResults.forEach(r ->
            System.out.println("  - " + r.path() + " (score: " + r.score() + ")")
        );

        System.out.println("\nSemantic Search (" + semanticResults.size() + " results):");
        semanticResults.forEach(r ->
            System.out.println("  - " + r.path() + " (score: " + String.format("%.4f", r.score()) + ")")
        );

        System.out.println("\nHybrid Search (" + hybridResults.size() + " results):");
        hybridResults.forEach(r ->
            System.out.println("  - " + r.path() + " (RRF score: " + String.format("%.6f", r.score()) + ")")
        );

        // 각 방법이 서로 다른 관점의 결과를 제공할 수 있음
        assertTrue(keywordResults.size() > 0 || semanticResults.size() > 0 || hybridResults.size() > 0,
            "At least one search method should return results");
    }

    /**
     * 유사도 임계값(threshold) 필터링 테스트.
     *
     * <p>이 테스트는 다음을 검증합니다:
     * <ul>
     *   <li>threshold 0.3, 0.5, 0.7로 각각 시맨틱 검색 수행</li>
     *   <li>임계값이 높을수록 반환되는 결과 수가 줄어듦</li>
     *   <li>반환된 모든 결과의 유사도가 설정된 임계값 이상임</li>
     * </ul>
     *
     * <p><b>사용 사례</b>: 검색 품질 조절 - 높은 임계값 = 높은 정확도, 낮은 재현율
     */
    @Test
    @Order(6)
    @DisplayName("유사도 임계값: threshold 0.3/0.5/0.7 필터링 검증")
    void testSimilarityThreshold() {
        // Given: 임베딩이 생성된 두 개의 문서
        chunkingService.chunkAndSave(testVersion1);
        embeddingService.embedDocumentVersion(testVersion1);
        chunkingService.chunkAndSave(testVersion2);
        embeddingService.embedDocumentVersion(testVersion2);

        // When: 다양한 유사도 임계값으로 검색
        String query = "authentication mechanism";

        List<SearchService.SearchResult> results_0_3 = semanticSearchService.searchSemantic(
            testProject.getId(), query, 10, 0.3
        );

        List<SearchService.SearchResult> results_0_5 = semanticSearchService.searchSemantic(
            testProject.getId(), query, 10, 0.5
        );

        List<SearchService.SearchResult> results_0_7 = semanticSearchService.searchSemantic(
            testProject.getId(), query, 10, 0.7
        );

        // Then: 임계값이 높을수록 결과가 줄어듦
        System.out.println("\n=== Similarity Threshold Test ===");
        System.out.println("Query: \"" + query + "\"");
        System.out.println("Threshold 0.3: " + results_0_3.size() + " results");
        System.out.println("Threshold 0.5: " + results_0_5.size() + " results");
        System.out.println("Threshold 0.7: " + results_0_7.size() + " results");

        assertTrue(results_0_3.size() >= results_0_5.size(),
            "Lower threshold should return more or equal results");
        assertTrue(results_0_5.size() >= results_0_7.size(),
            "Lower threshold should return more or equal results");

        // 모든 결과의 유사도가 임계값 이상인지 확인
        results_0_7.forEach(r ->
            assertTrue(r.score() >= 0.7,
                "All results should meet the similarity threshold")
        );
    }
}
