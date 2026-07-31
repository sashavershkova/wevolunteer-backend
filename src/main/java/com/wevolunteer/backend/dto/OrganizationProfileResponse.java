package com.wevolunteer.backend.dto;

import com.wevolunteer.backend.model.Organization;

/**
 * API representation of an organization profile.
 *
 * <p>Deliberately omits {@code profileImageKey}, for the same reasons as
 * {@link UserProfileResponse}.
 */
public record OrganizationProfileResponse(
        String organizationId,
        String name,
        String description,
        String email,
        String website,
        String profileImageUrl
) {

    /** For contexts with no display URL, such as tests and internal mapping. */
    public static OrganizationProfileResponse from(Organization organization) {
        return from(organization, null);
    }

    public static OrganizationProfileResponse from(
            Organization organization, String profileImageUrl) {

        return new OrganizationProfileResponse(
                organization.organizationId(),
                organization.name(),
                organization.description(),
                organization.email(),
                organization.website(),
                profileImageUrl
        );
    }
}
