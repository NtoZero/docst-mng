package com.docst.domain;

/**
 * 프로젝트 멤버 역할.
 * 역할에 따라 프로젝트 내 권한이 결정된다.
 */
public enum ProjectRole {
    /** 프로젝트 소유자 - 모든 권한 */
    OWNER,
    /** 관리자 - 레포 관리, 동기화 실행 */
    ADMIN,
    /** 편집자 - 문서 수정 (향후) */
    EDITOR,
    /** 뷰어 - 읽기 전용 */
    VIEWER;

    /**
     * 현재 역할이 요구 역할 이상의 권한을 가지는지 확인한다.
     *
     * @param required 요구되는 역할
     * @return 권한이 있으면 true
     */
    public boolean hasPermission(ProjectRole required) {
        return this.ordinal() <= required.ordinal();
    }
}
