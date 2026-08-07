package com.wevolunteer.notification;

/** Mirrors the backend's {@code NotificationEventType} enum exactly. */
public enum NotificationEventType {
    REGISTRATION_CREATED,
    REGISTRATION_CANCELLED,
    REGISTRATION_CANCELLED_BY_ORGANIZATION,
    WAITLIST_JOINED,
    WAITLIST_LEFT,
    WAITLIST_CANCELLED_BY_ORGANIZATION
}
