package com.docst.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.util.List;

/**
 * OpenAPI/Swagger 설정.
 * API 문서화 및 Swagger UI 설정을 담당한다.
 */
@Configuration
@Slf4j
public class OpenApiConfig {

    @Value("${docst.base-url}")
    private String baseUrl;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    /**
     * OpenAPI 기본 정보를 설정한다.
     */
    @Bean
    public OpenAPI customOpenAPI() {
        // Security Scheme 정의 (JWT Bearer Token)
        String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("Docst API")
                        .version("0.1.0")
                        .description("""
                                Docst - Unified Documentation Hub

                                분산된 Git 레포지토리의 문서를 통합 관리하고, AI 기반 의미 검색을 제공하는 문서 허브 플랫폼입니다.

                                주요 기능:
                                - 문서 동기화: GitHub/Local Git 레포지토리 연결 및 자동 동기화
                                - 버전 관리: Git 커밋 기반 문서 버전 추적 및 Diff 비교
                                - 검색: 키워드 검색 + 의미 검색(Semantic Search) + 하이브리드 검색
                                - MCP Tools: AI 에이전트가 문서를 조회/검색할 수 있는 MCP 인터페이스
                                - 문서 관계 그래프: 문서 간 링크 분석 및 영향도 파악

                                ## 인증 방법
                                1. `/api/auth/local/login` 또는 `/api/auth/local/register`로 JWT 토큰 획득
                                2. 우측 상단 "Authorize" 버튼 클릭
                                3. 받은 토큰을 입력 (Bearer 접두사 없이 토큰만 입력)
                                4. "Authorize" 클릭 후 API 사용
                                """)
                        .contact(new Contact()
                                .name("Docst Team")
                                .url("https://github.com/your-org/docst")))
                .servers(List.of(
                        new Server()
                                .url(baseUrl + contextPath)
                                .description("API Server")
                ))
                // Security Scheme 추가
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT 토큰을 입력하세요 (Bearer 접두사 없이)")
                        )
                )
                // 전역 Security Requirement 추가 (모든 API에 기본 적용)
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName));
    }

    /**
     * 애플리케이션 시작 시 Swagger UI URL을 로깅한다.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void logSwaggerUrl() {
        String swaggerUiUrl = baseUrl + contextPath + "/swagger-ui.html";
        String apiDocsUrl = baseUrl + contextPath + "/v3/api-docs";

        log.info("");
        log.info("========================================");
        log.info("  Swagger UI is available at:");
        log.info("  {}", swaggerUiUrl);
        log.info("");
        log.info("  OpenAPI JSON is available at:");
        log.info("  {}", apiDocsUrl);
        log.info("========================================");
        log.info("");
    }
}
