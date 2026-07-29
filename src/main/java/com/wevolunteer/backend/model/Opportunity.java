package com.wevolunteer.backend.model;

import java.util.List;

/**
 * recurring: whether this opportunity represents an ongoing/repeating commitment
 * (e.g. a weekly shift) rather than a single one-time event. Shown as the wireframe's
 * "Ongoing" badge; the {@code date} field still holds the opportunity's own event date.
 *
 * <p>time: legacy free-text time carried over from before startTime/endTime existed. Retained
 * only so older DynamoDB records can still display something; new create/update operations
 * always leave it {@code null} rather than inventing one from startTime/endTime.
 *
 * <p>startTime/endTime: structured HH:mm values (24-hour, no time zone conversion) describing
 * when the opportunity begins and ends on its {@code date}. These are the source of truth for
 * new and edited opportunities.
 *
 * <p>imageKey: the S3 object key of the opportunity's image, or {@code null} when none was
 * uploaded. A stable storage key such as
 * {@code organizations/<cognito-sub>/opportunities/<uuid>.jpg} — never image bytes, and never a
 * pre-signed URL, which would expire.
 */
public record Opportunity(
        String opportunityId,
        String title,
        String description,
        String category,
        String location,
        String date,
        String status,
        String organizationId,
        String organizationName,
        int capacity,
        int registeredCount,
        int availableSpots,
        String time,
        String startTime,
        String endTime,
        List<String> whatYoullDo,
        boolean recurring,
        String imageKey
) {

    /** Creates an opportunity with no image. */
    public Opportunity(
            String opportunityId,
            String title,
            String description,
            String category,
            String location,
            String date,
            String status,
            String organizationId,
            String organizationName,
            int capacity,
            int registeredCount,
            int availableSpots,
            String time,
            String startTime,
            String endTime,
            List<String> whatYoullDo,
            boolean recurring) {

        this(opportunityId, title, description, category, location, date, status,
                organizationId, organizationName, capacity, registeredCount, availableSpots,
                time, startTime, endTime, whatYoullDo, recurring, null);
    }
}