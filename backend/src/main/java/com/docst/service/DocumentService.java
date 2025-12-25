package com.docst.service;

import com.docst.domain.Document;
import com.docst.domain.Document.DocType;
import com.docst.domain.DocumentVersion;
import com.docst.domain.Repository;
import com.docst.repository.DocumentRepository;
import com.docst.repository.DocumentVersionRepository;
import com.docst.repository.RepositoryRepository;
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

@Service
@Transactional(readOnly = true)
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final RepositoryRepository repositoryRepository;

    public DocumentService(DocumentRepository documentRepository,
                           DocumentVersionRepository documentVersionRepository,
                           RepositoryRepository repositoryRepository) {
        this.documentRepository = documentRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.repositoryRepository = repositoryRepository;
    }

    public List<Document> findByRepositoryId(UUID repositoryId, String pathPrefix, String docType) {
        DocType type = docType != null ? DocType.valueOf(docType.toUpperCase()) : null;
        return documentRepository.findByRepositoryIdWithFilters(repositoryId, pathPrefix, type);
    }

    public List<Document> findByProjectId(UUID projectId) {
        return documentRepository.findByProjectId(projectId);
    }

    public Optional<Document> findById(UUID id) {
        return documentRepository.findById(id);
    }

    public Optional<DocumentVersion> findLatestVersion(UUID documentId) {
        return documentVersionRepository.findLatestByDocumentId(documentId);
    }

    public List<DocumentVersion> findVersions(UUID documentId) {
        return documentVersionRepository.findByDocumentIdOrderByCommittedAtDesc(documentId);
    }

    public Optional<DocumentVersion> findVersion(UUID documentId, String commitSha) {
        return documentVersionRepository.findByDocumentIdAndCommitSha(documentId, commitSha);
    }

    @Transactional
    public Document upsertDocument(UUID repositoryId, String path, String commitSha,
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

        // Check if this exact content already exists
        if (!documentVersionRepository.existsByDocumentIdAndContentHash(document.getId(), contentHash)) {
            DocumentVersion version = new DocumentVersion(document, commitSha);
            version.setAuthorName(authorName);
            version.setAuthorEmail(authorEmail);
            version.setCommittedAt(committedAt);
            version.setMessage(message);
            version.setContentHash(contentHash);
            version.setContent(content);

            document.addVersion(version);
        } else {
            document.setLatestCommitSha(commitSha);
        }

        return documentRepository.save(document);
    }

    @Transactional
    public void markDeleted(UUID repositoryId, String path) {
        documentRepository.findByRepositoryIdAndPath(repositoryId, path)
                .ifPresent(doc -> {
                    doc.setDeleted(true);
                    documentRepository.save(doc);
                });
    }

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
