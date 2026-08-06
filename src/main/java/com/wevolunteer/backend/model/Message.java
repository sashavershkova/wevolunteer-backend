package com.wevolunteer.backend.model;

/**
 * A single message in a conversation.
 *
 * <p>{@code sentAt} is a fixed-width UTC timestamp (see {@code MessageService#now}) because it
 * is part of the DynamoDB sort key and must order lexicographically. {@code messageId} is
 * appended to that sort key to break ties between messages sent in the same millisecond.
 */
public record Message(
        String conversationId,
        String messageId,
        String senderId,
        ParticipantType senderType,
        String body,
        String sentAt
) {}
