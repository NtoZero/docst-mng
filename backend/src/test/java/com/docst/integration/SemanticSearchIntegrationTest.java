package com.docst.integration;

import com.docst.chunking.ChunkingService;
import com.docst.document.*;
import com.docst.document.Document.DocType;
import com.docst.document.repository.DocChunkRepository;
import com.docst.document.repository.DocumentRepository;
import com.docst.document.repository.DocumentVersionRepository;
import com.docst.gitrepo.Repository;
import com.docst.gitrepo.Repository.RepoProvider;
import com.docst.gitrepo.repository.RepositoryRepository;
import com.docst.user.User;
import com.docst.user.User.AuthProvider;
import com.docst.user.repository.UserRepository;
import com.docst.project.Project;
import com.docst.project.repository.ProjectRepository;
import com.docst.embedding.DocstEmbeddingService;
import com.docst.search.service.HybridSearchService;
import com.docst.search.service.SearchService;
import com.docst.search.service.SemanticSearchService;
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

    @Test
    @Order(1)
    @DisplayName("문서 청킹 및 OpenAI 임베딩 생성")
    void testChunkingAndEmbedding() {
        List<DocChunk> chunks1 = chunkingService.chunkAndSave(testVersion1);
        int embeddedCount1 = embeddingService.embedDocumentVersion(testVersion1);

        List<DocChunk> chunks2 = chunkingService.chunkAndSave(testVersion2);
        int embeddedCount2 = embeddingService.embedDocumentVersion(testVersion2);

        assertTrue(chunks1.size() > 0, "Document 1 should be chunked");
        assertTrue(chunks2.size() > 0, "Document 2 should be chunked");

        assertEquals(chunks1.size(), embeddedCount1, "All chunks should be embedded for doc1");
        assertEquals(chunks2.size(), embeddedCount2, "All chunks should be embedded for doc2");
    }

    @Test
    @Order(2)
    @DisplayName("시맨틱 검색: JWT 인증 쿼리 → authentication.md 검색")
    void testSemanticSearch_Authentication() {
        chunkingService.chunkAndSave(testVersion1);
        embeddingService.embedDocumentVersion(testVersion1);
        chunkingService.chunkAndSave(testVersion2);
        embeddingService.embedDocumentVersion(testVersion2);

        String query = "How does JWT authentication work?";
        List<SearchService.SearchResult> results = semanticSearchService.searchSemantic(
            testProject.getId(), query, 5
        );

        assertFalse(results.isEmpty(), "Should find relevant results");

        assertTrue(
            results.get(0).path().contains("authentication.md"),
            "Top result should be from authentication document"
        );
        assertTrue(
            results.get(0).score() > 0.5,
            "Top result should have high similarity score"
        );
    }

    @Test
    @Order(3)
    @DisplayName("시맨틱 검색: PostgreSQL/pgvector 쿼리 → database.md 검색")
    void testSemanticSearch_Database() {
        chunkingService.chunkAndSave(testVersion1);
        embeddingService.embedDocumentVersion(testVersion1);
        chunkingService.chunkAndSave(testVersion2);
        embeddingService.embedDocumentVersion(testVersion2);

        String query = "How to configure PostgreSQL with pgvector?";
        List<SearchService.SearchResult> results = semanticSearchService.searchSemantic(
            testProject.getId(), query, 5
        );

        assertFalse(results.isEmpty(), "Should find relevant results");

        assertTrue(
            results.get(0).path().contains("database.md"),
            "Top result should be from database document"
        );
    }

    @Test
    @Order(4)
    @DisplayName("하이브리드 검색: RRF 알고리즘으로 키워드 + 시맨틱 결과 융합")
    void testHybridSearch() {
        chunkingService.chunkAndSave(testVersion1);
        embeddingService.embedDocumentVersion(testVersion1);
        chunkingService.chunkAndSave(testVersion2);
        embeddingService.embedDocumentVersion(testVersion2);

        String query = "JWT token security";
        List<SearchService.SearchResult> hybridResults = hybridSearchService.hybridSearch(
            testProject.getId(), query, 5
        );

        assertFalse(hybridResults.isEmpty(), "Should find results with hybrid search");

        for (int i = 0; i < hybridResults.size() - 1; i++) {
            assertTrue(
                hybridResults.get(i).score() >= hybridResults.get(i + 1).score(),
                "Results should be sorted by RRF score descending"
            );
        }
    }

    @Test
    @Order(5)
    @DisplayName("검색 방법 비교: 키워드 vs 시맨틱 vs 하이브리드")
    void testSearchComparison() {
        chunkingService.chunkAndSave(testVersion1);
        embeddingService.embedDocumentVersion(testVersion1);
        chunkingService.chunkAndSave(testVersion2);
        embeddingService.embedDocumentVersion(testVersion2);

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

        assertTrue(keywordResults.size() > 0 || semanticResults.size() > 0 || hybridResults.size() > 0,
            "At least one search method should return results");
    }

    @Test
    @Order(6)
    @DisplayName("유사도 임계값: threshold 0.3/0.5/0.7 필터링 검증")
    void testSimilarityThreshold() {
        chunkingService.chunkAndSave(testVersion1);
        embeddingService.embedDocumentVersion(testVersion1);
        chunkingService.chunkAndSave(testVersion2);
        embeddingService.embedDocumentVersion(testVersion2);

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

        assertTrue(results_0_3.size() >= results_0_5.size(),
            "Lower threshold should return more or equal results");
        assertTrue(results_0_5.size() >= results_0_7.size(),
            "Lower threshold should return more or equal results");

        results_0_7.forEach(r ->
            assertTrue(r.score() >= 0.7,
                "All results should meet the similarity threshold")
        );
    }
}
