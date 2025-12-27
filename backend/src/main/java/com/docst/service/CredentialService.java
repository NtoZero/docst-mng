package com.docst.service;

import com.docst.domain.Credential;
import com.docst.domain.Credential.CredentialType;
import com.docst.domain.User;
import com.docst.repository.CredentialRepository;
import com.docst.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 자격증명 서비스.
 * 자격증명 CRUD 및 암호화/복호화를 담당한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CredentialService {

    private final CredentialRepository credentialRepository;
    private final UserRepository userRepository;
    private final EncryptionService encryptionService;

    /**
     * 사용자의 모든 자격증명을 조회한다.
     *
     * @param userId 사용자 ID
     * @return 자격증명 목록
     */
    public List<Credential> findByUserId(UUID userId) {
        return credentialRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * 자격증명을 ID로 조회한다.
     *
     * @param id 자격증명 ID
     * @param userId 소유자 ID (권한 확인용)
     * @return 자격증명 (존재하지 않거나 권한이 없으면 empty)
     */
    public Optional<Credential> findById(UUID id, UUID userId) {
        return credentialRepository.findByIdAndUserId(id, userId);
    }

    /**
     * 자격증명을 생성한다.
     *
     * @param userId 소유자 ID
     * @param name 자격증명 이름
     * @param type 자격증명 타입
     * @param username 사용자명 (선택)
     * @param secret 비밀 (평문 - 암호화되어 저장됨)
     * @param description 설명 (선택)
     * @return 생성된 자격증명
     */
    @Transactional
    public Credential create(UUID userId, String name, CredentialType type,
                             String username, String secret, String description) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // 중복 이름 확인
        if (credentialRepository.findByUserIdAndName(userId, name).isPresent()) {
            throw new IllegalArgumentException("Credential with name '" + name + "' already exists");
        }

        // 비밀 암호화
        String encryptedSecret = encryptionService.encrypt(secret);

        Credential credential = new Credential(user, name, type, encryptedSecret);
        credential.setUsername(username);
        credential.setDescription(description);

        Credential saved = credentialRepository.save(credential);
        log.info("Created credential: {} for user: {}", name, userId);
        return saved;
    }

    /**
     * 자격증명을 수정한다.
     *
     * @param id 자격증명 ID
     * @param userId 소유자 ID (권한 확인용)
     * @param name 새 이름 (null이면 변경 안 함)
     * @param username 새 사용자명 (null이면 변경 안 함)
     * @param secret 새 비밀 (null이면 변경 안 함)
     * @param description 새 설명 (null이면 변경 안 함)
     * @return 수정된 자격증명
     */
    @Transactional
    public Credential update(UUID id, UUID userId, String name, String username,
                             String secret, String description) {
        Credential credential = credentialRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Credential not found or access denied"));

        if (name != null && !name.equals(credential.getName())) {
            // 중복 이름 확인
            if (credentialRepository.findByUserIdAndName(userId, name).isPresent()) {
                throw new IllegalArgumentException("Credential with name '" + name + "' already exists");
            }
            // 이름 변경은 새 엔티티 생성으로 처리 (이름은 불변)
            throw new UnsupportedOperationException("Credential name cannot be changed");
        }

        if (username != null) {
            credential.setUsername(username);
        }
        if (secret != null) {
            credential.setEncryptedSecret(encryptionService.encrypt(secret));
        }
        if (description != null) {
            credential.setDescription(description);
        }
        credential.setUpdatedAt(Instant.now());

        log.info("Updated credential: {} for user: {}", credential.getName(), userId);
        return credentialRepository.save(credential);
    }

    /**
     * 자격증명을 삭제한다.
     *
     * @param id 자격증명 ID
     * @param userId 소유자 ID (권한 확인용)
     */
    @Transactional
    public void delete(UUID id, UUID userId) {
        Credential credential = credentialRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Credential not found or access denied"));

        credentialRepository.delete(credential);
        log.info("Deleted credential: {} for user: {}", credential.getName(), userId);
    }

    /**
     * 자격증명의 복호화된 비밀을 반환한다.
     *
     * @param credentialId 자격증명 ID
     * @return 복호화된 비밀
     */
    public String getDecryptedSecret(UUID credentialId) {
        Credential credential = credentialRepository.findById(credentialId)
                .orElseThrow(() -> new IllegalArgumentException("Credential not found: " + credentialId));

        return encryptionService.decrypt(credential.getEncryptedSecret());
    }

    /**
     * 자격증명의 복호화된 비밀을 반환한다. (권한 확인 포함)
     *
     * @param credentialId 자격증명 ID
     * @param userId 소유자 ID
     * @return 복호화된 비밀
     */
    public String getDecryptedSecret(UUID credentialId, UUID userId) {
        Credential credential = credentialRepository.findByIdAndUserId(credentialId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Credential not found or access denied"));

        return encryptionService.decrypt(credential.getEncryptedSecret());
    }
}
