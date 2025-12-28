package com.docst.auth;

import com.docst.config.AdminProperties;
import com.docst.domain.User;
import com.docst.domain.User.AuthProvider;
import com.docst.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 관리자 계정 자동 초기화 컴포넌트.
 * 애플리케이션 시작 시 관리자 계정을 자동으로 생성한다.
 *
 * local/dev 환경에서는 개발 편의를 위해 활성화하고,
 * prod 환경에서는 보안상 비활성화하고 SetupController를 사용한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminInitializer {

    private final AdminProperties adminProperties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordValidator passwordValidator;

    /**
     * 애플리케이션 시작 시 관리자 계정을 초기화한다.
     * docst.admin.enabled=true일 때만 동작한다.
     */
    @PostConstruct
    public void initializeAdmin() {
        if (!adminProperties.isEnabled()) {
            log.info("Admin auto-initialization is disabled (docst.admin.enabled=false)");
            return;
        }

        String email = adminProperties.getEmail();
        String password = adminProperties.getPassword();
        String displayName = adminProperties.getDisplayName();

        // 비밀번호 검증
        if (password == null || password.isEmpty()) {
            log.error("Admin auto-initialization failed: password is not set. " +
                    "Please set DOCST_ADMIN_PASSWORD environment variable.");
            return;
        }

        try {
            passwordValidator.validate(password);
        } catch (IllegalArgumentException e) {
            log.error("Admin auto-initialization failed: invalid password - {}", e.getMessage());
            return;
        }

        // 관리자 계정이 이미 존재하는지 확인
        boolean adminExists = userRepository.findByProviderAndProviderUserId(AuthProvider.LOCAL, email)
                .isPresent();

        if (adminExists) {
            log.info("Admin user already exists: {}", email);
            return;
        }

        // 관리자 계정 생성
        try {
            User admin = new User(AuthProvider.LOCAL, email, email, displayName);
            String hashedPassword = passwordEncoder.encode(password);
            admin.setPasswordHash(hashedPassword);

            userRepository.save(admin);

            log.info("Admin user created successfully: {}", email);
            log.warn("SECURITY WARNING: Please change the admin password immediately after first login!");
        } catch (Exception e) {
            log.error("Failed to create admin user", e);
        }
    }
}
