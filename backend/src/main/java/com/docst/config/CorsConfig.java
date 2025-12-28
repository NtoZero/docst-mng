package com.docst.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * CORS (Cross-Origin Resource Sharing) 설정.
 * 프론트엔드에서 백엔드 API 호출을 허용하기 위한 설정.
 *
 * application.yml의 docst.cors.* 설정을 사용한다.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class CorsConfig {

    private final CorsProperties corsProperties;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        if (!corsProperties.isEnabled()) {
            log.info("CORS is disabled");
            return request -> new CorsConfiguration().applyPermitDefaultValues();
        }

        CorsConfiguration configuration = new CorsConfiguration();

        // 허용할 Origin 설정
        configuration.setAllowedOrigins(corsProperties.getAllowedOrigins());
        log.info("CORS allowed origins: {}", corsProperties.getAllowedOrigins());

        // 허용할 HTTP 메서드
        configuration.setAllowedMethods(corsProperties.getAllowedMethods());

        // 허용할 헤더
        configuration.setAllowedHeaders(corsProperties.getAllowedHeaders());

        // 자격증명 허용 (쿠키, Authorization 헤더 등)
        configuration.setAllowCredentials(corsProperties.isAllowCredentials());

        // Preflight 요청 캐시 시간 (초)
        configuration.setMaxAge(corsProperties.getMaxAge());

        // 노출할 헤더 (클라이언트에서 접근 가능한 헤더)
        configuration.setExposedHeaders(corsProperties.getExposedHeaders());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
