package com.docst.mcp;

import com.docst.auth.SecurityUtils;
import com.docst.auth.UserPrincipal;
import com.docst.domain.Document;
import com.docst.domain.Document.DocType;
import com.docst.domain.DocumentVersion;
import com.docst.domain.Repository;
import com.docst.mcp.McpModels.*;
import com.docst.mcp.tools.McpDocumentTools;
import com.docst.mcp.tools.McpGitTools;
import com.docst.mcp.tools.McpProjectTools;
import com.docst.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * MCP Tools 단위 테스트.
 * Tool annotation 기반 도구들의 동작을 검증.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class McpToolsTest {

    @Nested
    @DisplayName("McpDocumentTools Tests")
    class DocumentToolsTests {

        @Mock
        private DocumentService documentService;
        @Mock
        private SearchService searchService;
        @Mock
        private SemanticSearchService semanticSearchService;
        @Mock
        private HybridSearchService hybridSearchService;
        @Mock
        private ProjectService projectService;

        private McpDocumentTools documentTools;

        @BeforeEach
        void setUp() {
            documentTools = new McpDocumentTools(
                documentService, searchService, semanticSearchService,
                hybridSearchService, projectService
            );
        }

        @Test
        @DisplayName("listDocuments - repositoryId로 문서 목록 조회")
        void listDocuments_withRepositoryId_returnsDocuments() {
            // Given
            UUID repoId = UUID.randomUUID();
            Document mockDoc = createMockDocument(repoId);
            when(documentService.findByRepositoryId(eq(repoId), isNull(), isNull()))
                .thenReturn(List.of(mockDoc));

            // When
            ListDocumentsResult result = documentTools.listDocuments(
                repoId.toString(), null, null, null
            );

            // Then
            assertThat(result.documents()).hasSize(1);
            assertThat(result.documents().get(0).path()).isEqualTo("docs/test.md");
            verify(documentService).findByRepositoryId(repoId, null, null);
        }

        @Test
        @DisplayName("listDocuments - projectId로 문서 목록 조회")
        void listDocuments_withProjectId_returnsDocuments() {
            // Given
            UUID projectId = UUID.randomUUID();
            UUID repoId = UUID.randomUUID();
            Document mockDoc = createMockDocument(repoId);
            when(documentService.findByProjectId(projectId))
                .thenReturn(List.of(mockDoc));

            // When
            ListDocumentsResult result = documentTools.listDocuments(
                null, projectId.toString(), null, null
            );

            // Then
            assertThat(result.documents()).hasSize(1);
            verify(documentService).findByProjectId(projectId);
        }

        @Test
        @DisplayName("getDocument - 문서 내용 조회")
        void getDocument_returnsDocumentContent() {
            // Given
            UUID docId = UUID.randomUUID();
            UUID repoId = UUID.randomUUID();
            Document mockDoc = createMockDocument(repoId);
            DocumentVersion mockVersion = createMockVersion(mockDoc);

            when(documentService.findById(docId)).thenReturn(Optional.of(mockDoc));
            when(documentService.findLatestVersion(docId)).thenReturn(Optional.of(mockVersion));

            // When
            GetDocumentResult result = documentTools.getDocument(docId.toString(), null);

            // Then
            assertThat(result.content()).isEqualTo("# Test Document\n\nContent here.");
            assertThat(result.commitSha()).isEqualTo("abc123");
        }

        @Test
        @DisplayName("getDocument - 존재하지 않는 문서 조회 시 예외")
        void getDocument_notFound_throwsException() {
            // Given
            UUID docId = UUID.randomUUID();
            when(documentService.findById(docId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> documentTools.getDocument(docId.toString(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Document not found");
        }

        @Test
        @DisplayName("searchDocuments - keyword 모드 검색")
        void searchDocuments_keywordMode_returnsResults() {
            // Given
            UUID projectId = UUID.randomUUID();
            var mockResult = new SearchService.SearchResult(
                UUID.randomUUID(),  // documentId
                UUID.randomUUID(),  // repositoryId
                "docs/test.md",     // path
                "abc123",           // commitSha
                null,               // chunkId
                "Test > Section",   // headingPath
                0.95,               // score
                "matching snippet", // snippet
                null                // highlightedSnippet
            );
            when(searchService.searchByKeyword(eq(projectId), eq("test query"), eq(10)))
                .thenReturn(List.of(mockResult));

            // When
            SearchDocumentsResult result = documentTools.searchDocuments(
                projectId.toString(), "test query", "keyword", 10
            );

            // Then
            assertThat(result.results()).hasSize(1);
            assertThat(result.results().get(0).score()).isEqualTo(0.95);
            assertThat(result.metadata().mode()).isEqualTo("keyword");
        }

        @Test
        @DisplayName("searchDocuments - semantic 모드 검색")
        void searchDocuments_semanticMode_usesSemanticService() {
            // Given
            UUID projectId = UUID.randomUUID();
            when(semanticSearchService.searchSemantic(eq(projectId), anyString(), anyInt()))
                .thenReturn(List.of());

            // When
            documentTools.searchDocuments(projectId.toString(), "query", "semantic", 5);

            // Then
            verify(semanticSearchService).searchSemantic(projectId, "query", 5);
            verifyNoInteractions(searchService);
        }

        @Test
        @DisplayName("searchDocuments - hybrid 모드 검색")
        void searchDocuments_hybridMode_usesHybridService() {
            // Given
            UUID projectId = UUID.randomUUID();
            when(hybridSearchService.hybridSearch(eq(projectId), anyString(), anyInt()))
                .thenReturn(List.of());

            // When
            documentTools.searchDocuments(projectId.toString(), "query", "hybrid", 5);

            // Then
            verify(hybridSearchService).hybridSearch(projectId, "query", 5);
        }

        @Test
        @DisplayName("diffDocument - 두 버전 비교")
        void diffDocument_returnsDiff() {
            // Given
            UUID docId = UUID.randomUUID();
            UUID repoId = UUID.randomUUID();
            Document mockDoc = createMockDocument(repoId);

            DocumentVersion fromVersion = mock(DocumentVersion.class);
            when(fromVersion.getContent()).thenReturn("Line 1\nLine 2");

            DocumentVersion toVersion = mock(DocumentVersion.class);
            when(toVersion.getContent()).thenReturn("Line 1\nLine 2 modified\nLine 3");

            when(documentService.findVersion(docId, "sha1")).thenReturn(Optional.of(fromVersion));
            when(documentService.findVersion(docId, "sha2")).thenReturn(Optional.of(toVersion));

            // When
            DiffDocumentResult result = documentTools.diffDocument(
                docId.toString(), "sha1", "sha2"
            );

            // Then
            assertThat(result.diff()).contains("--- sha1");
            assertThat(result.diff()).contains("+++ sha2");
            assertThat(result.diff()).contains("-Line 2");
            assertThat(result.diff()).contains("+Line 2 modified");
        }

        private Document createMockDocument(UUID repoId) {
            Repository repo = mock(Repository.class);
            when(repo.getId()).thenReturn(repoId);

            Document doc = mock(Document.class);
            when(doc.getId()).thenReturn(UUID.randomUUID());
            when(doc.getRepository()).thenReturn(repo);
            when(doc.getPath()).thenReturn("docs/test.md");
            when(doc.getTitle()).thenReturn("Test Document");
            when(doc.getDocType()).thenReturn(DocType.MD);
            when(doc.getLatestCommitSha()).thenReturn("abc123");

            return doc;
        }

        private DocumentVersion createMockVersion(Document doc) {
            DocumentVersion version = mock(DocumentVersion.class);
            when(version.getDocument()).thenReturn(doc);
            when(version.getCommitSha()).thenReturn("abc123");
            when(version.getContent()).thenReturn("# Test Document\n\nContent here.");
            when(version.getAuthorName()).thenReturn("Test Author");
            when(version.getCommittedAt()).thenReturn(Instant.now());

            return version;
        }
    }

    @Nested
    @DisplayName("McpProjectTools Tests")
    class ProjectToolsTests {

        @Mock
        private ProjectService projectService;

        private McpProjectTools projectTools;

        @BeforeEach
        void setUp() {
            projectTools = new McpProjectTools(projectService);
        }

        @Test
        @DisplayName("listProjects - 인증된 사용자의 프로젝트 목록 조회")
        void listProjects_returnsUserProjects() {
            // Given
            UUID userId = UUID.randomUUID();
            UUID projectId = UUID.randomUUID();
            UserPrincipal principal = new UserPrincipal(userId, "test@example.com", "Test User", null);

            var mockProject = mock(com.docst.domain.Project.class);
            when(mockProject.getId()).thenReturn(projectId);
            when(mockProject.getName()).thenReturn("Test Project");
            when(mockProject.getDescription()).thenReturn("A test project");

            var mockMember = mock(com.docst.domain.ProjectMember.class);
            when(mockMember.getRole()).thenReturn(com.docst.domain.ProjectRole.OWNER);

            when(projectService.findByMemberUserId(userId)).thenReturn(List.of(mockProject));
            when(projectService.findMember(projectId, userId)).thenReturn(Optional.of(mockMember));

            try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
                securityUtils.when(SecurityUtils::getCurrentUserPrincipal).thenReturn(principal);

                // When
                ListProjectsResult result = projectTools.listProjects();

                // Then
                assertThat(result.projects()).hasSize(1);
                assertThat(result.projects().get(0).name()).isEqualTo("Test Project");
                assertThat(result.projects().get(0).role()).isEqualTo("OWNER");
            }
        }

        @Test
        @DisplayName("listProjects - 인증되지 않은 경우 예외")
        void listProjects_unauthenticated_throwsException() {
            try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
                securityUtils.when(SecurityUtils::getCurrentUserPrincipal).thenReturn(null);

                // When & Then
                assertThatThrownBy(() -> projectTools.listProjects())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Authentication required");
            }
        }
    }

    @Nested
    @DisplayName("McpGitTools Tests")
    class GitToolsTests {

        @Mock
        private SyncService syncService;
        @Mock
        private DocumentWriteService documentWriteService;

        private McpGitTools gitTools;

        @BeforeEach
        void setUp() {
            gitTools = new McpGitTools(syncService, documentWriteService);
        }

        @Test
        @DisplayName("syncRepository - 동기화 작업 시작")
        void syncRepository_startsSync() {
            // Given
            UUID repoId = UUID.randomUUID();
            var mockJob = mock(com.docst.domain.SyncJob.class);
            when(mockJob.getId()).thenReturn(UUID.randomUUID());
            when(mockJob.getStatus()).thenReturn(com.docst.domain.SyncJob.SyncStatus.RUNNING);
            when(syncService.startSync(repoId, "main")).thenReturn(mockJob);

            // When
            SyncRepositoryResult result = gitTools.syncRepository(repoId.toString(), "main");

            // Then
            assertThat(result.status()).isEqualTo("RUNNING");
            verify(syncService).startSync(repoId, "main");
        }

        @Test
        @DisplayName("pushToRemote - 성공 시 결과 반환")
        void pushToRemote_success_returnsResult() {
            // Given
            UUID repoId = UUID.randomUUID();
            doNothing().when(documentWriteService).pushToRemote(repoId, "main");

            // When
            PushToRemoteResult result = gitTools.pushToRemote(repoId.toString(), "main");

            // Then
            assertThat(result.success()).isTrue();
            assertThat(result.message()).contains("Successfully");
        }

        @Test
        @DisplayName("pushToRemote - 실패 시 에러 메시지 반환")
        void pushToRemote_failure_returnsError() {
            // Given
            UUID repoId = UUID.randomUUID();
            doThrow(new RuntimeException("Push failed"))
                .when(documentWriteService).pushToRemote(repoId, "main");

            // When
            PushToRemoteResult result = gitTools.pushToRemote(repoId.toString(), "main");

            // Then
            assertThat(result.success()).isFalse();
            assertThat(result.message()).contains("Push failed");
        }
    }
}
