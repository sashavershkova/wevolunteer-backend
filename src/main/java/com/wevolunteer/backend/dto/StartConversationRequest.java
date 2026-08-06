package com.wevolunteer.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Opens a conversation with the given counterpart and posts the first message.
 *
 * <p>There is no {@code senderId}: the sender is always the JWT subject. {@code recipientId} is
 * the counterpart's Cognito subject — an organization id when a volunteer sends, a volunteer id
 * when an organization sends. The service decides which by resolving the sender's own type.
 */
public record StartConversationRequest(
        @NotBlank String recipientId,
        @NotBlank @Size(max = 2000) String body
) {}
