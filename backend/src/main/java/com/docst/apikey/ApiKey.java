package com.docst.apikey;

import com.docst.project.Project;
import com.docst.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * API Key for MCP client authentication.
 * API Keys provide a persistent authentication method for MCP clients
 * (Claude Desktop, Claude Code) without expiration unless manually revoked.
 */
@Entity
@Table(name = "dm_api_key", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "name"}),
        @UniqueConstraint(columnNames = {"key_hash"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Owner user
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * API key name for identification (e.g., "Claude Desktop", "CI/CD Pipeline")
     */
    @Column(nullable = false)
    private String name;

    /**
     * Key prefix for display: "docst_ak_xxxxx..."
     * Shows first 8 characters of random part for identification
     */
    @Column(name = "key_prefix", nullable = false, length = 20)
    private String keyPrefix;

    /**
     * SHA-256 hash of the full API key
     * Stored as 64 hex characters
     */
    @Column(name = "key_hash", nullable = false, length = 64)
    private String keyHash;

    /**
     * Last used timestamp (updated on each successful authentication)
     */
    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    /**
     * Expiration timestamp (nullable for non-expiring keys)
     */
    @Column(name = "expires_at")
    private Instant expiresAt;

    /**
     * Active status (false = revoked)
     */
    @Column(nullable = false)
    private boolean active = true;

    /**
     * Creation timestamp
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Default project for MCP tool calls.
     * When projectId is not provided in MCP requests, this project will be used.
     */
    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_project_id")
    private Project defaultProject;

    /**
     * Constructor for creating a new API key
     *
     * @param user      Owner user
     * @param name      Identifier name
     * @param keyPrefix Display prefix
     * @param keyHash   SHA-256 hash of full key
     * @param expiresAt Expiration time (null for never)
     */
    public ApiKey(User user, String name, String keyPrefix, String keyHash, Instant expiresAt) {
        this.user = user;
        this.name = name;
        this.keyPrefix = keyPrefix;
        this.keyHash = keyHash;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    /**
     * Check if the API key is expired
     *
     * @return true if expired, false otherwise
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * Update last used timestamp
     */
    public void updateLastUsed() {
        this.lastUsedAt = Instant.now();
    }

    /**
     * Revoke the API key (soft delete)
     */
    public void revoke() {
        this.active = false;
    }
}
