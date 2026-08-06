package com.wevolunteer.backend.model;

/**
 * Both sides of a conversation with the display names needed to write inbox snapshots.
 *
 * <p>Each participant's inbox item stores the <em>other</em> party's name, so a single send
 * needs both names in hand. Resolved once in the service and handed to the repository, which
 * keeps the repository free of {@code UserRepository}/{@code OrganizationRepository} lookups.
 */
public record ConversationParticipants(
        String userId,
        String userName,
        String organizationId,
        String organizationName
) {

    public ConversationId conversationId() {
        return new ConversationId(userId, organizationId);
    }

    /** The display name the given side sees in their inbox — always the counterpart's. */
    public String counterpartNameFor(ParticipantType viewerType) {
        return viewerType == ParticipantType.USER ? organizationName : userName;
    }
}
