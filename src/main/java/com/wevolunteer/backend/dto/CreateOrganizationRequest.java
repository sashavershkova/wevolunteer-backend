package com.wevolunteer.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateOrganizationRequest(
        @NotBlank String organizationId,
        @NotBlank String name,
        String description,
        @Email @NotBlank String email,
        String website
) {}