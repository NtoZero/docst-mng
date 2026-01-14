# Target Architecture - Domain-Based Structure

## Overview

목표 아키텍처는 **도메인 기반 패키지 구조**로, 각 도메인이 자체적인 Entity, Repository, Service, Controller, DTO를 포함합니다.

## Architecture Diagram

```mermaid
graph TB
    subgraph "com.docst"
        APP[DocstApplication.java]

        subgraph "Core Domains"
            subgraph "user/"
                U_E[User.java]
                U_R[UserRepository.java]
                U_S[UserService.java]
            end

            subgraph "project/"
                P_E[Project.java<br/>ProjectMember.java<br/>ProjectRole.java]
                P_R[ProjectRepository.java<br/>ProjectMemberRepository.java]
                P_S[ProjectService.java]
                P_API[api/<br/>ProjectsController.java<br/>ProjectModels.java]
            end

            subgraph "gitrepo/"
                GR_E[Repository.java<br/>RepositorySyncConfig.java]
                GR_R[RepositoryRepository.java]
                GR_S[RepositoryService.java<br/>FolderTreeService.java]
                GR_API[api/<br/>RepositoriesController.java<br/>RepositoryModels.java]
            end

            subgraph "document/"
                D_E[Document.java<br/>DocumentVersion.java<br/>DocChunk.java<br/>DocumentLink.java]
                D_R[DocumentRepository.java<br/>DocumentVersionRepository.java<br/>DocChunkRepository.java<br/>DocumentLinkRepository.java]
                D_S[DocumentService.java<br/>DocumentWriteService.java<br/>DocumentLinkService.java]
                D_API[api/<br/>DocumentsController.java<br/>DocumentModels.java]
            end
        end

        subgraph "Feature Domains"
            subgraph "sync/"
                SY_E[SyncJob.java]
                SY_R[SyncJobRepository.java]
                SY_S[SyncService.java<br/>GitSyncService.java<br/>SyncProgressTracker.java]
                SY_API[api/<br/>SyncController.java<br/>SyncModels.java]
            end

            subgraph "credential/"
                CR_E[Credential.java<br/>CredentialScope.java]
                CR_R[CredentialRepository.java]
                CR_S[CredentialService.java<br/>DynamicCredentialResolver.java<br/>EncryptionService.java]
                CR_API[api/<br/>CredentialController.java<br/>AdminCredentialController.java<br/>ProjectCredentialController.java<br/>CredentialModels.java]
            end

            subgraph "search/"
                SE_S[SearchService.java<br/>SemanticSearchService.java<br/>HybridSearchService.java<br/>GraphService.java]
                SE_API[api/<br/>SearchController.java<br/>GraphController.java<br/>SearchModels.java]
            end

            subgraph "admin/"
                AD_E[SystemConfig.java]
                AD_R[SystemConfigRepository.java]
                AD_S[SystemConfigService.java]
                AD_API[api/<br/>AdminConfigController.java<br/>AdminHealthController.java<br/>AdminPgVectorController.java<br/>SetupController.java<br/>AdminModels.java]
            end

            subgraph "stats/"
                ST_S[StatsService.java]
                ST_API[api/StatsController.java]
            end

            subgraph "commit/"
                CM_S[CommitService.java]
                CM_API[api/CommitController.java]
            end

            subgraph "apikey/"
                AK_E[ApiKey.java]
                AK_R[ApiKeyRepository.java]
                AK_S[ApiKeyService.java]
            end
        end

        subgraph "Infrastructure Domains (Existing)"
            AUTH[auth/<br/>+ api/AuthController.java]
            GIT[git/]
            LLM[llm/<br/>+ api/LlmController.java]
            RAG[rag/<br/>+ api/ProjectRagConfigController.java]
            EMB[embedding/]
            CHK[chunking/]
            MCP[mcp/]
            WH[webhook/]
        end

        subgraph "Shared"
            COMMON[common/<br/>exception/GlobalExceptionHandler.java]
            CONFIG[config/<br/>SecurityConfig.java<br/>WebConfig.java<br/>etc.]
        end
    end
```

## Package Structure (Text)

```
com.docst/
├── DocstApplication.java
│
├── user/                              # User Domain
│   ├── User.java                      # Entity
│   ├── UserRepository.java            # Repository
│   └── UserService.java               # Service
│
├── project/                           # Project Domain
│   ├── Project.java                   # Entity
│   ├── ProjectMember.java             # Entity
│   ├── ProjectRole.java               # Enum
│   ├── ProjectRepository.java         # Repository
│   ├── ProjectMemberRepository.java   # Repository
│   ├── ProjectService.java            # Service
│   └── api/
│       ├── ProjectsController.java    # REST Controller
│       └── ProjectModels.java         # Request/Response DTOs
│
├── gitrepo/                           # Git Repository Domain
│   ├── Repository.java                # Entity (Git repo metadata)
│   ├── RepositorySyncConfig.java      # Embedded Value Object
│   ├── RepositoryRepository.java      # JPA Repository
│   ├── RepositoryService.java         # Service
│   ├── FolderTreeService.java         # Service
│   └── api/
│       ├── RepositoriesController.java
│       └── RepositoryModels.java
│
├── document/                          # Document Domain
│   ├── Document.java                  # Entity
│   ├── DocumentVersion.java           # Entity
│   ├── DocChunk.java                  # Entity (for semantic search)
│   ├── DocumentLink.java              # Entity (for graph)
│   ├── DocumentRepository.java        # Repository
│   ├── DocumentVersionRepository.java # Repository
│   ├── DocChunkRepository.java        # Repository
│   ├── DocumentLinkRepository.java    # Repository
│   ├── DocumentService.java           # Read Service
│   ├── DocumentWriteService.java      # Write Service
│   ├── DocumentLinkService.java       # Link Service
│   └── api/
│       ├── DocumentsController.java
│       └── DocumentModels.java
│
├── sync/                              # Sync Domain
│   ├── SyncJob.java                   # Entity
│   ├── SyncJobRepository.java         # Repository
│   ├── SyncService.java               # Orchestration Service
│   ├── GitSyncService.java            # Git sync execution
│   ├── SyncProgressTracker.java       # SSE progress tracking
│   └── api/
│       ├── SyncController.java
│       └── SyncModels.java
│
├── credential/                        # Credential Domain
│   ├── Credential.java                # Entity
│   ├── CredentialScope.java           # Enum
│   ├── CredentialRepository.java      # Repository
│   ├── CredentialService.java         # CRUD Service
│   ├── DynamicCredentialResolver.java # Scope-based resolution
│   ├── EncryptionService.java         # AES-256 encryption
│   └── api/
│       ├── CredentialController.java  # User credentials
│       ├── AdminCredentialController.java  # System credentials
│       ├── ProjectCredentialController.java # Project credentials
│       └── CredentialModels.java
│
├── search/                            # Search Domain
│   ├── SearchService.java             # Keyword search
│   ├── SemanticSearchService.java     # Vector search
│   ├── HybridSearchService.java       # Combined search
│   ├── GraphService.java              # Graph traversal
│   └── api/
│       ├── SearchController.java
│       ├── GraphController.java
│       └── SearchModels.java
│
├── admin/                             # Admin Domain
│   ├── SystemConfig.java              # Entity
│   ├── SystemConfigRepository.java    # Repository
│   ├── SystemConfigService.java       # Service
│   └── api/
│       ├── AdminConfigController.java
│       ├── AdminHealthController.java
│       ├── AdminPgVectorController.java
│       ├── SetupController.java
│       └── AdminModels.java
│
├── stats/                             # Stats Domain
│   ├── StatsService.java
│   └── api/
│       ├── StatsController.java
│       └── StatsModels.java
│
├── commit/                            # Commit Domain
│   ├── CommitService.java
│   └── api/
│       ├── CommitController.java
│       └── CommitModels.java
│
├── apikey/                            # API Key Domain
│   ├── ApiKey.java                    # Entity
│   ├── ApiKeyRepository.java          # Repository
│   └── ApiKeyService.java             # Service
│
├── auth/                              # Auth Domain (existing + api/)
│   ├── JwtService.java
│   ├── JwtConfig.java
│   ├── JwtAuthenticationFilter.java
│   ├── ApiKeyAuthenticationFilter.java
│   ├── SecurityUtils.java
│   ├── UserPrincipal.java
│   ├── PermissionService.java
│   ├── ProjectPermissionAspect.java
│   ├── RequireProjectRole.java
│   ├── RequireRepositoryAccess.java
│   ├── PasswordValidator.java
│   ├── AdminInitializer.java
│   ├── GitHubOAuthService.java
│   ├── GitHubOAuthController.java
│   └── api/
│       └── AuthController.java        # Moved from api/
│
├── git/                               # Git Domain (existing)
│   ├── GitService.java
│   ├── GitWriteService.java
│   ├── BranchService.java
│   ├── GitFileScanner.java
│   ├── GitCommitWalker.java
│   ├── DocumentParser.java
│   └── LinkParser.java
│
├── llm/                               # LLM Domain (existing + api/)
│   ├── LlmService.java
│   ├── DynamicChatClientFactory.java
│   ├── LlmConfig.java
│   ├── LlmProvider.java
│   ├── PromptTemplate.java
│   ├── RateLimitService.java
│   ├── CitationCollector.java
│   ├── tools/
│   ├── model/
│   └── api/
│       └── LlmController.java         # Moved from api/
│
├── rag/                               # RAG Domain (existing + api/)
│   ├── RagMode.java
│   ├── RagSearchStrategy.java
│   ├── config/
│   ├── pgvector/
│   ├── neo4j/
│   ├── hybrid/
│   └── api/
│       └── ProjectRagConfigController.java  # Moved from api/
│
├── embedding/                         # Embedding Domain (existing)
│   ├── DynamicEmbeddingClientFactory.java
│   ├── DocstEmbeddingService.java
│   └── ReEmbeddingService.java
│
├── chunking/                          # Chunking Domain (existing)
│   ├── ChunkingService.java
│   ├── MarkdownChunker.java
│   ├── ChunkResult.java
│   ├── ChunkingConfig.java
│   └── TokenCounter.java
│
├── mcp/                               # MCP Domain (existing)
│   ├── McpModels.java
│   └── tools/
│
├── webhook/                           # Webhook Domain (existing)
│   ├── GitHubWebhookController.java
│   └── WebhookService.java
│
├── common/                            # Shared Components
│   └── exception/
│       └── GlobalExceptionHandler.java
│
└── config/                            # Global Configuration
    ├── SecurityConfig.java
    ├── WebConfig.java
    ├── CorsConfig.java
    ├── CorsProperties.java
    ├── OpenApiConfig.java
    ├── AdminProperties.java
    ├── VectorStoreConfig.java
    └── DynamicNeo4jConfig.java
```

## Benefits of Target Architecture

### 1. High Cohesion
- 도메인 관련 모든 코드가 한 패키지에 위치
- 변경 범위가 도메인 내로 제한됨

### 2. Clear Domain Boundaries
- 패키지 구조가 곧 도메인 경계
- 새 개발자도 구조 파악이 쉬움

### 3. Easy Navigation
- 특정 도메인 작업 시 해당 패키지만 확인
- IDE 탐색이 직관적

### 4. Distributed DTOs
- 각 도메인에 `api/XxxModels.java`로 DTO 분리
- 관리 용이성 향상

### 5. Future-Ready
- 마이크로서비스 분리 시 도메인 단위로 가능
- 도메인 간 의존성이 명확

## Domain Layer Classification

```mermaid
graph TD
    subgraph "Core Layer"
        USER[user]
        PROJECT[project]
        GITREPO[gitrepo]
        DOCUMENT[document]
    end

    subgraph "Feature Layer"
        SYNC[sync]
        SEARCH[search]
        CREDENTIAL[credential]
        ADMIN[admin]
        STATS[stats]
        COMMIT[commit]
        APIKEY[apikey]
    end

    subgraph "Infrastructure Layer"
        AUTH[auth]
        GIT[git]
        LLM[llm]
        RAG[rag]
        EMBEDDING[embedding]
        CHUNKING[chunking]
        MCP[mcp]
        WEBHOOK[webhook]
    end

    subgraph "Shared Layer"
        COMMON[common]
        CONFIG[config]
    end
```

## Comparison

| Aspect | Before (Layer) | After (Domain) |
|--------|----------------|----------------|
| File location | 4 directories | 1 directory |
| Related code | Scattered | Grouped |
| New feature | Touch 4 packages | Touch 1 package |
| Domain boundary | Implicit | Explicit |
| DTO management | 1 huge file | Per-domain files |
| Navigation | Hard | Easy |
| Microservice-ready | No | Yes |
