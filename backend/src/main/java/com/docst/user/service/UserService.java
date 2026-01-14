package com.docst.user.service;

import com.docst.auth.PasswordValidator;
import com.docst.user.User;
import com.docst.user.User.AuthProvider;
import com.docst.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * 사용자 서비스.
 * 사용자 계정에 대한 비즈니스 로직을 담당한다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordValidator passwordValidator;

    /**
     * ID로 사용자를 조회한다.
     *
     * @param id 사용자 ID
     * @return 사용자 (존재하지 않으면 empty)
     */
    public Optional<User> findById(UUID id) {
        return userRepository.findById(id);
    }

    /**
     * 인증 제공자와 제공자 사용자 ID로 사용자를 조회한다.
     *
     * @param provider 인증 제공자
     * @param providerUserId 제공자 사용자 ID
     * @return 사용자 (존재하지 않으면 empty)
     */
    public Optional<User> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId) {
        return userRepository.findByProviderAndProviderUserId(provider, providerUserId);
    }

    /**
     * 로컬 사용자를 생성하거나 업데이트한다.
     * 이메일을 기준으로 기존 사용자가 있으면 업데이트하고, 없으면 새로 생성한다.
     *
     * @param email 이메일 주소
     * @param displayName 표시 이름
     * @return 생성 또는 업데이트된 사용자
     */
    @Transactional
    public User createOrUpdateLocalUser(String email, String displayName) {
        return userRepository.findByProviderAndProviderUserId(AuthProvider.LOCAL, email)
                .map(user -> {
                    user.setDisplayName(displayName);
                    return userRepository.save(user);
                })
                .orElseGet(() -> {
                    User user = new User(AuthProvider.LOCAL, email, email, displayName);
                    return userRepository.save(user);
                });
    }

    /**
     * GitHub 사용자를 생성하거나 업데이트한다.
     * GitHub 사용자 ID를 기준으로 기존 사용자가 있으면 업데이트하고, 없으면 새로 생성한다.
     *
     * @param providerUserId GitHub 사용자 ID
     * @param email 이메일 주소
     * @param displayName 표시 이름
     * @return 생성 또는 업데이트된 사용자
     */
    @Transactional
    public User createOrUpdateGitHubUser(String providerUserId, String email, String displayName) {
        return userRepository.findByProviderAndProviderUserId(AuthProvider.GITHUB, providerUserId)
                .map(user -> {
                    user.setEmail(email);
                    user.setDisplayName(displayName);
                    return userRepository.save(user);
                })
                .orElseGet(() -> {
                    User user = new User(AuthProvider.GITHUB, providerUserId, email, displayName);
                    return userRepository.save(user);
                });
    }

    /**
     * 비밀번호를 사용하는 LOCAL 사용자를 생성한다.
     * 이메일 중복 여부를 확인하고, 비밀번호 복잡도를 검증한 후 사용자를 생성한다.
     *
     * @param email 이메일 주소
     * @param password 평문 비밀번호
     * @param displayName 표시 이름
     * @return 생성된 사용자
     * @throws IllegalArgumentException 이메일이 중복되거나 비밀번호가 유효하지 않은 경우
     */
    @Transactional
    public User createLocalUser(String email, String password, String displayName) {
        // 이메일 중복 확인
        if (userRepository.findByProviderAndProviderUserId(AuthProvider.LOCAL, email).isPresent()) {
            throw new IllegalArgumentException("Email already exists: " + email);
        }

        // 비밀번호 복잡도 검증
        passwordValidator.validate(password);

        // 사용자 생성
        User user = new User(AuthProvider.LOCAL, email, email, displayName);
        String hashedPassword = passwordEncoder.encode(password);
        user.setPasswordHash(hashedPassword);

        return userRepository.save(user);
    }

    /**
     * LOCAL 사용자의 이메일과 비밀번호를 검증한다.
     *
     * @param email 이메일 주소
     * @param password 평문 비밀번호
     * @return 인증된 사용자
     * @throws BadCredentialsException 인증 실패 시
     */
    public User authenticateLocalUser(String email, String password) {
        User user = userRepository.findByProviderAndProviderUserId(AuthProvider.LOCAL, email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!user.hasPassword()) {
            throw new BadCredentialsException("User does not have password authentication enabled");
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        return user;
    }

    /**
     * 사용자의 비밀번호를 변경한다.
     * 기존 비밀번호를 검증하고, 새 비밀번호의 복잡도를 확인한 후 변경한다.
     *
     * @param userId 사용자 ID
     * @param oldPassword 기존 평문 비밀번호
     * @param newPassword 새 평문 비밀번호
     * @throws IllegalArgumentException 사용자가 존재하지 않거나 비밀번호가 유효하지 않은 경우
     * @throws BadCredentialsException 기존 비밀번호가 일치하지 않는 경우
     */
    @Transactional
    public void changePassword(UUID userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!user.isLocalUser()) {
            throw new IllegalArgumentException("Only LOCAL users can change password");
        }

        if (!user.hasPassword()) {
            throw new IllegalArgumentException("User does not have password authentication enabled");
        }

        // 기존 비밀번호 확인
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new BadCredentialsException("Current password is incorrect");
        }

        // 새 비밀번호 복잡도 검증
        passwordValidator.validate(newPassword);

        // 새 비밀번호와 기존 비밀번호가 동일한지 확인
        if (oldPassword.equals(newPassword)) {
            throw new IllegalArgumentException("New password must be different from current password");
        }

        // 비밀번호 변경
        String hashedPassword = passwordEncoder.encode(newPassword);
        user.setPasswordHash(hashedPassword);
        userRepository.save(user);
    }
}
