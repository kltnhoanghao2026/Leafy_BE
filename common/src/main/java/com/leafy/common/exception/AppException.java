package com.leafy.common.exception;

import lombok.Getter;

@Getter
public class AppException extends RuntimeException {

    private final ErrorCode errorCode;

    /** Optional: the raw message from the downstream service, surfaced as-is when present. */
    private final String detail;

    public AppException(ErrorCode errorCode) {
        super(errorCode.getMessageKey());
        this.errorCode = errorCode;
        this.detail = null;
    }

    public AppException(ErrorCode errorCode, String detail) {
        super(detail != null ? detail : errorCode.getMessageKey());
        this.errorCode = errorCode;
        this.detail = detail;
    }
}