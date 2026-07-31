package com.wevolunteer.backend.dto;

import com.wevolunteer.backend.model.User;

/**
 * API representation of a volunteer profile.
 *
 * <p>Deliberately omits {@code profileImageKey}. The S3 object key is internal storage detail:
 * exposing it would leak the bucket layout and invite clients to send keys back to the server.
 * {@code profileImageUrl} is a temporary pre-signed link, or {@code null} when the profile has no
 * image, in which case the frontend shows its placeholder.
 */
public record UserProfileResponse(
        String userId,
        String name,
        String email,
        String role,
        String profileImageUrl
) {

    /** For contexts with no display URL, such as tests and internal mapping. */
    public static UserProfileResponse from(User user) {
        return from(user, null);
    }

    public static UserProfileResponse from(User user, String profileImageUrl) {
        return new UserProfileResponse(
                user.userId(),
                user.name(),
                user.email(),
                user.role(),
                profileImageUrl
        );
    }
}
