package com.wevolunteer.backend.dto;

import com.wevolunteer.backend.model.ParticipantType;

/**
 * A single message in a thread.
 *
 * <p>No {@code sentByMe} flag: the client already knows its own Cognito subject and can compare
 * it to {@code senderId}, so adding one would make the payload depend on who asked for it.
 */
public record MessageResponse(
        String messageId,
        String conversationId,
        String senderId,
        ParticipantType senderType,
        String body,
        String sentAt
) {}
