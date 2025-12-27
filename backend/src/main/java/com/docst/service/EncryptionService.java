package com.docst.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 암호화 서비스.
 * AES-256-GCM을 사용하여 민감한 데이터를 암호화/복호화한다.
 */
@Service
@Slf4j
public class EncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    @Value("${docst.encryption.key:#{null}}")
    private String encryptionKeyBase64;

    private SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    @PostConstruct
    public void init() {
        if (encryptionKeyBase64 == null || encryptionKeyBase64.isBlank()) {
            // 개발 환경용 기본 키 (프로덕션에서는 반드시 환경변수로 설정)
            log.warn("DOCST_ENCRYPTION_KEY is not set. Using default key for development. DO NOT use in production!");
            encryptionKeyBase64 = "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="; // 32 bytes
        }

        byte[] keyBytes = Base64.getDecoder().decode(encryptionKeyBase64);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException("Encryption key must be 32 bytes (256 bits). Got: " + keyBytes.length);
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
        log.info("Encryption service initialized");
    }

    /**
     * 평문을 암호화한다.
     *
     * @param plainText 암호화할 평문
     * @return Base64로 인코딩된 암호문 (IV + 암호문 + 태그)
     */
    public String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }

        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // IV + 암호문을 결합
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);

            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * 암호문을 복호화한다.
     *
     * @param encryptedText Base64로 인코딩된 암호문
     * @return 복호화된 평문
     */
    public String decrypt(String encryptedText) {
        if (encryptedText == null) {
            return null;
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedText);

            // IV와 암호문 분리
            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }
}
