package com.wevolunteer.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateUserRequest(
        @NotBlank String userId,
        @NotBlank String name,
        @Email @NotBlank String email,
        @NotBlank @Pattern(regexp = "VOLUNTEER|ORGANIZATION", message = "role must be one of: VOLUNTEER, ORGANIZATION") String role
) {}