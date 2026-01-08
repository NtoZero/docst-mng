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
     * UserPrincipal인 경우 null을 반환한다 (User 엔티티가 필요한 경우만 사용).
     *
     * @return 현재 사용자 (인증되지 않았거나 UserPrincipal인 경우 null)
     * @deprecated {@link #getCurrentUserId()}를 사용하세요
     */
    @Deprecated
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
     * 현재 인증된 UserPrincipal을 반환한다.
     *
     * @return UserPrincipal (인증되지 않았으면 null)
     */
    public static UserPrincipal getCurrentUserPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal) {
            return (UserPrincipal) principal;
        }

        // User 엔티티인 경우 UserPrincipal로 변환
        if (principal instanceof User user) {
            return UserPrincipal.from(user);
        }

        return null;
    }

    /**
     * 현재 인증된 사용자 ID를 반환한다.
     *
     * @return 현재 사용자 ID (인증되지 않았으면 null)
     */
    public static UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof UserPrincipal userPrincipal) {
            return userPrincipal.id();
        }

        if (principal instanceof User user) {
            return user.getId();
        }

        return null;
    }

    /**
     * 현재 사용자가 인증되었는지 확인한다.
     *
     * @return 인증되었으면 true
     */
    public static boolean isAuthenticated() {
        return getCurrentUserId() != null;
    }

    /**
     * 현재 인증된 사용자 ID를 반환한다. 인증되지 않았으면 예외를 발생시킨다.
     *
     * @return 현재 사용자 ID
     * @throws SecurityException 인증되지 않았을 경우
     */
    public static UUID requireCurrentUserId() {
        UUID userId = getCurrentUserId();
        if (userId == null) {
            throw new SecurityException("Authentication required");
        }
        return userId;
    }

    /**
     * 현재 인증된 UserPrincipal을 반환한다. 인증되지 않았으면 예외를 발생시킨다.
     *
     * @return UserPrincipal
     * @throws SecurityException 인증되지 않았을 경우
     */
    public static UserPrincipal requireCurrentUserPrincipal() {
        UserPrincipal principal = getCurrentUserPrincipal();
        if (principal == null) {
            throw new SecurityException("Authentication required");
        }
        return principal;
    }

    /**
     * 현재 인증된 사용자를 반환한다. 인증되지 않았으면 예외를 발생시킨다.
     *
     * @return 현재 사용자
     * @throws SecurityException 인증되지 않았을 경우
     * @deprecated {@link #requireCurrentUserId()} 또는 {@link #requireCurrentUserPrincipal()}을 사용하세요
     */
    @Deprecated
    public static User requireCurrentUser() {
        User user = getCurrentUser();
        if (user == null) {
            throw new SecurityException("Authentication required");
        }
        return user;
    }
}
