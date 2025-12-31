package com.docst.api;

import com.docst.domain.SystemConfig;
import com.docst.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 시스템 설정 관리 API (ADMIN 권한).
 * dm_system_config 테이블의 설정을 관리한다.
 */
@RestController
@RequestMapping("/api/admin/config")
@RequiredArgsConstructor
@Slf4j
public class AdminConfigController {

    private final SystemConfigService systemConfigService;

    /**
     * 모든 시스템 설정 조회.
     *
     * @return 시스템 설정 목록
     */
    @GetMapping
    public ResponseEntity<List<ApiModels.SystemConfigResponse>> getAllConfigs() {
        log.debug("Fetching all system configs");

        List<ApiModels.SystemConfigResponse> configs = systemConfigService.getAllConfigs().stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(configs);
    }

    /**
     * 특정 시스템 설정 조회.
     *
     * @param key 설정 키
     * @return 시스템 설정
     */
    @GetMapping("/{key}")
    public ResponseEntity<ApiModels.SystemConfigResponse> getConfig(@PathVariable String key) {
        log.debug("Fetching system config: {}", key);

        return systemConfigService.getConfig(key)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 시스템 설정 업데이트.
     *
     * @param key 설정 키
     * @param request 업데이트 요청
     * @return 업데이트된 시스템 설정
     */
    @PutMapping("/{key}")
    public ResponseEntity<ApiModels.SystemConfigResponse> updateConfig(
            @PathVariable String key,
            @RequestBody ApiModels.UpdateSystemConfigRequest request
    ) {
        log.info("Updating system config: {}", key);

        // 설정 업데이트
        if (request.configType() != null) {
            systemConfigService.setConfig(key, request.configValue(), request.configType());
        } else {
            systemConfigService.setConfig(key, request.configValue());
        }

        // 설명 업데이트 (있을 경우)
        if (request.description() != null) {
            systemConfigService.getConfig(key).ifPresent(config -> {
                config.setDescription(request.description());
            });
        }

        // 업데이트된 설정 조회
        return systemConfigService.getConfig(key)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 캐시 갱신.
     *
     * @return 성공 메시지
     */
    @PostMapping("/refresh")
    public ResponseEntity<String> refreshCache() {
        log.info("Refreshing system config cache");
        systemConfigService.refreshCache();
        return ResponseEntity.ok("Cache refreshed");
    }

    /**
     * SystemConfig 엔티티를 응답 DTO로 변환.
     *
     * @param config 시스템 설정
     * @return 응답 DTO
     */
    private ApiModels.SystemConfigResponse toResponse(SystemConfig config) {
        return new ApiModels.SystemConfigResponse(
                config.getId(),
                config.getConfigKey(),
                config.getConfigValue(),
                config.getConfigType(),
                config.getDescription(),
                config.getCreatedAt(),
                config.getUpdatedAt()
        );
    }
}
