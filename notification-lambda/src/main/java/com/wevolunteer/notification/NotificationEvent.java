package com.wevolunteer.notification;

import java.time.Instant;

/**
 * Mirrors the backend's {@code com.wevolunteer.backend.notification.NotificationEvent} contract
 * field-for-field. This module has no dependency on the backend module, so the shape is
 * duplicated here rather than shared; keep the two in sync by hand if the contract changes.
 */
public record NotificationEvent(
        NotificationEventType eventType,
        String userId,
        String volunteerName,
        String volunteerEmail,
        String opportunityId,
        String opportunityTitle,
        String opportunityDate,
        String organizationId,
        String organizationName,
        Instant timestamp
) {}
