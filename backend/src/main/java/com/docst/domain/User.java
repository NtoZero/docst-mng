package com.docst.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dm_user", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"provider", "provider_user_id"})
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthProvider provider;

    @Column(name = "provider_user_id", nullable = false)
    private String providerUserId;

    private String email;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected User() {}

    public User(AuthProvider provider, String providerUserId, String email, String displayName) {
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.email = email;
        this.displayName = displayName;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public AuthProvider getProvider() { return provider; }
    public String getProviderUserId() { return providerUserId; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    public Instant getCreatedAt() { return createdAt; }

    public void setEmail(String email) { this.email = email; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public enum AuthProvider {
        GITHUB, LOCAL
    }
}
