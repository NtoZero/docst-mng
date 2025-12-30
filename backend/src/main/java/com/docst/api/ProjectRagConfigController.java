package com.docst.api;

import com.docst.api.ApiModels.*;
import com.docst.domain.Project;
import com.docst.embedding.ReEmbeddingService;
import com.docst.embedding.ReEmbeddingService.ReEmbeddingStatus;
import com.docst.rag.config.RagConfigDto;
import com.docst.rag.config.RagConfigService;
import com.docst.rag.config.RagGlobalProperties;
import com.docst.rag.config.ResolvedRagConfig;
import com.docst.repository.ProjectRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 프로젝트 RAG 설정 컨트롤러.
 * Phase 4-D: 프로젝트별 RAG 설정 관리 API
 */
@Tag(name = "RAG Config", description = "프로젝트별 RAG 설정 관리 API")
@RestController
@RequestMapping("/api/projects/{projectId}/rag-config")
@RequiredArgsConstructor
@Slf4j
public class ProjectRagConfigController {

    private final ProjectRepository projectRepository;
    private final RagConfigService ragConfigService;
    private final RagGlobalProperties globalProperties;
    private final ReEmbeddingService reEmbeddingService;

    /**
     * 프로젝트 RAG 설정 조회.
     * 프로젝트별 설정이 없으면 전역 기본값 반환
     */
    @Operation(
            summary = "프로젝트 RAG 설정 조회",
            description = "프로젝트의 RAG 설정을 조회합니다. 설정이 없으면 전역 기본값이 반환됩니다."
    )
    @ApiResponse(responseCode = "200", description = "설정 조회 성공")
    @ApiResponse(responseCode = "404", description = "프로젝트 없음")
    @GetMapping
    public ResponseEntity<ProjectRagConfigResponse> getRagConfig(@PathVariable UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        ResolvedRagConfig config = ragConfigService.resolve(project);
        return ResponseEntity.ok(toResponse(projectId, config, project.getCreatedAt()));
    }

    /**
     * 프로젝트 RAG 설정 업데이트.
     * 임베딩 모델 변경 시 재임베딩 필요 경고 반환
     */
    @Operation(
            summary = "프로젝트 RAG 설정 업데이트",
            description = "프로젝트의 RAG 설정을 업데이트합니다. 임베딩 모델이 변경되면 재임베딩이 필요합니다."
    )
    @ApiResponse(responseCode = "200", description = "설정 업데이트 성공")
    @ApiResponse(responseCode = "404", description = "프로젝트 없음")
    @PutMapping
    public ResponseEntity<ProjectRagConfigResponse> updateRagConfig(
            @PathVariable UUID projectId,
            @RequestBody UpdateProjectRagConfigRequest request
    ) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        // 기존 설정 조회
        RagConfigDto existingConfig = ragConfigService.parseProjectConfig(project);

        // 새 설정 생성 (기존 값과 요청 값 병합)
        RagConfigDto newConfig = mergeConfig(existingConfig, request);

        // 프로젝트에 저장
        project.setRagConfig(ragConfigService.toJson(newConfig));
        projectRepository.save(project);

        log.info("Updated RAG config for project {}: {}", projectId, newConfig);

        // 임베딩 모델 변경 감지 및 경고 (재임베딩은 4-D-5에서 구현)
        if (embeddingModelChanged(existingConfig, newConfig)) {
            log.warn("Embedding model changed for project {}. Re-embedding required.", projectId);
        }

        ResolvedRagConfig resolved = ragConfigService.resolve(project);
        return ResponseEntity.ok(toResponse(projectId, resolved, Instant.now()));
    }

    /**
     * RAG 설정 검증.
     * 설정의 유효성을 검사하고 오류/경고 반환
     */
    @Operation(
            summary = "RAG 설정 검증",
            description = "RAG 설정의 유효성을 검사합니다."
    )
    @ApiResponse(responseCode = "200", description = "검증 완료")
    @PostMapping("/validate")
    public ResponseEntity<RagConfigValidationResponse> validateConfig(
            @PathVariable UUID projectId,
            @RequestBody UpdateProjectRagConfigRequest request
    ) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 임베딩 설정 검증
        if (request.embedding() != null) {
            EmbeddingConfigRequest embedding = request.embedding();
            if (embedding.dimensions() != null && embedding.dimensions() <= 0) {
                errors.add("embedding.dimensions must be positive");
            }
            if (embedding.provider() != null && !List.of("openai", "ollama").contains(embedding.provider().toLowerCase())) {
                warnings.add("Unknown embedding provider: " + embedding.provider());
            }
        }

        // Neo4j 설정 검증
        if (request.neo4j() != null) {
            Neo4jConfigRequest neo4j = request.neo4j();
            if (neo4j.maxHop() != null && (neo4j.maxHop() < 1 || neo4j.maxHop() > 5)) {
                errors.add("neo4j.maxHop must be between 1 and 5");
            }
        }

        // Hybrid 설정 검증
        if (request.hybrid() != null) {
            HybridConfigRequest hybrid = request.hybrid();
            if (hybrid.rrfK() != null && hybrid.rrfK() <= 0) {
                errors.add("hybrid.rrfK must be positive");
            }
            if (hybrid.vectorWeight() != null && (hybrid.vectorWeight() < 0 || hybrid.vectorWeight() > 1)) {
                errors.add("hybrid.vectorWeight must be between 0 and 1");
            }
            if (hybrid.graphWeight() != null && (hybrid.graphWeight() < 0 || hybrid.graphWeight() > 1)) {
                errors.add("hybrid.graphWeight must be between 0 and 1");
            }
            if (hybrid.fusionStrategy() != null && !List.of("rrf", "weighted_sum").contains(hybrid.fusionStrategy().toLowerCase())) {
                errors.add("hybrid.fusionStrategy must be 'rrf' or 'weighted_sum'");
            }
        }

        // 기존 설정과 비교하여 임베딩 모델 변경 경고
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project != null && request.embedding() != null) {
            RagConfigDto existingConfig = ragConfigService.parseProjectConfig(project);
            RagConfigDto newConfig = mergeConfig(existingConfig, request);
            if (embeddingModelChanged(existingConfig, newConfig)) {
                warnings.add("Embedding model change detected. This will require re-embedding all documents.");
            }
        }

        return ResponseEntity.ok(new RagConfigValidationResponse(
                errors.isEmpty(),
                errors,
                warnings
        ));
    }

    /**
     * 전역 RAG 설정 기본값 조회.
     * application.yml에 정의된 기본값 반환
     */
    @Operation(
            summary = "RAG 설정 기본값 조회",
            description = "전역 RAG 설정 기본값을 조회합니다."
    )
    @ApiResponse(responseCode = "200", description = "기본값 조회 성공")
    @GetMapping("/defaults")
    public ResponseEntity<RagConfigDefaultsResponse> getDefaults(@PathVariable UUID projectId) {
        // projectId는 권한 검사용 (향후 구현)
        ResolvedRagConfig defaults = ResolvedRagConfig.defaults();

        return ResponseEntity.ok(new RagConfigDefaultsResponse(
                new EmbeddingConfigResponse(
                        defaults.getEmbeddingProvider(),
                        defaults.getEmbeddingModel(),
                        defaults.getEmbeddingDimensions()
                ),
                new PgVectorConfigResponse(
                        defaults.isPgvectorEnabled(),
                        defaults.getSimilarityThreshold()
                ),
                new Neo4jConfigResponse(
                        defaults.isNeo4jEnabled(),
                        defaults.getMaxHop(),
                        defaults.getEntityExtractionModel()
                ),
                new HybridConfigResponse(
                        defaults.getFusionStrategy(),
                        defaults.getRrfK(),
                        defaults.getVectorWeight(),
                        defaults.getGraphWeight()
                )
        ));
    }

    /**
     * 프로젝트 재임베딩 트리거.
     * 모든 문서의 임베딩을 삭제하고 다시 생성한다 (비동기).
     */
    @Operation(
            summary = "재임베딩 트리거",
            description = "프로젝트의 모든 문서를 재임베딩합니다. 임베딩 모델 변경 후 호출하세요. 비동기로 실행됩니다."
    )
    @ApiResponse(responseCode = "202", description = "재임베딩 시작됨")
    @ApiResponse(responseCode = "409", description = "이미 재임베딩 진행 중")
    @PostMapping("/re-embed")
    public ResponseEntity<ReEmbeddingTriggerResponse> triggerReEmbedding(@PathVariable UUID projectId) {
        // 프로젝트 존재 확인
        projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        // 이미 진행 중인지 확인
        ReEmbeddingStatus status = reEmbeddingService.getStatus(projectId);
        if (status != null && status.isInProgress()) {
            return ResponseEntity.status(409).body(new ReEmbeddingTriggerResponse(
                    projectId,
                    "Re-embedding already in progress",
                    true
            ));
        }

        // 비동기 재임베딩 시작
        reEmbeddingService.reEmbedProjectAsync(projectId);

        log.info("Re-embedding triggered for project {}", projectId);

        return ResponseEntity.accepted().body(new ReEmbeddingTriggerResponse(
                projectId,
                "Re-embedding started",
                true
        ));
    }

    /**
     * 재임베딩 진행 상태 조회.
     */
    @Operation(
            summary = "재임베딩 상태 조회",
            description = "프로젝트의 재임베딩 진행 상태를 조회합니다."
    )
    @ApiResponse(responseCode = "200", description = "상태 조회 성공")
    @ApiResponse(responseCode = "404", description = "진행 중인 작업 없음")
    @GetMapping("/re-embed/status")
    public ResponseEntity<ReEmbeddingStatusResponse> getReEmbeddingStatus(@PathVariable UUID projectId) {
        ReEmbeddingStatus status = reEmbeddingService.getStatus(projectId);

        if (status == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(new ReEmbeddingStatusResponse(
                status.getProjectId(),
                status.isInProgress(),
                status.getTotalVersions(),
                status.getProcessedVersions(),
                status.getProgress(),
                status.getDeletedEmbeddings(),
                status.getEmbeddedCount(),
                status.getFailedCount(),
                status.getErrorMessage()
        ));
    }

    // ===== Helper methods =====

    private ProjectRagConfigResponse toResponse(UUID projectId, ResolvedRagConfig config, Instant updatedAt) {
        return new ProjectRagConfigResponse(
                projectId,
                new EmbeddingConfigResponse(
                        config.getEmbeddingProvider(),
                        config.getEmbeddingModel(),
                        config.getEmbeddingDimensions()
                ),
                new PgVectorConfigResponse(
                        config.isPgvectorEnabled(),
                        config.getSimilarityThreshold()
                ),
                new Neo4jConfigResponse(
                        config.isNeo4jEnabled(),
                        config.getMaxHop(),
                        config.getEntityExtractionModel()
                ),
                new HybridConfigResponse(
                        config.getFusionStrategy(),
                        config.getRrfK(),
                        config.getVectorWeight(),
                        config.getGraphWeight()
                ),
                updatedAt
        );
    }

    private RagConfigDto mergeConfig(RagConfigDto existing, UpdateProjectRagConfigRequest request) {
        RagConfigDto.EmbeddingConfig embeddingConfig = null;
        if (request.embedding() != null) {
            EmbeddingConfigRequest req = request.embedding();
            RagConfigDto.EmbeddingConfig ex = existing != null ? existing.embedding() : null;
            embeddingConfig = new RagConfigDto.EmbeddingConfig(
                    req.provider() != null ? req.provider() : (ex != null ? ex.provider() : null),
                    req.model() != null ? req.model() : (ex != null ? ex.model() : null),
                    req.dimensions() != null ? req.dimensions() : (ex != null ? ex.dimensions() : null)
            );
        } else if (existing != null) {
            embeddingConfig = existing.embedding();
        }

        RagConfigDto.PgVectorConfig pgvectorConfig = null;
        if (request.pgvector() != null) {
            PgVectorConfigRequest req = request.pgvector();
            RagConfigDto.PgVectorConfig ex = existing != null ? existing.pgvector() : null;
            pgvectorConfig = new RagConfigDto.PgVectorConfig(
                    req.enabled() != null ? req.enabled() : (ex != null ? ex.enabled() : null),
                    req.similarityThreshold() != null ? req.similarityThreshold() : (ex != null ? ex.similarityThreshold() : null)
            );
        } else if (existing != null) {
            pgvectorConfig = existing.pgvector();
        }

        RagConfigDto.Neo4jConfig neo4jConfig = null;
        if (request.neo4j() != null) {
            Neo4jConfigRequest req = request.neo4j();
            RagConfigDto.Neo4jConfig ex = existing != null ? existing.neo4j() : null;
            neo4jConfig = new RagConfigDto.Neo4jConfig(
                    req.enabled() != null ? req.enabled() : (ex != null ? ex.enabled() : null),
                    req.maxHop() != null ? req.maxHop() : (ex != null ? ex.maxHop() : null),
                    req.entityExtractionModel() != null ? req.entityExtractionModel() : (ex != null ? ex.entityExtractionModel() : null)
            );
        } else if (existing != null) {
            neo4jConfig = existing.neo4j();
        }

        RagConfigDto.HybridConfig hybridConfig = null;
        if (request.hybrid() != null) {
            HybridConfigRequest req = request.hybrid();
            RagConfigDto.HybridConfig ex = existing != null ? existing.hybrid() : null;
            hybridConfig = new RagConfigDto.HybridConfig(
                    req.fusionStrategy() != null ? req.fusionStrategy() : (ex != null ? ex.fusionStrategy() : null),
                    req.rrfK() != null ? req.rrfK() : (ex != null ? ex.rrfK() : null),
                    req.vectorWeight() != null ? req.vectorWeight() : (ex != null ? ex.vectorWeight() : null),
                    req.graphWeight() != null ? req.graphWeight() : (ex != null ? ex.graphWeight() : null)
            );
        } else if (existing != null) {
            hybridConfig = existing.hybrid();
        }

        return new RagConfigDto(
                "1.0",
                embeddingConfig,
                pgvectorConfig,
                neo4jConfig,
                hybridConfig
        );
    }

    private boolean embeddingModelChanged(RagConfigDto existing, RagConfigDto newConfig) {
        if (existing == null || existing.embedding() == null) {
            return newConfig != null && newConfig.embedding() != null;
        }
        if (newConfig == null || newConfig.embedding() == null) {
            return false;
        }

        RagConfigDto.EmbeddingConfig ex = existing.embedding();
        RagConfigDto.EmbeddingConfig nw = newConfig.embedding();

        boolean providerChanged = nw.provider() != null && !nw.provider().equals(ex.provider());
        boolean modelChanged = nw.model() != null && !nw.model().equals(ex.model());

        return providerChanged || modelChanged;
    }
}
