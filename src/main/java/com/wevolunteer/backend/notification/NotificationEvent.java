package com.wevolunteer.backend.notification;

import java.time.Instant;

/**
 * The payload published to the SNS notification topic when a volunteer-facing event occurs.
 *
 * <p>Fields are sourced from the existing {@code User} and {@code Opportunity} models: userId,
 * volunteerName, and volunteerEmail come from {@code User}; opportunityId, opportunityTitle,
 * opportunityDate, organizationId, and organizationName come from {@code Opportunity}.
 *
 * <p>{@code opportunityDate} is a {@code String}, matching {@code Opportunity#date} exactly as
 * stored — the rest of the application (model, DTOs, repositories, controllers) also represents
 * this value as a plain string, so the notification contract mirrors that rather than
 * introducing a lone {@code LocalDate} conversion. {@code startTime}/{@code endTime} are
 * intentionally omitted for this event contract.
 *
 * <p>Not a DynamoDB item — this event is only ever serialized to JSON for SNS and carries no
 * persistence annotations.
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
