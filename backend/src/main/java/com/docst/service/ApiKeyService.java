package com.docst.service;

import com.docst.domain.ApiKey;
import com.docst.domain.User;
import com.docst.repository.ApiKeyRepository;
import com.docst.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for API Key management and authentication
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ApiKeyService {

    private static final String KEY_PREFIX = "docst_ak_";
    private static final int RANDOM_PART_LENGTH = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;

    /**
     * Generate a new API key for the user.
     * Returns the full key (only shown once).
     *
     * @param userId      User ID
     * @param name        API key name
     * @param expiresAt   Expiration time (null for never)
     * @return ApiKeyCreationResult containing the saved API key and full key
     */
    @Transactional
    public ApiKeyCreationResult createApiKey(UUID userId, String name, Instant expiresAt) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // Check duplicate name
        if (apiKeyRepository.findByUserIdAndName(userId, name).isPresent()) {
            throw new IllegalArgumentException("API key with name '" + name + "' already exists");
        }

        // Generate random key
        String randomPart = generateRandomString(RANDOM_PART_LENGTH);
        String fullKey = KEY_PREFIX + randomPart;

        // Create prefix for display (show first 8 chars of random part)
        String keyPrefix = KEY_PREFIX + randomPart.substring(0, 8) + "...";

        // Hash the full key with SHA-256
        String keyHash = hashKey(fullKey);

        // Verify hash is unique (extremely unlikely collision, but check anyway)
        if (apiKeyRepository.existsByKeyHash(keyHash)) {
            log.warn("API key hash collision detected, regenerating...");
            return createApiKey(userId, name, expiresAt); // Retry
        }

        ApiKey apiKey = new ApiKey(user, name, keyPrefix, keyHash, expiresAt);
        ApiKey saved = apiKeyRepository.save(apiKey);

        log.info("AUDIT: Created API key '{}' for user: {} (expires: {})",
                name, userId, expiresAt != null ? expiresAt : "never");

        return new ApiKeyCreationResult(saved, fullKey);
    }

    /**
     * Authenticate using API key.
     * Returns the user if valid, empty otherwise.
     *
     * @param apiKey Full API key string
     * @return Optional User if authentication successful
     */
    @Transactional
    public Optional<User> authenticateByApiKey(String apiKey) {
        if (apiKey == null || !apiKey.startsWith(KEY_PREFIX)) {
            return Optional.empty();
        }

        String keyHash = hashKey(apiKey);
        Optional<ApiKey> found = apiKeyRepository.findByKeyHashAndActiveTrue(keyHash);

        if (found.isEmpty()) {
            return Optional.empty();
        }

        ApiKey key = found.get();

        // Check expiration
        if (key.isExpired()) {
            log.debug("API key expired: {}", key.getName());
            return Optional.empty();
        }

        // Update last used timestamp
        key.updateLastUsed();
        apiKeyRepository.save(key);

        log.debug("API key authentication successful: user={}, key={}", key.getUser().getId(), key.getName());

        return Optional.of(key.getUser());
    }

    /**
     * List all API keys for a user (without secrets).
     *
     * @param userId User ID
     * @return List of API keys
     */
    public List<ApiKey> findByUserId(UUID userId) {
        return apiKeyRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * Revoke (soft delete) an API key.
     *
     * @param id     API key ID
     * @param userId User ID (for authorization)
     */
    @Transactional
    public void revokeApiKey(UUID id, UUID userId) {
        ApiKey apiKey = apiKeyRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("API key not found or access denied"));

        apiKey.revoke();
        apiKeyRepository.save(apiKey);

        log.info("AUDIT: Revoked API key '{}' for user: {}", apiKey.getName(), userId);
    }

    /**
     * Hard delete an API key.
     *
     * @param id     API key ID
     * @param userId User ID (for authorization)
     */
    @Transactional
    public void deleteApiKey(UUID id, UUID userId) {
        ApiKey apiKey = apiKeyRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("API key not found or access denied"));

        apiKeyRepository.delete(apiKey);
        log.info("AUDIT: Deleted API key '{}' for user: {}", apiKey.getName(), userId);
    }

    /**
     * Generate a URL-safe random string
     *
     * @param length Desired length
     * @return Random string
     */
    private String generateRandomString(int length) {
        byte[] bytes = new byte[length];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).substring(0, length);
    }

    /**
     * Hash an API key using SHA-256
     *
     * @param key API key to hash
     * @return Hex-encoded SHA-256 hash
     */
    private String hashKey(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Convert byte array to hex string
     *
     * @param hash Byte array
     * @return Hex string
     */
    private String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * Result record for API key creation
     *
     * @param apiKey  Saved API key entity
     * @param fullKey Full API key string (only returned once)
     */
    public record ApiKeyCreationResult(ApiKey apiKey, String fullKey) {
    }
}
