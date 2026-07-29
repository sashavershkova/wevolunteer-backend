package com.wevolunteer.backend.model;

import java.util.List;

/**
 * recurring: whether this opportunity represents an ongoing/repeating commitment
 * (e.g. a weekly shift) rather than a single one-time event. Shown as the wireframe's
 * "Ongoing" badge; the {@code date} field still holds the opportunity's own event date.
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
        boolean recurring
) {}