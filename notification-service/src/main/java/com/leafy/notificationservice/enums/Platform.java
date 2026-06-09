package com.leafy.notificationservice.enums;

/**
 * Push token platform identifiers.
 * Stored in MongoDB as the enum name string (e.g. {@code "ANDROID"}).
 */
public enum Platform {
    ANDROID,
    IOS,
    WEB
}
