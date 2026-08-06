package com.wevolunteer.backend.dto;

/**
 * An inbox row, shaped for the conversation list both Messages pages already render.
 *
 * <p>Deliberately counterpart-relative rather than exposing {@code userId}/{@code organizationId}:
 * the volunteer page shows organization names and the organization page shows volunteer names,
 * so both can bind to {@code counterpartName} and neither needs to know which side it is on.
 */
public record ConversationResponse(
        String conversationId,
        String counterpartId,
        String counterpartName,
        String previewLine,
        String lastMessageAt,
        int unreadCount
) {}
