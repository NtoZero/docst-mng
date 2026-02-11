package com.docst.glossary.api;

import com.docst.auth.RequireProjectRole;
import com.docst.auth.SecurityUtils;
import com.docst.auth.UserPrincipal;
import com.docst.glossary.GlossaryTerm;
import com.docst.glossary.service.GlossaryService;
import com.docst.glossary.service.GlossaryService.GlossaryTermWithScore;
import com.docst.project.ProjectRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 용어 사전 컨트롤러.
 * 프로젝트별 용어 CRUD 및 검색 기능을 제공한다.
 */
@Tag(name = "Glossary", description = "프로젝트 용어 사전 API")
@RestController
@RequestMapping("/api/projects/{projectId}/glossary")
@RequiredArgsConstructor
public class GlossaryController {

    private final GlossaryService glossaryService;

    /**
     * 프로젝트의 용어 목록을 조회한다.
     *
     * @param projectId 프로젝트 ID
     * @param category 카테고리 필터 (선택)
     * @return 용어 목록
     */
    @Operation(summary = "용어 목록 조회", description = "프로젝트의 모든 용어를 조회합니다. 카테고리로 필터링할 수 있습니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping
    @RequireProjectRole(role = ProjectRole.VIEWER, projectIdParam = "projectId")
    public List<GlossaryTermResponse> listTerms(
            @Parameter(description = "프로젝트 ID") @PathVariable UUID projectId,
            @Parameter(description = "카테고리 필터") @RequestParam(required = false) String category
    ) {
        List<GlossaryTerm> terms = category != null
            ? glossaryService.findByProjectIdAndCategory(projectId, category)
            : glossaryService.findByProjectId(projectId);

        return terms.stream().map(this::toResponse).toList();
    }

    /**
     * 용어 상세 정보를 조회한다.
     *
     * @param projectId 프로젝트 ID
     * @param termId 용어 ID
     * @return 용어 상세 정보 (없으면 404)
     */
    @Operation(summary = "용어 상세 조회", description = "용어 ID로 상세 정보를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "용어를 찾을 수 없음")
    })
    @GetMapping("/{termId}")
    @RequireProjectRole(role = ProjectRole.VIEWER, projectIdParam = "projectId")
    public ResponseEntity<GlossaryTermDetailResponse> getTerm(
            @Parameter(description = "프로젝트 ID") @PathVariable UUID projectId,
            @Parameter(description = "용어 ID") @PathVariable UUID termId
    ) {
        return glossaryService.findById(termId)
            .filter(term -> term.getProject().getId().equals(projectId))
            .map(this::toDetailResponse)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 새 용어를 생성한다.
     *
     * @param projectId 프로젝트 ID
     * @param request 용어 생성 요청
     * @return 생성된 용어
     */
    @Operation(summary = "용어 생성", description = "새 용어를 생성합니다. EDITOR 이상 권한이 필요합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "생성 성공"),
            @ApiResponse(responseCode = "400", description = "중복된 용어명"),
            @ApiResponse(responseCode = "403", description = "권한 없음")
    })
    @PostMapping
    @RequireProjectRole(role = ProjectRole.EDITOR, projectIdParam = "projectId")
    public ResponseEntity<GlossaryTermDetailResponse> createTerm(
            @Parameter(description = "프로젝트 ID") @PathVariable UUID projectId,
            @RequestBody CreateGlossaryTermRequest request
    ) {
        UserPrincipal principal = SecurityUtils.getCurrentUserPrincipal();
        UUID createdById = principal != null ? principal.id() : null;

        try {
            GlossaryTerm term = glossaryService.create(
                projectId,
                request.name(),
                request.definition(),
                request.synonyms(),
                request.category(),
                request.abbreviation(),
                request.relatedTerms(),
                createdById
            );
            return ResponseEntity.ok(toDetailResponse(term));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 용어를 수정한다.
     *
     * @param projectId 프로젝트 ID
     * @param termId 용어 ID
     * @param request 용어 수정 요청
     * @return 수정된 용어 (없으면 404)
     */
    @Operation(summary = "용어 수정", description = "용어를 수정합니다. EDITOR 이상 권한이 필요합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "400", description = "중복된 용어명"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "404", description = "용어를 찾을 수 없음")
    })
    @PutMapping("/{termId}")
    @RequireProjectRole(role = ProjectRole.EDITOR, projectIdParam = "projectId")
    public ResponseEntity<GlossaryTermDetailResponse> updateTerm(
            @Parameter(description = "프로젝트 ID") @PathVariable UUID projectId,
            @Parameter(description = "용어 ID") @PathVariable UUID termId,
            @RequestBody UpdateGlossaryTermRequest request
    ) {
        // 용어가 해당 프로젝트에 속하는지 확인
        if (glossaryService.findById(termId)
                .filter(t -> t.getProject().getId().equals(projectId))
                .isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        try {
            GlossaryTerm term = glossaryService.update(
                termId,
                request.name(),
                request.definition(),
                request.synonyms(),
                request.category(),
                request.abbreviation(),
                request.relatedTerms()
            );
            return ResponseEntity.ok(toDetailResponse(term));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 용어를 삭제한다.
     *
     * @param projectId 프로젝트 ID
     * @param termId 용어 ID
     * @return 삭제 결과 (없으면 404)
     */
    @Operation(summary = "용어 삭제", description = "용어를 삭제합니다. EDITOR 이상 권한이 필요합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "삭제 성공"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "404", description = "용어를 찾을 수 없음")
    })
    @DeleteMapping("/{termId}")
    @RequireProjectRole(role = ProjectRole.EDITOR, projectIdParam = "projectId")
    public ResponseEntity<Void> deleteTerm(
            @Parameter(description = "프로젝트 ID") @PathVariable UUID projectId,
            @Parameter(description = "용어 ID") @PathVariable UUID termId
    ) {
        // 용어가 해당 프로젝트에 속하는지 확인
        if (glossaryService.findById(termId)
                .filter(t -> t.getProject().getId().equals(projectId))
                .isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        glossaryService.delete(termId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 용어를 검색한다.
     *
     * @param projectId 프로젝트 ID
     * @param q 검색 쿼리
     * @param mode 검색 모드 (keyword, semantic)
     * @param topK 결과 개수 제한
     * @return 검색 결과
     */
    @Operation(summary = "용어 검색", description = "키워드 또는 시맨틱 검색으로 용어를 검색합니다.")
    @ApiResponse(responseCode = "200", description = "검색 성공")
    @GetMapping("/search")
    @RequireProjectRole(role = ProjectRole.VIEWER, projectIdParam = "projectId")
    public List<GlossarySearchResultResponse> searchTerms(
            @Parameter(description = "프로젝트 ID") @PathVariable UUID projectId,
            @Parameter(description = "검색 쿼리") @RequestParam String q,
            @Parameter(description = "검색 모드 (keyword, semantic)") @RequestParam(defaultValue = "keyword") String mode,
            @Parameter(description = "결과 개수 제한") @RequestParam(defaultValue = "10") int topK
    ) {
        if ("semantic".equals(mode)) {
            return glossaryService.searchSemantic(projectId, q, topK).stream()
                .map(result -> new GlossarySearchResultResponse(
                    toResponse(result.term()),
                    result.score()
                ))
                .toList();
        } else {
            return glossaryService.searchByKeyword(projectId, q).stream()
                .limit(topK)
                .map(term -> new GlossarySearchResultResponse(toResponse(term), 1.0))
                .toList();
        }
    }

    /**
     * 프로젝트의 카테고리 목록을 조회한다.
     *
     * @param projectId 프로젝트 ID
     * @return 카테고리 목록
     */
    @Operation(summary = "카테고리 목록 조회", description = "프로젝트에서 사용 중인 모든 카테고리를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/categories")
    @RequireProjectRole(role = ProjectRole.VIEWER, projectIdParam = "projectId")
    public List<String> listCategories(
            @Parameter(description = "프로젝트 ID") @PathVariable UUID projectId
    ) {
        return glossaryService.findCategories(projectId);
    }

    // ===== DTOs =====

    /**
     * 용어 생성 요청.
     */
    public record CreateGlossaryTermRequest(
            String name,
            String definition,
            List<String> synonyms,
            String category,
            String abbreviation,
            List<UUID> relatedTerms
    ) {}

    /**
     * 용어 수정 요청.
     */
    public record UpdateGlossaryTermRequest(
            String name,
            String definition,
            List<String> synonyms,
            String category,
            String abbreviation,
            List<UUID> relatedTerms
    ) {}

    /**
     * 용어 응답 (요약).
     */
    public record GlossaryTermResponse(
            UUID id,
            UUID projectId,
            String name,
            String definition,
            List<String> synonyms,
            String category,
            String abbreviation,
            Instant createdAt,
            Instant updatedAt
    ) {}

    /**
     * 용어 응답 (상세).
     */
    public record GlossaryTermDetailResponse(
            UUID id,
            UUID projectId,
            String name,
            String definition,
            List<String> synonyms,
            String category,
            String abbreviation,
            List<UUID> relatedTerms,
            UUID createdBy,
            Instant createdAt,
            Instant updatedAt
    ) {}

    /**
     * 용어 검색 결과 응답.
     */
    public record GlossarySearchResultResponse(
            GlossaryTermResponse term,
            double score
    ) {}

    // ===== Mappers =====

    private GlossaryTermResponse toResponse(GlossaryTerm term) {
        return new GlossaryTermResponse(
            term.getId(),
            term.getProject().getId(),
            term.getName(),
            term.getDefinition(),
            term.getSynonyms(),
            term.getCategory(),
            term.getAbbreviation(),
            term.getCreatedAt(),
            term.getUpdatedAt()
        );
    }

    private GlossaryTermDetailResponse toDetailResponse(GlossaryTerm term) {
        return new GlossaryTermDetailResponse(
            term.getId(),
            term.getProject().getId(),
            term.getName(),
            term.getDefinition(),
            term.getSynonyms(),
            term.getCategory(),
            term.getAbbreviation(),
            term.getRelatedTerms(),
            term.getCreatedBy() != null ? term.getCreatedBy().getId() : null,
            term.getCreatedAt(),
            term.getUpdatedAt()
        );
    }
}
