package com.docst.auth;

/**
 * 권한 거부 예외.
 * 사용자가 요청한 작업에 대한 권한이 없을 때 발생한다.
 */
public class PermissionDeniedException extends RuntimeException {

    public PermissionDeniedException(String message) {
        super(message);
    }

    public PermissionDeniedException(String message, Throwable cause) {
        super(message, cause);
    }
}
