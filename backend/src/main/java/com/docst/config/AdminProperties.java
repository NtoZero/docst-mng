package com.docst.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 관리자 계정 자동 초기화 설정.
 */
@Component
@ConfigurationProperties(prefix = "docst.admin")
@Getter
@Setter
public class AdminProperties {

    /**
     * 관리자 자동 초기화 활성화 여부.
     * local/dev 환경에서는 true, prod 환경에서는 false 권장.
     */
    private boolean enabled = false;

    /**
     * 관리자 이메일 주소.
     */
    private String email = "admin@docst.local";

    /**
     * 관리자 초기 비밀번호.
     * 보안상 환경 변수로 제공하고, 최초 로그인 후 변경 필요.
     */
    private String password = "";

    /**
     * 관리자 표시 이름.
     */
    private String displayName = "System Admin";
}
