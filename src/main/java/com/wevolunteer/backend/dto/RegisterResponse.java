package com.wevolunteer.backend.dto;

public record RegisterResponse(
        String message,
        String userId,
        String opportunityId,
        int registeredCount,
        int availableSpots
) {}