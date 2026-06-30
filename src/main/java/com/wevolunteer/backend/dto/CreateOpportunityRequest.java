package com.wevolunteer.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateOpportunityRequest(
        @NotBlank String opportunityId,
        @NotBlank String title,
        @NotBlank String description,
        @NotBlank String category,
        @NotBlank String location,
        @NotBlank String date,
        @Min(1) int capacity
) {}