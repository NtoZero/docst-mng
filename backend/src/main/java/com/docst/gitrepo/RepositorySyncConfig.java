package com.docst.gitrepo;

import java.io.Serializable;
import java.util.List;

/**
 * 레포지토리 동기화 설정.
 * JSONB로 저장되어 유연한 확장이 가능하다.
 *
 * @param fileExtensions 동기화할 파일 확장자 목록 (예: ["md", "adoc", "yml"])
 * @param includePaths   포함 경로 목록 - 비어있으면 전체 경로 스캔 (예: ["docs/", "src/"])
 * @param excludePaths   제외 경로 목록 (예: [".git", "node_modules"])
 * @param scanOpenApi    OpenAPI 스펙 파일 스캔 여부 (*.openapi.yaml/yml/json)
 * @param scanSwagger    Swagger 스펙 파일 스캔 여부 (*.swagger.yaml/yml/json)
 * @param customPatterns 커스텀 정규식 패턴 목록
 */
public record RepositorySyncConfig(
    List<String> fileExtensions,
    List<String> includePaths,
    List<String> excludePaths,
    boolean scanOpenApi,
    boolean scanSwagger,
    List<String> customPatterns
) implements Serializable {

    /**
     * 기본 동기화 설정을 반환한다.
     */
    public static RepositorySyncConfig defaultConfig() {
        return new RepositorySyncConfig(
            List.of("md", "adoc"),
            List.of(),  // 빈 목록 = 전체 경로 스캔
            List.of(".git", "node_modules", "target", "build", ".gradle", "dist", "out"),
            true,   // OpenAPI 스캔
            true,   // Swagger 스캔
            List.of()
        );
    }

    /**
     * null-safe 파일 확장자 목록 반환.
     */
    public List<String> getFileExtensions() {
        return fileExtensions != null ? fileExtensions : List.of();
    }

    /**
     * null-safe 포함 경로 목록 반환.
     */
    public List<String> getIncludePaths() {
        return includePaths != null ? includePaths : List.of();
    }

    /**
     * null-safe 제외 경로 목록 반환.
     */
    public List<String> getExcludePaths() {
        return excludePaths != null ? excludePaths : List.of();
    }

    /**
     * null-safe 커스텀 패턴 목록 반환.
     */
    public List<String> getCustomPatterns() {
        return customPatterns != null ? customPatterns : List.of();
    }
}
