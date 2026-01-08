package com.docst.repository;

import com.docst.domain.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for API Key management
 */
@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    /**
     * Find all API keys for a user, ordered by creation date descending
     *
     * @param userId User ID
     * @return List of API keys
     */
    List<ApiKey> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Find all active API keys for a user
     *
     * @param userId User ID
     * @return List of active API keys
     */
    List<ApiKey> findByUserIdAndActiveTrue(UUID userId);

    /**
     * Find an API key by ID and user ID
     *
     * @param id     API key ID
     * @param userId User ID
     * @return Optional API key
     */
    Optional<ApiKey> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Find an active API key by key hash
     * Used for authentication
     *
     * @param keyHash SHA-256 hash of the API key
     * @return Optional API key
     */
    Optional<ApiKey> findByKeyHashAndActiveTrue(String keyHash);

    /**
     * Find an active API key by key hash with User eagerly fetched.
     * Used for authentication to avoid LazyInitializationException
     * when accessing User fields after transaction ends.
     *
     * @param keyHash SHA-256 hash of the API key
     * @return Optional API key with User loaded
     */
    @Query("SELECT ak FROM ApiKey ak JOIN FETCH ak.user WHERE ak.keyHash = :keyHash AND ak.active = true")
    Optional<ApiKey> findByKeyHashAndActiveTrueWithUser(@Param("keyHash") String keyHash);

    /**
     * Find an API key by user ID and name
     *
     * @param userId User ID
     * @param name   API key name
     * @return Optional API key
     */
    Optional<ApiKey> findByUserIdAndName(UUID userId, String name);

    /**
     * Check if a key hash already exists
     *
     * @param keyHash SHA-256 hash of the API key
     * @return true if exists, false otherwise
     */
    boolean existsByKeyHash(String keyHash);
}
