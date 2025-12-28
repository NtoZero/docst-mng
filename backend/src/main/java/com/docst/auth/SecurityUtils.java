package com.docst.auth;

import com.docst.domain.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * 보안 유틸리티.
 * 현재 인증된 사용자 정보를 조회한다.
 */
public class SecurityUtils {

    /**
     * 현재 인증된 사용자를 반환한다.
     *
     * @return 현재 사용자 (인증되지 않았으면 null)
     */
    public static User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof User) {
            return (User) principal;
        }

        return null;
    }

    /**
     * 현재 인증된 사용자 ID를 반환한다.
     *
     * @return 현재 사용자 ID (인증되지 않았으면 null)
     */
    public static UUID getCurrentUserId() {
        User user = getCurrentUser();
        return user != null ? user.getId() : null;
    }

    /**
     * 현재 사용자가 인증되었는지 확인한다.
     *
     * @return 인증되었으면 true
     */
    public static boolean isAuthenticated() {
        return getCurrentUser() != null;
    }

    /**
     * 현재 인증된 사용자를 반환한다. 인증되지 않았으면 예외를 발생시킨다.
     *
     * @return 현재 사용자
     * @throws SecurityException 인증되지 않았을 경우
     */
    public static User requireCurrentUser() {
        User user = getCurrentUser();
        if (user == null) {
            throw new SecurityException("Authentication required");
        }
        return user;
    }
}
