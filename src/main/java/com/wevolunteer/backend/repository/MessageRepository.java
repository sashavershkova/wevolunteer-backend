package com.wevolunteer.backend.repository;

import com.wevolunteer.backend.model.Conversation;
import com.wevolunteer.backend.model.ConversationId;
import com.wevolunteer.backend.model.ConversationParticipants;
import com.wevolunteer.backend.model.Message;
import com.wevolunteer.backend.model.ParticipantType;

import java.util.List;
import java.util.Optional;

public interface MessageRepository {

    /**
     * Every conversation the given participant is part of, most recently active first.
     *
     * @param participantId the participant's Cognito subject
     * @param participantType which side of the conversation they are on, which selects the
     *                        {@code USER#}/{@code ORG#} partition to read
     */
    List<Conversation> findConversationsByParticipant(
            String participantId, ParticipantType participantType);

    /** The given participant's view of one conversation, empty if it does not exist yet. */
    Optional<Conversation> findConversation(
            ConversationId conversationId, ParticipantType viewerType);

    /** Every message in a conversation, oldest first. */
    List<Message> findMessages(ConversationId conversationId);

    /**
     * Appends a message and refreshes both participants' inbox snapshots in one transaction,
     * incrementing only the recipient's unread count.
     *
     * <p>Takes {@code participants} rather than looking up names itself so the repository stays
     * a persistence concern; the service has already resolved and authorized both sides.
     */
    void saveMessage(Message message, ConversationParticipants participants);

    /** Zeroes the given participant's unread count. A no-op if they have no such conversation. */
    void markRead(ConversationId conversationId, ParticipantType readerType);
}
