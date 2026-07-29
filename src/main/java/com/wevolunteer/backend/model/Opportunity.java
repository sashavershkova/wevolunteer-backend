package com.wevolunteer.backend.model;

import java.util.List;

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
        List<String> whatYoullDo
) {}