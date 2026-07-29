package com.wevolunteer.backend.model;

import java.util.List;

/**
 * recurring: whether this opportunity represents an ongoing/repeating commitment
 * (e.g. a weekly shift) rather than a single one-time event. Shown as the wireframe's
 * "Ongoing" badge; the {@code date} field still holds the opportunity's own event date.
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
            List<String> whatYoullDo,
            boolean recurring) {

        this(opportunityId, title, description, category, location, date, status,
                organizationId, organizationName, capacity, registeredCount, availableSpots,
                time, whatYoullDo, recurring, null);
    }
}