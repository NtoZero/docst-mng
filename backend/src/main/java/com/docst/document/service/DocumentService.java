package com.docst.document.service;

import com.docst.document.Document;
import com.docst.document.Document.DocType;
import com.docst.document.DocumentVersion;
import com.docst.document.repository.DocumentRepository;
import com.docst.document.repository.DocumentVersionRepository;
import com.docst.gitrepo.Repository;
import com.docst.gitrepo.repository.RepositoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 문서 서비스.
 * 문서 및 버전 관리에 대한 비즈니스 로직을 담당한다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final RepositoryRepository repositoryRepository;

    /**
     * 레포지토리의 문서를 필터링하여 조회한다.
     *
     * @param repositoryId 레포지토리 ID
     * @param pathPrefix 경로 접두사 (null이면 전체)
     * @param docType 문서 타입 문자열 (null이면 전체)
     * @return 문서 목록
     */
    public List<Document> findByRepositoryId(UUID repositoryId, String pathPrefix, String docType) {
        DocType type = docType != null ? DocType.valueOf(docType.toUpperCase()) : null;
        // pathPrefix가 있으면 LIKE 패턴으로 변환 (LIKE 특수문자 이스케이프 처리)
        String pathPattern = null;
        if (pathPrefix != null) {
            String escaped = escapeLikePattern(pathPrefix);
            pathPattern = escaped + "%";
        }
        return documentRepository.findByRepositoryIdWithFilters(repositoryId, pathPattern, type);
    }

    /**
     * LIKE 패턴에서 사용되는 특수문자를 이스케이프 처리한다.
     * Wildcard Injection 방지를 위해 %, _, ! 문자를 이스케이프한다.
     * ESCAPE 문자로 '!'를 사용한다.
     *
     * @param input 원본 문자열
     * @return 이스케이프된 문자열
     */
    private String escapeLikePattern(String input) {
        return input
                .replace("!", "!!")     // 이스케이프 문자 먼저 처리
                .replace("%", "!%")
                .replace("_", "!_");
    }

    /**
     * 프로젝트에 속한 모든 문서를 조회한다.
     *
     * @param projectId 프로젝트 ID
     * @return 문서 목록
     */
    public List<Document> findByProjectId(UUID projectId) {
        return documentRepository.findByProjectId(projectId);
    }

    /**
     * ID로 문서를 조회한다.
     *
     * @param id 문서 ID
     * @return 문서 (존재하지 않으면 empty)
     */
    public Optional<Document> findById(UUID id) {
        return documentRepository.findById(id);
    }

    /**
     * 문서의 최신 버전을 조회한다.
     *
     * @param documentId 문서 ID
     * @return 최신 버전 (존재하지 않으면 empty)
     */
    public Optional<DocumentVersion> findLatestVersion(UUID documentId) {
        return documentVersionRepository.findLatestByDocumentId(documentId);
    }

    /**
     * 문서의 모든 버전을 조회한다.
     *
     * @param documentId 문서 ID
     * @return 버전 목록 (최신순)
     */
    public List<DocumentVersion> findVersions(UUID documentId) {
        return documentVersionRepository.findByDocumentIdOrderByCommittedAtDesc(documentId);
    }

    /**
     * 문서의 특정 커밋 버전을 조회한다.
     *
     * @param documentId 문서 ID
     * @param commitSha 커밋 SHA
     * @return 버전 (존재하지 않으면 empty)
     */
    public Optional<DocumentVersion> findVersion(UUID documentId, String commitSha) {
        return documentVersionRepository.findByDocumentIdAndCommitSha(documentId, commitSha);
    }

    /**
     * 문서를 생성하거나 업데이트한다.
     * 동일한 경로의 문서가 있으면 업데이트하고, 없으면 새로 생성한다.
     * 내용이 변경된 경우에만 새 버전을 추가한다.
     *
     * @param repositoryId 레포지토리 ID
     * @param path 파일 경로
     * @param commitSha 커밋 SHA
     * @param content 문서 내용
     * @param authorName 작성자 이름
     * @param authorEmail 작성자 이메일
     * @param committedAt 커밋 시각
     * @param message 커밋 메시지
     * @return 새로 생성된 문서 버전 (기존과 동일한 내용이면 null)
     * @throws IllegalArgumentException 레포지토리가 존재하지 않을 경우
     */
    @Transactional
    public DocumentVersion upsertDocument(UUID repositoryId, String path, String commitSha,
                                    String content, String authorName, String authorEmail,
                                    Instant committedAt, String message) {
        Repository repo = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repositoryId));

        String title = extractTitle(path, content);
        DocType docType = detectDocType(path);
        String contentHash = hashContent(content);

        Document document = documentRepository.findByRepositoryIdAndPath(repositoryId, path)
                .map(doc -> {
                    doc.setTitle(title);
                    doc.setDeleted(false);
                    return doc;
                })
                .orElseGet(() -> new Document(repo, path, title, docType));

        // Save document first to ensure it has an ID (for new documents)
        // and to flush any pending changes (for existing documents)
        document = documentRepository.saveAndFlush(document);

        DocumentVersion newVersion = null;

        // Check if this exact content already exists
        if (!documentVersionRepository.existsByDocumentIdAndContentHash(document.getId(), contentHash)) {
            DocumentVersion version = new DocumentVersion(document, commitSha);
            version.setAuthorName(authorName);
            version.setAuthorEmail(authorEmail);
            version.setCommittedAt(committedAt);
            version.setMessage(message);
            version.setContentHash(contentHash);
            version.setContent(content);

            // Explicitly save the version to avoid TransientObjectException
            // when subsequent queries trigger auto-flush
            newVersion = documentVersionRepository.saveAndFlush(version);

            // Update document's latest commit SHA and add version to collection
            document.setLatestCommitSha(commitSha);
            document.getVersions().add(newVersion);
        } else {
            document.setLatestCommitSha(commitSha);
            documentRepository.save(document);
        }

        return newVersion;
    }

    /**
     * 문서를 삭제 상태로 표시한다.
     *
     * @param repositoryId 레포지토리 ID
     * @param path 파일 경로
     */
    @Transactional
    public void markDeleted(UUID repositoryId, String path) {
        documentRepository.findByRepositoryIdAndPath(repositoryId, path)
                .ifPresent(doc -> {
                    doc.setDeleted(true);
                    documentRepository.save(doc);
                });
    }

    /**
     * 문서 내용에서 제목을 추출한다.
     * 마크다운 h1 헤딩을 찾아 반환하고, 없으면 파일명을 반환한다.
     */
    private String extractTitle(String path, String content) {
        // Try to extract title from markdown heading
        if (content != null) {
            String[] lines = content.split("\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("# ")) {
                    return trimmed.substring(2).trim();
                }
            }
        }

        // Fallback to filename
        String fileName = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        if (fileName.contains(".")) {
            fileName = fileName.substring(0, fileName.lastIndexOf('.'));
        }
        return fileName;
    }

    /**
     * 파일 경로에서 문서 타입을 감지한다.
     */
    private DocType detectDocType(String path) {
        String lowerPath = path.toLowerCase();
        if (lowerPath.endsWith(".md")) return DocType.MD;
        if (lowerPath.endsWith(".adoc") || lowerPath.endsWith(".asciidoc")) return DocType.ADOC;
        if (lowerPath.contains("openapi") && (lowerPath.endsWith(".yaml") || lowerPath.endsWith(".yml") || lowerPath.endsWith(".json"))) {
            return DocType.OPENAPI;
        }
        if (lowerPath.contains("/adr/") || lowerPath.contains("/adrs/")) return DocType.ADR;
        return DocType.OTHER;
    }

    /**
     * 문서 내용의 SHA-256 해시를 계산한다.
     */
    private String hashContent(String content) {
        if (content == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
