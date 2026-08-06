package com.wevolunteer.backend.model;

/**
 * The identity of a conversation, derived deterministically from its two participants.
 *
 * <p>There is exactly one conversation per volunteer/organization pair, so the id is simply
 * {@code <userId>_<organizationId>}. Deriving rather than generating the id buys two things:
 * starting a conversation is naturally idempotent (no "does a thread already exist?" query
 * before every send), and authorizing a read is a string comparison against the JWT subject
 * rather than a lookup of the conversation's participant list.
 *
 * <p>Both ids are Cognito subjects — UUIDs, which contain hyphens but never underscores — so
 * {@code _} is an unambiguous separator. The constructor rejects ids containing one anyway,
 * since a separator collision would silently let one participant impersonate another.
 */
public record ConversationId(String userId, String organizationId) {

    private static final String SEPARATOR = "_";

    public ConversationId {
        requireUsableId(userId, "userId");
        requireUsableId(organizationId, "organizationId");
    }

    private static void requireUsableId(String id, String fieldName) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }

        if (id.contains(SEPARATOR)) {
            throw new IllegalArgumentException(
                    fieldName + " must not contain '" + SEPARATOR + "'");
        }
    }

    /** Parses the wire form produced by {@link #value()}. */
    public static ConversationId parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("conversationId must not be blank");
        }

        String[] parts = value.split(SEPARATOR, -1);

        if (parts.length != 2) {
            throw new IllegalArgumentException("Malformed conversationId: " + value);
        }

        return new ConversationId(parts[0], parts[1]);
    }

    /** The wire form: what clients send back in a path variable. */
    public String value() {
        return userId + SEPARATOR + organizationId;
    }

    /** Whether the given Cognito subject is one of the two participants. */
    public boolean includes(String participantId) {
        return userId.equals(participantId) || organizationId.equals(participantId);
    }

    /** Which side the given participant is on, or {@code null} if they are not a participant. */
    public ParticipantType participantType(String participantId) {
        if (userId.equals(participantId)) {
            return ParticipantType.USER;
        }

        if (organizationId.equals(participantId)) {
            return ParticipantType.ORGANIZATION;
        }

        return null;
    }
}
