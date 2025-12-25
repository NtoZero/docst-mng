package com.docst.service;

import com.docst.domain.User;
import com.docst.domain.User.AuthProvider;
import com.docst.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<User> findById(UUID id) {
        return userRepository.findById(id);
    }

    public Optional<User> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId) {
        return userRepository.findByProviderAndProviderUserId(provider, providerUserId);
    }

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
