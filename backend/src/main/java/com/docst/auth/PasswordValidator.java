package com.docst.auth;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 비밀번호 복잡도 검증 유틸리티.
 * 최소 8자, 최대 128자, 대문자/소문자/숫자/특수문자 중 3가지 이상 포함 검증.
 */
@Component
public class PasswordValidator {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 128;
    private static final Pattern UPPERCASE = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL = Pattern.compile("[^a-zA-Z0-9]");

    /**
     * 비밀번호 복잡도 검증
     * - 최소 8자, 최대 128자
     * - 대문자, 소문자, 숫자, 특수문자 중 3가지 이상 포함
     * - 동일 문자 3회 이상 연속 반복 금지
     */
    public void validate(String password) {
        if (password == null) {
            throw new IllegalArgumentException("비밀번호는 필수입니다.");
        }

        // 길이 검증
        if (password.length() < MIN_LENGTH) {
            throw new IllegalArgumentException(
                "비밀번호는 최소 " + MIN_LENGTH + "자 이상이어야 합니다."
            );
        }

        if (password.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                "비밀번호는 최대 " + MAX_LENGTH + "자까지 가능합니다."
            );
        }

        // 복잡도 검증
        int strength = 0;
        if (UPPERCASE.matcher(password).find()) strength++;
        if (LOWERCASE.matcher(password).find()) strength++;
        if (DIGIT.matcher(password).find()) strength++;
        if (SPECIAL.matcher(password).find()) strength++;

        if (strength < 3) {
            throw new IllegalArgumentException(
                "비밀번호는 대문자, 소문자, 숫자, 특수문자 중 3가지 이상을 포함해야 합니다."
            );
        }

        // 추가 검증: 반복 문자 제한
        if (hasRepeatingCharacters(password, 3)) {
            throw new IllegalArgumentException(
                "동일한 문자가 3회 이상 연속으로 반복될 수 없습니다."
            );
        }
    }

    /**
     * 연속 반복 문자 확인
     */
    private boolean hasRepeatingCharacters(String password, int maxRepeat) {
        for (int i = 0; i < password.length() - maxRepeat + 1; i++) {
            char current = password.charAt(i);
            boolean repeating = true;

            for (int j = 1; j < maxRepeat; j++) {
                if (password.charAt(i + j) != current) {
                    repeating = false;
                    break;
                }
            }

            if (repeating) {
                return true;
            }
        }

        return false;
    }

    /**
     * 비밀번호 강도 평가 (선택적)
     * @return 0-4 (약함-매우강함)
     */
    public int assessStrength(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            return 0;
        }

        int score = 0;

        // 길이 점수
        if (password.length() >= 12) score++;
        if (password.length() >= 16) score++;

        // 문자 종류 점수
        if (UPPERCASE.matcher(password).find()) score++;
        if (LOWERCASE.matcher(password).find()) score++;
        if (DIGIT.matcher(password).find()) score++;
        if (SPECIAL.matcher(password).find()) score++;

        return Math.min(score, 4);
    }
}
