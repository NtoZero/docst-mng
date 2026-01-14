package com.docst.stats.api;

import com.docst.api.ApiModels.StatsResponse;
import com.docst.stats.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 통계 컨트롤러.
 * 대시보드 통계 정보를 제공한다.
 */
@Tag(name = "Statistics", description = "대시보드 통계 API")
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    /**
     * 대시보드 통계를 조회한다.
     *
     * @return 통계 정보 (프로젝트, 레포지토리, 문서 수)
     */
    @Operation(summary = "통계 조회", description = "대시보드 통계 정보를 조회합니다. (프로젝트, 레포지토리, 문서 수)")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping
    public StatsResponse getStats() {
        return new StatsResponse(
                statsService.countProjects(),
                statsService.countRepositories(),
                statsService.countDocuments()
        );
    }
}
