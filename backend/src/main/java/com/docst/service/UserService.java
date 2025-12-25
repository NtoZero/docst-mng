package com.docst.service;

import com.docst.domain.User;
import com.docst.domain.User.AuthProvider;
import com.docst.repository.UserRepository;
import lombok.RequiredArgsConstructor;
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
}
