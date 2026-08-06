package com.wevolunteer.backend.model;

/**
 * Which side of a conversation an identity is on.
 *
 * <p>Cognito issues one subject per account and the JWT carries no role claim, so the backend
 * resolves this by looking the subject up in {@code UserRepository} and then
 * {@code OrganizationRepository}. See {@code MessageService#resolveParticipants}.
 */
public enum ParticipantType {
    USER,
    ORGANIZATION
}
