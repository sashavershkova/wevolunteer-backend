package com.wevolunteer.backend.dto;

import com.wevolunteer.backend.model.User;

/**
 * API representation of a volunteer profile.
 *
 * <p>Deliberately omits {@code profileImageKey}. The S3 object key is internal storage detail:
 * exposing it would leak the bucket layout and invite clients to send keys back to the server.
 * A temporary {@code profileImageUrl} is added in a later step.
 */
public record UserProfileResponse(
        String userId,
        String name,
        String email,
        String role
) {

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.userId(),
                user.name(),
                user.email(),
                user.role()
        );
    }
}
