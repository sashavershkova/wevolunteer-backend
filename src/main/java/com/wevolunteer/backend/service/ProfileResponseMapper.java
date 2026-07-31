package com.wevolunteer.backend.service;

import com.wevolunteer.backend.dto.OrganizationProfileResponse;
import com.wevolunteer.backend.dto.UserProfileResponse;
import com.wevolunteer.backend.model.Organization;
import com.wevolunteer.backend.model.User;
import org.springframework.stereotype.Component;

/**
 * Turns profiles into API responses, resolving a temporary display URL for any that have an image.
 *
 * <p>Kept out of the controllers so the pre-signing rule lives in one place, and out of the
 * services so internal reads do not pay to sign URLs nobody displays.
 */
@Component
public class ProfileResponseMapper {

    private final ProfileImageService profileImageService;

    public ProfileResponseMapper(ProfileImageService profileImageService) {
        this.profileImageService = profileImageService;
    }

    public UserProfileResponse toResponse(User user) {
        return UserProfileResponse.from(
                user,
                profileImageService.resolveImageUrl(user.profileImageKey())
        );
    }

    public OrganizationProfileResponse toResponse(Organization organization) {
        return OrganizationProfileResponse.from(
                organization,
                profileImageService.resolveImageUrl(organization.profileImageKey())
        );
    }
}
