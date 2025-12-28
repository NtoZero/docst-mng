package com.docst.api;

import com.docst.api.ApiModels.StatsResponse;
import com.docst.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 통계 컨트롤러.
 * 대시보드 통계 정보를 제공한다.
 */
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
    @GetMapping
    public StatsResponse getStats() {
        return new StatsResponse(
                statsService.countProjects(),
                statsService.countRepositories(),
                statsService.countDocuments()
        );
    }
}
