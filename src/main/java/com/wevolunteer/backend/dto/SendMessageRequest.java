package com.wevolunteer.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Posts a message to a conversation that already exists. */
public record SendMessageRequest(
        @NotBlank @Size(max = 2000) String body
) {}
