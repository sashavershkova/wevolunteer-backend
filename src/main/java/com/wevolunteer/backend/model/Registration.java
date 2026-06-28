package com.wevolunteer.backend.model;

public record Registration(
        String userId,
        String opportunityId,
        String title,
        String date,
        String location,
        String organizationId,
        String organizationName,
        String registrationStatus
) {}