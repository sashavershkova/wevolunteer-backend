package com.wevolunteer.backend.service;

import com.wevolunteer.backend.dto.OrganizationProfileResponse;
import com.wevolunteer.backend.dto.UserProfileResponse;
import com.wevolunteer.backend.model.Organization;
import com.wevolunteer.backend.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProfileResponseMapper")
class ProfileResponseMapperTest {

    private static final String USER_KEY = "users/user-1/profile/abc.jpg";
    private static final String ORG_KEY = "organizations/org-1/profile/abc.png";
    private static final String SIGNED_URL = "https://bucket.s3.amazonaws.com/signed";

    @Mock
    private ProfileImageService profileImageService;

    @InjectMocks
    private ProfileResponseMapper mapper;

    @Test
    @DisplayName("adds a display URL to a volunteer profile that has an image")
    void addsUrlToUser() {
        when(profileImageService.resolveImageUrl(USER_KEY)).thenReturn(SIGNED_URL);

        UserProfileResponse response = mapper.toResponse(
                new User("user-1", "Chelsea", "c@example.com", "VOLUNTEER", USER_KEY));

        assertThat(response.profileImageUrl()).isEqualTo(SIGNED_URL);
        assertThat(response.name()).isEqualTo("Chelsea");
    }

    @Test
    @DisplayName("leaves the URL null for a volunteer with no image")
    void nullUrlForUserWithoutImage() {
        when(profileImageService.resolveImageUrl(null)).thenReturn(null);

        UserProfileResponse response = mapper.toResponse(
                new User("user-1", "Chelsea", "c@example.com", "VOLUNTEER"));

        assertThat(response.profileImageUrl()).isNull();
    }

    @Test
    @DisplayName("adds a display URL to an organization profile that has an image")
    void addsUrlToOrganization() {
        when(profileImageService.resolveImageUrl(ORG_KEY)).thenReturn(SIGNED_URL);

        OrganizationProfileResponse response = mapper.toResponse(new Organization(
                "org-1", "Green Bean", "We grow trees",
                "info@greenbean.org", "https://greenbean.org", ORG_KEY));

        assertThat(response.profileImageUrl()).isEqualTo(SIGNED_URL);
        assertThat(response.name()).isEqualTo("Green Bean");
    }

    @Test
    @DisplayName("leaves the URL null for an organization with no image")
    void nullUrlForOrganizationWithoutImage() {
        when(profileImageService.resolveImageUrl(null)).thenReturn(null);

        OrganizationProfileResponse response = mapper.toResponse(new Organization(
                "org-1", "Green Bean", null, "info@greenbean.org", null));

        assertThat(response.profileImageUrl()).isNull();
    }
}
