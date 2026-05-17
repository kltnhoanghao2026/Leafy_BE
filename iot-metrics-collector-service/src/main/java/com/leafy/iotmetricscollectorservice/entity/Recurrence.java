package com.leafy.iotmetricscollectorservice.entity;

/**
 * Recurrence policy for automatic camera capture schedules.
 */
public enum Recurrence {
    /**
     * One-shot schedule. It is disabled after the first successful trigger attempt.
     */
    NONE,

    /**
     * Run every day at the configured timeOfDay.
     */
    DAILY,

    /**
     * Run every seven days at the configured timeOfDay.
     */
    WEEKLY
}
