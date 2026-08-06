package com.wevolunteer.backend.model;

/**
 * One participant's view of a conversation — the inbox row, not the thread.
 *
 * <p>Stored twice per conversation (once under {@code USER#}, once under {@code ORG#}), each
 * copy carrying the counterpart's name and that participant's own unread count. The denormalized
 * {@code lastMessagePreview}/{@code lastMessageAt} snapshot means rendering an inbox costs one
 * query and no fan-out into message threads — the same trade-off {@code Favorite} and
 * {@code Registration} already make.
 */
public record Conversation(
        String conversationId,
        String userId,
        String organizationId,
        String counterpartName,
        String lastMessagePreview,
        String lastMessageAt,
        String lastMessageSenderId,
        int unreadCount
) {}
