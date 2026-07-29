package com.wevolunteer.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record UpdateOpportunityRequest(
        @NotBlank String title,
        @NotBlank String description,
        @NotBlank String category,
        @NotBlank String location,
        @NotBlank String date,
        @NotBlank String status,
        @Min(1) int capacity,
        @NotBlank String startTime,
        @NotBlank String endTime,
        List<String> whatYoullDo,
        boolean recurring
) {}