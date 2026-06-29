package com.wevolunteer.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record RegisterRequest(
        @NotBlank String userId,
        @NotBlank String opportunityId
) {}