package com.docst.api;

import com.docst.domain.DocumentLink;
import com.docst.service.DocumentLinkService;
import com.docst.service.GraphService;
import com.docst.service.GraphService.GraphData;
import com.docst.service.GraphService.ImpactAnalysis;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 그래프 컨트롤러.
 * 문서 관계 그래프 및 링크 분석 API를 제공한다.
 */
@Tag(name = "Graph", description = "문서 관계 그래프 및 링크 분석 API")
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class GraphController {

    private final GraphService graphService;
    private final DocumentLinkService documentLinkService;

    /**
     * 프로젝트 전체의 문서 관계 그래프를 조회한다.
     *
     * @param projectId 프로젝트 ID
     * @return 그래프 데이터 (노드 및 엣지)
     */
    @Operation(summary = "프로젝트 문서 그래프 조회", description = "프로젝트 전체의 문서 관계 그래프를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/projects/{projectId}/graph")
    public ResponseEntity<GraphData> getProjectGraph(
            @Parameter(description = "프로젝트 ID") @PathVariable UUID projectId) {
        GraphData graph = graphService.getProjectGraph(projectId);
        return ResponseEntity.ok(graph);
    }

    /**
     * 레포지토리의 문서 관계 그래프를 조회한다.
     *
     * @param repositoryId 레포지토리 ID
     * @return 그래프 데이터 (노드 및 엣지)
     */
    @Operation(summary = "레포지토리 문서 그래프 조회", description = "레포지토리의 문서 관계 그래프를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/repositories/{repositoryId}/graph")
    public ResponseEntity<GraphData> getRepositoryGraph(
            @Parameter(description = "레포지토리 ID") @PathVariable UUID repositoryId) {
        GraphData graph = graphService.getRepositoryGraph(repositoryId);
        return ResponseEntity.ok(graph);
    }

    /**
     * 특정 문서를 중심으로 연결된 문서 그래프를 조회한다.
     *
     * @param documentId 문서 ID
     * @param depth      탐색 깊이 (기본값: 1)
     * @return 그래프 데이터
     */
    @Operation(summary = "문서 중심 그래프 조회", description = "특정 문서를 중심으로 연결된 문서 그래프를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/documents/{documentId}/graph")
    public ResponseEntity<GraphData> getDocumentGraph(
            @Parameter(description = "문서 ID") @PathVariable UUID documentId,
            @Parameter(description = "탐색 깊이 (기본값: 1)") @RequestParam(defaultValue = "1") int depth) {
        GraphData graph = graphService.getDocumentGraph(documentId, depth);
        return ResponseEntity.ok(graph);
    }

    /**
     * 특정 문서에서 나가는 링크 목록을 조회한다.
     *
     * @param documentId 문서 ID
     * @return 나가는 링크 목록
     */
    @Operation(summary = "나가는 링크 목록 조회", description = "특정 문서에서 나가는 링크 목록을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/documents/{documentId}/links/outgoing")
    public ResponseEntity<List<DocumentLinkResponse>> getOutgoingLinks(
            @Parameter(description = "문서 ID") @PathVariable UUID documentId) {
        List<DocumentLink> links = documentLinkService.getOutgoingLinks(documentId);
        List<DocumentLinkResponse> response = links.stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    /**
     * 특정 문서로 들어오는 링크 목록을 조회한다 (역참조).
     *
     * @param documentId 문서 ID
     * @return 들어오는 링크 목록
     */
    @Operation(summary = "들어오는 링크 목록 조회", description = "특정 문서로 들어오는 링크 목록을 조회합니다. (역참조)")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/documents/{documentId}/links/incoming")
    public ResponseEntity<List<DocumentLinkResponse>> getIncomingLinks(
            @Parameter(description = "문서 ID") @PathVariable UUID documentId) {
        List<DocumentLink> links = documentLinkService.getIncomingLinks(documentId);
        List<DocumentLinkResponse> response = links.stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    /**
     * 특정 문서의 영향 분석을 수행한다.
     * 이 문서가 변경되면 영향을 받을 수 있는 문서 목록을 반환한다.
     *
     * @param documentId 문서 ID
     * @return 영향 분석 결과
     */
    @Operation(summary = "문서 영향 분석", description = "특정 문서가 변경될 때 영향을 받을 수 있는 문서 목록을 분석합니다.")
    @ApiResponse(responseCode = "200", description = "분석 성공")
    @GetMapping("/documents/{documentId}/impact")
    public ResponseEntity<ImpactAnalysis> analyzeImpact(
            @Parameter(description = "문서 ID") @PathVariable UUID documentId) {
        ImpactAnalysis analysis = graphService.analyzeImpact(documentId);
        return ResponseEntity.ok(analysis);
    }

    /**
     * 레포지토리 내 깨진 링크 목록을 조회한다.
     *
     * @param repositoryId 레포지토리 ID
     * @return 깨진 링크 목록
     */
    @Operation(summary = "깨진 링크 목록 조회", description = "레포지토리 내 깨진 링크 목록을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/repositories/{repositoryId}/links/broken")
    public ResponseEntity<List<DocumentLinkResponse>> getBrokenLinks(
            @Parameter(description = "레포지토리 ID") @PathVariable UUID repositoryId) {
        List<DocumentLink> links = documentLinkService.getBrokenLinks(repositoryId);
        List<DocumentLinkResponse> response = links.stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    /**
     * DocumentLink를 DocumentLinkResponse로 변환한다.
     */
    private DocumentLinkResponse toResponse(DocumentLink link) {
        return new DocumentLinkResponse(
                link.getId(),
                link.getSourceDocument().getId(),
                link.getSourceDocument().getPath(),
                link.getTargetDocument() != null ? link.getTargetDocument().getId() : null,
                link.getTargetDocument() != null ? link.getTargetDocument().getPath() : null,
                link.getLinkText(),
                link.getLinkType().name(),
                link.isBroken(),
                link.getLineNumber(),
                link.getAnchorText()
        );
    }

    /**
     * 문서 링크 응답.
     */
    public record DocumentLinkResponse(
            UUID id,
            UUID sourceDocumentId,
            String sourceDocumentPath,
            UUID targetDocumentId,
            String targetDocumentPath,
            String linkText,
            String linkType,
            boolean broken,
            Integer lineNumber,
            String anchorText
    ) {
    }
}
