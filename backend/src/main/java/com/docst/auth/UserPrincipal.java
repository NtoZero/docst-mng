package com.docst.auth;

import com.docst.domain.User;

import java.security.Principal;
import java.util.UUID;

/**
 * User Principal DTO for Spring Security Authentication.
 * This class is used instead of User entity to avoid LazyInitializationException
 * when Spring MVC calls AbstractAuthenticationToken.getName() after the Hibernate session is closed.
 *
 * @param id User ID
 * @param email User email
 * @param displayName User display name
 * @param defaultProjectId Default project ID for MCP tool calls (from API Key settings)
 */
public record UserPrincipal(
        UUID id,
        String email,
        String displayName,
        UUID defaultProjectId
) implements Principal {

    /**
     * Create UserPrincipal from User entity (without default project).
     * This extracts the necessary fields immediately to avoid lazy loading issues.
     *
     * @param user User entity
     * @return UserPrincipal DTO
     */
    public static UserPrincipal from(User user) {
        return new UserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                null
        );
    }

    /**
     * Create UserPrincipal from User entity with default project ID.
     * Used by ApiKeyAuthenticationFilter to include the API Key's default project.
     *
     * @param user User entity
     * @param defaultProjectId Default project ID from API Key
     * @return UserPrincipal DTO
     */
    public static UserPrincipal from(User user, UUID defaultProjectId) {
        return new UserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                defaultProjectId
        );
    }

    /**
     * Returns the name of this principal for Spring Security.
     * This is called by AbstractAuthenticationToken.getName().
     *
     * @return the email address as the principal name
     */
    @Override
    public String getName() {
        return email;
    }

    @Override
    public String toString() {
        return "UserPrincipal{id=" + id + ", email='" + email + "', defaultProjectId=" + defaultProjectId + "}";
    }
}
