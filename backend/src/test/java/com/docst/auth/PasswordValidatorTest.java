package com.docst.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * PasswordValidator 단위 테스트.
 */
@DisplayName("PasswordValidator")
class PasswordValidatorTest {

    private PasswordValidator passwordValidator;

    @BeforeEach
    void setUp() {
        passwordValidator = new PasswordValidator();
    }

    @Test
    @DisplayName("유효한 비밀번호는 검증을 통과한다")
    void validPassword_shouldPass() {
        // Given: 복잡도 요구사항을 만족하는 비밀번호
        String validPassword = "MyP@ssw0rd";

        // When & Then: 예외가 발생하지 않아야 함
        assertDoesNotThrow(() -> passwordValidator.validate(validPassword));
    }

    @Test
    @DisplayName("null 비밀번호는 예외를 발생시킨다")
    void nullPassword_shouldThrowException() {
        // When & Then
        assertThatThrownBy(() -> passwordValidator.validate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("필수");
    }

    @Test
    @DisplayName("빈 문자열 비밀번호는 예외를 발생시킨다")
    void emptyPassword_shouldThrowException() {
        // When & Then
        assertThatThrownBy(() -> passwordValidator.validate(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("8자 이상");
    }

    @Test
    @DisplayName("8자 미만 비밀번호는 예외를 발생시킨다")
    void tooShortPassword_shouldThrowException() {
        // Given: 7자 비밀번호
        String shortPassword = "Ab1@567";

        // When & Then
        assertThatThrownBy(() -> passwordValidator.validate(shortPassword))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("8자 이상");
    }

    @Test
    @DisplayName("128자 초과 비밀번호는 예외를 발생시킨다")
    void tooLongPassword_shouldThrowException() {
        // Given: 129자 비밀번호
        String longPassword = "A1@" + "a".repeat(126);

        // When & Then
        assertThatThrownBy(() -> passwordValidator.validate(longPassword))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("128자");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "abcdefgh",           // 소문자만
            "ABCDEFGH",           // 대문자만
            "12345678",           // 숫자만
            "!@#$%^&*",           // 특수문자만
            "abcdefgh1234",       // 소문자 + 숫자 (2가지)
            "ABCDEFGH1234",       // 대문자 + 숫자 (2가지)
            "abcdefgh!@#$"        // 소문자 + 특수문자 (2가지)
    })
    @DisplayName("3가지 미만 문자 종류를 포함한 비밀번호는 예외를 발생시킨다")
    void insufficientComplexity_shouldThrowException(String password) {
        // When & Then
        assertThatThrownBy(() -> passwordValidator.validate(password))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("3가지 이상");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Abc123!@#",          // 대문자 + 소문자 + 숫자 + 특수문자 (4가지)
            "Abcdef123",          // 대문자 + 소문자 + 숫자 (3가지)
            "Abcdef!@#",          // 대문자 + 소문자 + 특수문자 (3가지)
            "ABCD1234!@#"         // 대문자 + 숫자 + 특수문자 (3가지)
    })
    @DisplayName("3가지 이상 문자 종류를 포함한 비밀번호는 검증을 통과한다")
    void sufficientComplexity_shouldPass(String password) {
        // When & Then
        assertDoesNotThrow(() -> passwordValidator.validate(password));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Aaaa1234",           // 'a' 4번 연속 (3번 이상)
            "AB111cde",           // '1' 3번 연속
            "Abc!!!12",           // '!' 3번 연속
            "Password000"         // '0' 3번 연속
    })
    @DisplayName("동일 문자 3회 이상 연속 반복 비밀번호는 예외를 발생시킨다")
    void repeatingCharacters_shouldThrowException(String password) {
        // When & Then
        assertThatThrownBy(() -> passwordValidator.validate(password))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("3회 이상 연속");
    }

    @Test
    @DisplayName("동일 문자 2회 연속은 허용된다")
    void twoRepeatingCharacters_shouldPass() {
        // Given: 'a'가 2번 연속
        String password = "Aab12345";

        // When & Then
        assertDoesNotThrow(() -> passwordValidator.validate(password));
    }

    @Test
    @DisplayName("비밀번호 강도 평가: 약함 (8자, 2가지 종류)")
    void assessStrength_weak() {
        // Given
        String weakPassword = "abcd1234";

        // When
        int strength = passwordValidator.assessStrength(weakPassword);

        // Then: 길이(8자 미만) + 소문자 + 숫자 = 2
        assertThat(strength).isEqualTo(2);
    }

    @Test
    @DisplayName("비밀번호 강도 평가: 보통 (12자, 3가지 종류)")
    void assessStrength_medium() {
        // Given
        String mediumPassword = "Abcdef123456";

        // When
        int strength = passwordValidator.assessStrength(mediumPassword);

        // Then: 길이(12자) + 대문자 + 소문자 + 숫자 = 4
        assertThat(strength).isEqualTo(4);
    }

    @Test
    @DisplayName("비밀번호 강도 평가: 강함 (16자, 4가지 종류)")
    void assessStrength_strong() {
        // Given
        String strongPassword = "Abcdef123456!@#$";

        // When
        int strength = passwordValidator.assessStrength(strongPassword);

        // Then: 길이(12자+16자) + 대문자 + 소문자 + 숫자 + 특수문자 = 6, but max 4
        assertThat(strength).isEqualTo(4);
    }

    @Test
    @DisplayName("비밀번호 강도 평가: 너무 짧은 비밀번호는 0점")
    void assessStrength_tooShort() {
        // Given
        String tooShort = "Abc123!";

        // When
        int strength = passwordValidator.assessStrength(tooShort);

        // Then
        assertThat(strength).isEqualTo(0);
    }

    @Test
    @DisplayName("비밀번호 강도 평가: null은 0점")
    void assessStrength_null() {
        // When
        int strength = passwordValidator.assessStrength(null);

        // Then
        assertThat(strength).isEqualTo(0);
    }
}
