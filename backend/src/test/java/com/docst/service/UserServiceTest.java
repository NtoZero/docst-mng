package com.docst.service;

import com.docst.auth.PasswordValidator;
import com.docst.domain.User;
import com.docst.domain.User.AuthProvider;
import com.docst.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * UserService 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PasswordValidator passwordValidator;

    @InjectMocks
    private UserService userService;

    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_PASSWORD = "TestP@ssw0rd";
    private static final String TEST_DISPLAY_NAME = "Test User";

    @Test
    @DisplayName("createLocalUser: 새 사용자를 성공적으로 생성한다")
    void createLocalUser_success() {
        // Given
        when(userRepository.findByProviderAndProviderUserId(AuthProvider.LOCAL, TEST_EMAIL))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode(TEST_PASSWORD))
                .thenReturn("$argon2id$v=19$m=19456,t=2,p=1$...");
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        User user = userService.createLocalUser(TEST_EMAIL, TEST_PASSWORD, TEST_DISPLAY_NAME);

        // Then
        assertThat(user).isNotNull();
        assertThat(user.getEmail()).isEqualTo(TEST_EMAIL);
        assertThat(user.getDisplayName()).isEqualTo(TEST_DISPLAY_NAME);
        assertThat(user.getProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(user.getProviderUserId()).isEqualTo(TEST_EMAIL);

        verify(passwordValidator).validate(TEST_PASSWORD);
        verify(passwordEncoder).encode(TEST_PASSWORD);
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("createLocalUser: 이메일 중복 시 예외를 발생시킨다")
    void createLocalUser_duplicateEmail_throwsException() {
        // Given: 이미 존재하는 사용자
        User existingUser = new User(AuthProvider.LOCAL, TEST_EMAIL, TEST_EMAIL, TEST_DISPLAY_NAME);
        when(userRepository.findByProviderAndProviderUserId(AuthProvider.LOCAL, TEST_EMAIL))
                .thenReturn(Optional.of(existingUser));

        // When & Then
        assertThatThrownBy(() -> userService.createLocalUser(TEST_EMAIL, TEST_PASSWORD, TEST_DISPLAY_NAME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email already exists");

        verify(passwordValidator, never()).validate(anyString());
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("createLocalUser: 잘못된 비밀번호는 예외를 발생시킨다")
    void createLocalUser_invalidPassword_throwsException() {
        // Given
        when(userRepository.findByProviderAndProviderUserId(AuthProvider.LOCAL, TEST_EMAIL))
                .thenReturn(Optional.empty());
        doThrow(new IllegalArgumentException("비밀번호는 최소 8자 이상이어야 합니다."))
                .when(passwordValidator).validate(anyString());

        // When & Then
        assertThatThrownBy(() -> userService.createLocalUser(TEST_EMAIL, "weak", TEST_DISPLAY_NAME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("8자 이상");

        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("authenticateLocalUser: 올바른 이메일과 비밀번호로 인증에 성공한다")
    void authenticateLocalUser_success() {
        // Given
        User user = new User(AuthProvider.LOCAL, TEST_EMAIL, TEST_EMAIL, TEST_DISPLAY_NAME);
        String hashedPassword = "$argon2id$v=19$m=19456,t=2,p=1$...";
        user.setPasswordHash(hashedPassword);

        when(userRepository.findByProviderAndProviderUserId(AuthProvider.LOCAL, TEST_EMAIL))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches(TEST_PASSWORD, hashedPassword))
                .thenReturn(true);

        // When
        User authenticatedUser = userService.authenticateLocalUser(TEST_EMAIL, TEST_PASSWORD);

        // Then
        assertThat(authenticatedUser).isEqualTo(user);
        verify(passwordEncoder).matches(TEST_PASSWORD, hashedPassword);
    }

    @Test
    @DisplayName("authenticateLocalUser: 존재하지 않는 이메일은 예외를 발생시킨다")
    void authenticateLocalUser_userNotFound_throwsException() {
        // Given
        when(userRepository.findByProviderAndProviderUserId(AuthProvider.LOCAL, TEST_EMAIL))
                .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userService.authenticateLocalUser(TEST_EMAIL, TEST_PASSWORD))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid email or password");

        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("authenticateLocalUser: 잘못된 비밀번호는 예외를 발생시킨다")
    void authenticateLocalUser_wrongPassword_throwsException() {
        // Given
        User user = new User(AuthProvider.LOCAL, TEST_EMAIL, TEST_EMAIL, TEST_DISPLAY_NAME);
        String hashedPassword = "$argon2id$v=19$m=19456,t=2,p=1$...";
        user.setPasswordHash(hashedPassword);

        when(userRepository.findByProviderAndProviderUserId(AuthProvider.LOCAL, TEST_EMAIL))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches(TEST_PASSWORD, hashedPassword))
                .thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> userService.authenticateLocalUser(TEST_EMAIL, TEST_PASSWORD))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    @DisplayName("authenticateLocalUser: 비밀번호가 없는 사용자는 예외를 발생시킨다")
    void authenticateLocalUser_noPassword_throwsException() {
        // Given: 비밀번호가 설정되지 않은 사용자
        User user = new User(AuthProvider.LOCAL, TEST_EMAIL, TEST_EMAIL, TEST_DISPLAY_NAME);
        // passwordHash is null

        when(userRepository.findByProviderAndProviderUserId(AuthProvider.LOCAL, TEST_EMAIL))
                .thenReturn(Optional.of(user));

        // When & Then
        assertThatThrownBy(() -> userService.authenticateLocalUser(TEST_EMAIL, TEST_PASSWORD))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("password authentication enabled");

        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("changePassword: 비밀번호 변경에 성공한다")
    void changePassword_success() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = new User(AuthProvider.LOCAL, TEST_EMAIL, TEST_EMAIL, TEST_DISPLAY_NAME);
        String oldHashedPassword = "$argon2id$old";
        user.setPasswordHash(oldHashedPassword);

        String oldPassword = "OldP@ssw0rd";
        String newPassword = "NewP@ssw0rd";
        String newHashedPassword = "$argon2id$new";

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(oldPassword, oldHashedPassword)).thenReturn(true);
        when(passwordEncoder.encode(newPassword)).thenReturn(newHashedPassword);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        userService.changePassword(userId, oldPassword, newPassword);

        // Then
        verify(passwordValidator).validate(newPassword);
        verify(passwordEncoder).matches(oldPassword, oldHashedPassword);
        verify(passwordEncoder).encode(newPassword);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("changePassword: 사용자가 존재하지 않으면 예외를 발생시킨다")
    void changePassword_userNotFound_throwsException() {
        // Given
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userService.changePassword(userId, "old", "new"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    @DisplayName("changePassword: LOCAL 사용자가 아니면 예외를 발생시킨다")
    void changePassword_notLocalUser_throwsException() {
        // Given: GITHUB 사용자
        UUID userId = UUID.randomUUID();
        User githubUser = new User(AuthProvider.GITHUB, "123456", TEST_EMAIL, TEST_DISPLAY_NAME);

        when(userRepository.findById(userId)).thenReturn(Optional.of(githubUser));

        // When & Then
        assertThatThrownBy(() -> userService.changePassword(userId, "old", "new"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only LOCAL users");
    }

    @Test
    @DisplayName("changePassword: 현재 비밀번호가 틀리면 예외를 발생시킨다")
    void changePassword_wrongCurrentPassword_throwsException() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = new User(AuthProvider.LOCAL, TEST_EMAIL, TEST_EMAIL, TEST_DISPLAY_NAME);
        String hashedPassword = "$argon2id$old";
        user.setPasswordHash(hashedPassword);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPassword", hashedPassword)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> userService.changePassword(userId, "wrongPassword", "NewP@ssw0rd"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Current password is incorrect");

        verify(passwordValidator, never()).validate(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("changePassword: 새 비밀번호가 기존과 동일하면 예외를 발생시킨다")
    void changePassword_samePassword_throwsException() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = new User(AuthProvider.LOCAL, TEST_EMAIL, TEST_EMAIL, TEST_DISPLAY_NAME);
        String hashedPassword = "$argon2id$old";
        user.setPasswordHash(hashedPassword);

        String password = "SameP@ssw0rd";

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(password, hashedPassword)).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> userService.changePassword(userId, password, password))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be different");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("changePassword: 새 비밀번호가 복잡도 요구사항을 만족하지 않으면 예외를 발생시킨다")
    void changePassword_invalidNewPassword_throwsException() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = new User(AuthProvider.LOCAL, TEST_EMAIL, TEST_EMAIL, TEST_DISPLAY_NAME);
        String hashedPassword = "$argon2id$old";
        user.setPasswordHash(hashedPassword);

        String oldPassword = "OldP@ssw0rd";
        String weakNewPassword = "weak";

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(oldPassword, hashedPassword)).thenReturn(true);
        doThrow(new IllegalArgumentException("비밀번호는 최소 8자 이상이어야 합니다."))
                .when(passwordValidator).validate(weakNewPassword);

        // When & Then
        assertThatThrownBy(() -> userService.changePassword(userId, oldPassword, weakNewPassword))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("8자 이상");

        verify(userRepository, never()).save(any(User.class));
    }
}
