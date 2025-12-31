package com.docst.domain;

/**
 * 크리덴셜 스코프.
 * 크리덴셜이 어느 레벨에서 사용되는지 정의한다.
 */
public enum CredentialScope {
    /**
     * 사용자별 크리덴셜 (기존 동작).
     * user_id 필수, project_id null
     */
    USER,

    /**
     * 시스템 전역 크리덴셜 (관리자만 생성 가능).
     * user_id null, project_id null
     */
    SYSTEM,

    /**
     * 프로젝트별 크리덴셜.
     * project_id 필수, user_id 선택 (생성자 추적용)
     */
    PROJECT
}
