package com.wevolunteer.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateUserRequest(
        @NotBlank String userId,
        @NotBlank String name,
        @Email @NotBlank String email,
        @NotBlank String role
) {}