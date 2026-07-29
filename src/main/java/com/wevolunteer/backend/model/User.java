package com.wevolunteer.backend.model;

/**
 * A volunteer profile.
 *
 * <p>{@code profileImageKey} is the S3 object key of the user's profile image, or {@code null}
 * when no image has been uploaded. It is a stable storage key such as
 * {@code users/<cognito-sub>/profile/<uuid>.jpg} — never image bytes, and never a pre-signed URL,
 * which would expire. It is internal to the backend and is not exposed by the API.
 */
public record User(
        String userId,
        String name,
        String email,
        String role,
        String profileImageKey
) {

    /** Creates a user with no profile image. */
    public User(String userId, String name, String email, String role) {
        this(userId, name, email, role, null);
    }
}
