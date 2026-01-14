package com.docst.user.repository;

import com.docst.user.User;
import com.docst.user.User.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * 사용자 레포지토리.
 * 사용자 엔티티에 대한 데이터 접근을 제공한다.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * 인증 제공자와 제공자 사용자 ID로 사용자를 조회한다.
     *
     * @param provider 인증 제공자
     * @param providerUserId 제공자 사용자 ID
     * @return 사용자 (존재하지 않으면 empty)
     */
    Optional<User> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);

    /**
     * 이메일로 사용자를 조회한다.
     *
     * @param email 이메일 주소
     * @return 사용자 (존재하지 않으면 empty)
     */
    Optional<User> findByEmail(String email);

    /**
     * 특정 인증 제공자와 제공자 사용자 ID를 가진 사용자가 존재하는지 확인한다.
     *
     * @param provider 인증 제공자
     * @param providerUserId 제공자 사용자 ID
     * @return 존재 여부
     */
    boolean existsByProviderAndProviderUserId(AuthProvider provider, String providerUserId);
}
