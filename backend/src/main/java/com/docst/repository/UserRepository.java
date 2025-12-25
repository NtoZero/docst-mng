package com.docst.repository;

import com.docst.domain.User;
import com.docst.domain.User.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);

    Optional<User> findByEmail(String email);

    boolean existsByProviderAndProviderUserId(AuthProvider provider, String providerUserId);
}
