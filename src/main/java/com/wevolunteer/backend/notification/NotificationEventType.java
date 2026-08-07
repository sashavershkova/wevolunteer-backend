package com.wevolunteer.backend.notification;

/** The set of volunteer-facing events that publish a notification. */
public enum NotificationEventType {
    REGISTRATION_CREATED,
    REGISTRATION_CANCELLED,
    REGISTRATION_CANCELLED_BY_ORGANIZATION,
    WAITLIST_JOINED,
    WAITLIST_LEFT,
    WAITLIST_CANCELLED_BY_ORGANIZATION
}
