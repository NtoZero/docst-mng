package com.docst.service;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256 암호화 키 생성 유틸리티 테스트.
 * 실행하여 docst.encryption.key에 사용할 키를 생성한다.
 */
class EncryptionKeyGeneratorTest {

    @Test
    void generateEncryptionKey() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] key = new byte[32]; // 256 bits
        secureRandom.nextBytes(key);

        String base64Key = Base64.getEncoder().encodeToString(key);

        System.out.println("=".repeat(60));
        System.out.println("Generated AES-256 Encryption Key (Base64):");
        System.out.println(base64Key);
        System.out.println("=".repeat(60));
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  application.yml:");
        System.out.println("    docst.encryption.key: " + base64Key);
        System.out.println();
        System.out.println("  Environment variable:");
        System.out.println("    DOCST_ENCRYPTION_KEY=" + base64Key);
        System.out.println("=".repeat(60));
    }
}