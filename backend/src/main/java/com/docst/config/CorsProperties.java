package com.docst.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * CORS 설정 프로퍼티.
 * application.yml에서 docst.cors.* 설정을 바인딩한다.
 */
@Component
@ConfigurationProperties(prefix = "docst.cors")
@Getter
@Setter
public class CorsProperties {

    /**
     * CORS 활성화 여부.
     */
    private boolean enabled = true;

    /**
     * 허용할 Origin 목록.
     * 쉼표로 구분된 문자열을 List로 변환.
     */
    private List<String> allowedOrigins = new ArrayList<>(List.of(
            "http://localhost:3000",
            "http://localhost:3001",
            "http://localhost:3002"
    ));

    /**
     * 허용할 HTTP 메서드.
     */
    private List<String> allowedMethods = new ArrayList<>(List.of(
            "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
    ));

    /**
     * 허용할 헤더.
     */
    private List<String> allowedHeaders = new ArrayList<>(List.of(
            "Authorization", "Content-Type", "Accept", "X-Requested-With"
    ));

    /**
     * 자격증명 허용 여부 (쿠키, Authorization 헤더 등).
     */
    private boolean allowCredentials = true;

    /**
     * Preflight 요청 캐시 시간 (초).
     */
    private Long maxAge = 3600L;

    /**
     * 노출할 헤더 (클라이언트에서 접근 가능한 헤더).
     */
    private List<String> exposedHeaders = new ArrayList<>(List.of("Authorization"));
}
