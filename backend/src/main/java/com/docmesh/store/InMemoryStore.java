package com.docmesh.store;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class InMemoryStore {
  private final Map<UUID, Project> projects = new ConcurrentHashMap<>();
  private final Map<UUID, Repository> repositories = new ConcurrentHashMap<>();
  private final Map<UUID, Document> documents = new ConcurrentHashMap<>();
  private final Map<UUID, DocumentVersion> documentVersions = new ConcurrentHashMap<>();
  private final Map<UUID, SyncJob> syncJobs = new ConcurrentHashMap<>();

  public List<Project> listProjects() {
    return projects.values().stream()
        .sorted(Comparator.comparing(Project::createdAt))
        .toList();
  }

  public Project createProject(String name, String description) {
    UUID id = UUID.randomUUID();
    Project project = new Project(id, name, description, true, Instant.now());
    projects.put(id, project);
    return project;
  }

  public Optional<Project> getProject(UUID projectId) {
    return Optional.ofNullable(projects.get(projectId));
  }

  public Optional<Project> updateProject(UUID projectId, String name, String description, Boolean active) {
    Project current = projects.get(projectId);
    if (current == null) {
      return Optional.empty();
    }
    Project updated = new Project(
        current.id(),
        name != null ? name : current.name(),
        description != null ? description : current.description(),
        active != null ? active : current.active(),
        current.createdAt()
    );
    projects.put(projectId, updated);
    return Optional.of(updated);
  }

  public void deleteProject(UUID projectId) {
    projects.remove(projectId);
    repositories.values().removeIf(repo -> repo.projectId().equals(projectId));
  }

  public List<Repository> listRepositories(UUID projectId) {
    return repositories.values().stream()
        .filter(repo -> repo.projectId().equals(projectId))
        .sorted(Comparator.comparing(Repository::createdAt))
        .toList();
  }

  public Optional<Repository> getRepository(UUID repositoryId) {
    return Optional.ofNullable(repositories.get(repositoryId));
  }

  public Repository createRepository(UUID projectId, String provider, String owner, String name, String defaultBranch, String localPath) {
    UUID id = UUID.randomUUID();
    Repository repo = new Repository(
        id,
        projectId,
        provider,
        null,
        owner,
        name,
        provider.equalsIgnoreCase("LOCAL") ? null : "https://github.com/%s/%s.git".formatted(owner, name),
        defaultBranch != null ? defaultBranch : "main",
        localPath,
        true,
        Instant.now()
    );
    repositories.put(id, repo);

    seedDocument(repo);
    return repo;
  }

  public Optional<Repository> updateRepository(UUID repositoryId, Boolean active, String defaultBranch) {
    Repository current = repositories.get(repositoryId);
    if (current == null) {
      return Optional.empty();
    }
    Repository updated = new Repository(
        current.id(),
        current.projectId(),
        current.provider(),
        current.externalId(),
        current.owner(),
        current.name(),
        current.cloneUrl(),
        defaultBranch != null ? defaultBranch : current.defaultBranch(),
        current.localMirrorPath(),
        active != null ? active : current.active(),
        current.createdAt()
    );
    repositories.put(repositoryId, updated);
    return Optional.of(updated);
  }

  public void deleteRepository(UUID repositoryId) {
    repositories.remove(repositoryId);
    documents.values().removeIf(doc -> doc.repositoryId().equals(repositoryId));
    documentVersions.values().removeIf(version -> {
      Document doc = documents.get(version.documentId());
      return doc == null || doc.repositoryId().equals(repositoryId);
    });
  }

  public List<Document> listDocuments(UUID repositoryId, String pathPrefix, String type) {
    return documents.values().stream()
        .filter(doc -> doc.repositoryId().equals(repositoryId))
        .filter(doc -> pathPrefix == null || doc.path().startsWith(pathPrefix))
        .filter(doc -> type == null || doc.docType().equalsIgnoreCase(type))
        .sorted(Comparator.comparing(Document::path))
        .toList();
  }

  public Optional<Document> getDocument(UUID documentId) {
    return Optional.ofNullable(documents.get(documentId));
  }

  public Optional<DocumentVersion> getLatestVersion(UUID documentId) {
    Document doc = documents.get(documentId);
    if (doc == null || doc.latestCommitSha() == null) {
      return Optional.empty();
    }
    return documentVersions.values().stream()
        .filter(version -> version.documentId().equals(documentId))
        .filter(version -> version.commitSha().equals(doc.latestCommitSha()))
        .findFirst();
  }

  public List<DocumentVersion> listDocumentVersions(UUID documentId) {
    return documentVersions.values().stream()
        .filter(version -> version.documentId().equals(documentId))
        .sorted(Comparator.comparing(DocumentVersion::committedAt).reversed())
        .toList();
  }

  public Optional<DocumentVersion> getDocumentVersion(UUID documentId, String commitSha) {
    return documentVersions.values().stream()
        .filter(version -> version.documentId().equals(documentId))
        .filter(version -> version.commitSha().equals(commitSha))
        .findFirst();
  }

  public SyncJob createSyncJob(UUID repositoryId, String branch) {
    SyncJob job = new SyncJob(
        UUID.randomUUID(),
        repositoryId,
        "RUNNING",
        branch,
        null,
        null,
        Instant.now(),
        null,
        Instant.now()
    );
    syncJobs.put(job.id(), job);
    return job;
  }

  public SyncJob completeSyncJob(UUID jobId, String lastCommit) {
    SyncJob current = syncJobs.get(jobId);
    if (current == null) {
      return null;
    }
    SyncJob updated = new SyncJob(
        current.id(),
        current.repositoryId(),
        "SUCCEEDED",
        current.targetBranch(),
        lastCommit,
        null,
        current.startedAt(),
        Instant.now(),
        current.createdAt()
    );
    syncJobs.put(jobId, updated);
    return updated;
  }

  public Optional<SyncJob> getSyncJob(UUID jobId) {
    return Optional.ofNullable(syncJobs.get(jobId));
  }

  public List<DocumentVersion> searchDocuments(UUID projectId, String query) {
    List<UUID> repoIds = repositories.values().stream()
        .filter(repo -> repo.projectId().equals(projectId))
        .map(Repository::id)
        .toList();

    return documentVersions.values().stream()
        .filter(version -> {
          Document doc = documents.get(version.documentId());
          return doc != null && repoIds.contains(doc.repositoryId());
        })
        .filter(version -> version.content() != null && version.content().toLowerCase().contains(query.toLowerCase()))
        .sorted(Comparator.comparing(DocumentVersion::committedAt).reversed())
        .collect(Collectors.toList());
  }

  private void seedDocument(Repository repository) {
    UUID docId = UUID.randomUUID();
    String content = "# " + repository.name() + "\n\nDocMesh sample document for " + repository.owner();
    String commitSha = randomCommitSha();
    Document doc = new Document(
        docId,
        repository.id(),
        "README.md",
        repository.name() + " Overview",
        "MD",
        commitSha,
        Instant.now()
    );
    documents.put(docId, doc);

    DocumentVersion version = new DocumentVersion(
        UUID.randomUUID(),
        docId,
        commitSha,
        "DocMesh",
        "docmesh@example.com",
        Instant.now(),
        "Initial import",
        hashContent(content),
        content
    );
    documentVersions.put(version.id(), version);
  }

  private String randomCommitSha() {
    return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
  }

  private String hashContent(String content) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    } catch (Exception ex) {
      return null;
    }
  }
}
