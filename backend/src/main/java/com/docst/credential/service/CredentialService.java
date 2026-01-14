package com.docst.credential.service;

import com.docst.credential.Credential;
import com.docst.credential.Credential.CredentialType;
import com.docst.credential.repository.CredentialRepository;
import com.docst.credential.CredentialScope;
import com.docst.project.Project;
import com.docst.project.repository.ProjectRepository;
import com.docst.user.User;
import com.docst.user.repository.UserRepository;
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
    private final ProjectRepository projectRepository;
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

    // ============================================================
    // 시스템 크리덴셜 (Phase 4-D2)
    // ============================================================

    /**
     * 시스템 크리덴셜 생성 (ADMIN 권한 필요).
     *
     * @param name 크리덴셜 이름
     * @param type 크리덴셜 타입
     * @param secret 비밀 (평문)
     * @param description 설명
     * @return 생성된 시스템 크리덴셜
     */
    @Transactional
    public Credential createSystemCredential(String name, CredentialType type, String secret, String description) {
        // 중복 이름 확인
        Optional<Credential> existing = credentialRepository.findByScope(CredentialScope.SYSTEM).stream()
                .filter(c -> c.getName().equals(name))
                .findFirst();
        if (existing.isPresent()) {
            throw new IllegalArgumentException("System credential with name '" + name + "' already exists");
        }

        String encryptedSecret = encryptionService.encrypt(secret);
        Credential credential = Credential.createSystemCredential(name, type, encryptedSecret);
        credential.setDescription(description);

        Credential saved = credentialRepository.save(credential);
        log.info("AUDIT: Created system credential: {}", name);
        return saved;
    }

    /**
     * 시스템 크리덴셜 목록 조회.
     *
     * @return 시스템 크리덴셜 목록
     */
    public List<Credential> findSystemCredentials() {
        return credentialRepository.findByScope(CredentialScope.SYSTEM);
    }

    /**
     * 시스템 크리덴셜 조회.
     *
     * @param id 크리덴셜 ID
     * @return 크리덴셜
     */
    public Optional<Credential> findSystemCredentialById(UUID id) {
        return credentialRepository.findById(id)
                .filter(c -> c.getScope() == CredentialScope.SYSTEM);
    }

    /**
     * 시스템 크리덴셜 업데이트.
     *
     * @param id 크리덴셜 ID
     * @param secret 새 비밀 (null이면 변경 안 함)
     * @param description 새 설명 (null이면 변경 안 함)
     * @return 업데이트된 크리덴셜
     */
    @Transactional
    public Credential updateSystemCredential(UUID id, String secret, String description) {
        Credential credential = findSystemCredentialById(id)
                .orElseThrow(() -> new IllegalArgumentException("System credential not found: " + id));

        if (secret != null) {
            credential.setEncryptedSecret(encryptionService.encrypt(secret));
        }
        if (description != null) {
            credential.setDescription(description);
        }
        credential.setUpdatedAt(Instant.now());

        log.info("AUDIT: Updated system credential: {}", credential.getName());
        return credentialRepository.save(credential);
    }

    /**
     * 시스템 크리덴셜 삭제.
     *
     * @param id 크리덴셜 ID
     */
    @Transactional
    public void deleteSystemCredential(UUID id) {
        Credential credential = findSystemCredentialById(id)
                .orElseThrow(() -> new IllegalArgumentException("System credential not found: " + id));

        credentialRepository.delete(credential);
        log.info("AUDIT: Deleted system credential: {}", credential.getName());
    }

    // ============================================================
    // 프로젝트 크리덴셜 (Phase 4-D2)
    // ============================================================

    /**
     * 프로젝트 크리덴셜 생성 (PROJECT_ADMIN 권한 필요).
     *
     * @param projectId 프로젝트 ID
     * @param name 크리덴셜 이름
     * @param type 크리덴셜 타입
     * @param secret 비밀 (평문)
     * @param description 설명
     * @return 생성된 프로젝트 크리덴셜
     */
    @Transactional
    public Credential createProjectCredential(UUID projectId, String name, CredentialType type, String secret, String description) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        // 중복 이름 확인
        Optional<Credential> existing = credentialRepository.findByProjectId(projectId).stream()
                .filter(c -> c.getName().equals(name))
                .findFirst();
        if (existing.isPresent()) {
            throw new IllegalArgumentException("Project credential with name '" + name + "' already exists in this project");
        }

        String encryptedSecret = encryptionService.encrypt(secret);
        Credential credential = Credential.createProjectCredential(project, name, type, encryptedSecret);
        credential.setDescription(description);

        Credential saved = credentialRepository.save(credential);
        log.info("AUDIT: Created project credential: {} in project {}", name, projectId);
        return saved;
    }

    /**
     * 프로젝트 크리덴셜 목록 조회.
     *
     * @param projectId 프로젝트 ID
     * @return 프로젝트 크리덴셜 목록
     */
    public List<Credential> findProjectCredentials(UUID projectId) {
        return credentialRepository.findByProjectId(projectId);
    }

    /**
     * 프로젝트 크리덴셜 조회.
     *
     * @param projectId 프로젝트 ID
     * @param credentialId 크리덴셜 ID
     * @return 크리덴셜
     */
    public Optional<Credential> findProjectCredentialById(UUID projectId, UUID credentialId) {
        return credentialRepository.findById(credentialId)
                .filter(c -> c.getScope() == CredentialScope.PROJECT)
                .filter(c -> c.getProject() != null && c.getProject().getId().equals(projectId));
    }

    /**
     * 프로젝트 크리덴셜 업데이트.
     *
     * @param projectId 프로젝트 ID
     * @param credentialId 크리덴셜 ID
     * @param secret 새 비밀 (null이면 변경 안 함)
     * @param description 새 설명 (null이면 변경 안 함)
     * @return 업데이트된 크리덴셜
     */
    @Transactional
    public Credential updateProjectCredential(UUID projectId, UUID credentialId, String secret, String description) {
        Credential credential = findProjectCredentialById(projectId, credentialId)
                .orElseThrow(() -> new IllegalArgumentException("Project credential not found"));

        if (secret != null) {
            credential.setEncryptedSecret(encryptionService.encrypt(secret));
        }
        if (description != null) {
            credential.setDescription(description);
        }
        credential.setUpdatedAt(Instant.now());

        log.info("AUDIT: Updated project credential: {} in project {}", credential.getName(), projectId);
        return credentialRepository.save(credential);
    }

    /**
     * 프로젝트 크리덴셜 삭제.
     *
     * @param projectId 프로젝트 ID
     * @param credentialId 크리덴셜 ID
     */
    @Transactional
    public void deleteProjectCredential(UUID projectId, UUID credentialId) {
        Credential credential = findProjectCredentialById(projectId, credentialId)
                .orElseThrow(() -> new IllegalArgumentException("Project credential not found"));

        credentialRepository.delete(credential);
        log.info("AUDIT: Deleted project credential: {} in project {}", credential.getName(), projectId);
    }
}
