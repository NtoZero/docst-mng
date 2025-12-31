package com.docst.config;

import com.docst.service.Neo4jDriverManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * 동적 Neo4j 설정.
 * Neo4jDriverManager를 통해 캐싱된 Driver를 제공한다.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class DynamicNeo4jConfig {

    private final Neo4jDriverManager neo4jDriverManager;

    /**
     * Neo4j Driver 빈 생성.
     * Neo4jDriverManager에서 캐싱된 Driver를 가져온다.
     *
     * @return Neo4j Driver (설정 없으면 null)
     */
    @Bean
    @Lazy  // 필요할 때만 생성
    public Driver neo4jDriver() {
        return neo4jDriverManager.getOrCreateDriver();
    }
}
