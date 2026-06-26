package com.wevolunteer.backend.model;

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
        int availableSpots
) {}