package com.wevolunteer.backend.model;

/**
 * An organization profile.
 *
 * <p>{@code profileImageKey} is the S3 object key of the organization's logo, or {@code null}
 * when no image has been uploaded. It is a stable storage key such as
 * {@code organizations/<cognito-sub>/profile/<uuid>.png} — never image bytes, and never a
 * pre-signed URL, which would expire. It is internal to the backend and is not exposed by the API.
 */
public record Organization(
        String organizationId,
        String name,
        String description,
        String email,
        String website,
        String profileImageKey
) {

    /** Creates an organization with no profile image. */
    public Organization(
            String organizationId,
            String name,
            String description,
            String email,
            String website) {

        this(organizationId, name, description, email, website, null);
    }
}
