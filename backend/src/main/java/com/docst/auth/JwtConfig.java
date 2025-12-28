package com.docst.auth;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "docst.jwt")
@Getter
@Setter
public class JwtConfig {
    /**
     * JWT secret key (256-bit minimum)
     */
    private String secret = "your-256-bit-secret-key-change-this-in-production-environment";

    /**
     * JWT expiration time in seconds (default: 24 hours)
     */
    private long expiration = 86400;
}
