package com.leafy.notificationservice.service;

public class PushDeliveryException extends Exception {

    private final String errorCode;

    public PushDeliveryException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
