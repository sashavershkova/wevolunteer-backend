package com.wevolunteer.backend.controller;

import com.wevolunteer.backend.dto.AttachProfileImageRequest;
import com.wevolunteer.backend.dto.OrganizationProfileResponse;
import com.wevolunteer.backend.dto.ProfileImageUploadUrlRequest;
import com.wevolunteer.backend.dto.ProfileImageUploadUrlResponse;
import com.wevolunteer.backend.dto.UserProfileResponse;
import com.wevolunteer.backend.model.Organization;
import com.wevolunteer.backend.model.User;
import com.wevolunteer.backend.service.ProfileImageService;
import com.wevolunteer.backend.service.ProfileResponseMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProfileImageController")
class ProfileImageControllerTest {

    private static final String SUB = "sub-1";
    private static final String USER_KEY = "users/sub-1/profile/abc.jpg";
    private static final String ORG_KEY = "organizations/sub-1/profile/abc.png";

    @Mock
    private ProfileImageService profileImageService;

    @Mock
    private ProfileResponseMapper profileResponseMapper;

    @Mock
    private Jwt jwt;

    @InjectMocks
    private ProfileImageController profileImageController;

    @Test
    @DisplayName("volunteer upload URL is requested for the JWT subject")
    void userUploadUrlUsesJwtSubject() {
        when(jwt.getSubject()).thenReturn(SUB);
        ProfileImageUploadUrlResponse expected =
                new ProfileImageUploadUrlResponse(USER_KEY, "https://signed", 900L);
        when(profileImageService.createUserUploadUrl(SUB, "image/jpeg")).thenReturn(expected);

        ProfileImageUploadUrlResponse result =
                profileImageController.createUserProfileImageUploadUrl(
                        jwt, new ProfileImageUploadUrlRequest("image/jpeg"));

        assertThat(result).isSameAs(expected);
        verify(profileImageService).createUserUploadUrl(SUB, "image/jpeg");
    }

    @Test
    @DisplayName("organization upload URL is requested for the JWT subject")
    void organizationUploadUrlUsesJwtSubject() {
        when(jwt.getSubject()).thenReturn(SUB);
        ProfileImageUploadUrlResponse expected =
                new ProfileImageUploadUrlResponse(ORG_KEY, "https://signed", 900L);
        when(profileImageService.createOrganizationUploadUrl(SUB, "image/png"))
                .thenReturn(expected);

        ProfileImageUploadUrlResponse result =
                profileImageController.createOrganizationProfileImageUploadUrl(
                        jwt, new ProfileImageUploadUrlRequest("image/png"));

        assertThat(result).isSameAs(expected);
    }

    @Test
    @DisplayName("attaching a volunteer image uses the JWT subject, never a value from the body")
    void attachUserUsesJwtSubject() {
        when(jwt.getSubject()).thenReturn(SUB);
        User updated = new User(SUB, "Chelsea", "c@example.com", "VOLUNTEER", USER_KEY);
        UserProfileResponse mapped = UserProfileResponse.from(updated, "https://signed");
        when(profileImageService.attachUserImage(SUB, USER_KEY)).thenReturn(updated);
        when(profileResponseMapper.toResponse(updated)).thenReturn(mapped);

        UserProfileResponse result = profileImageController.attachUserProfileImage(
                jwt, new AttachProfileImageRequest(USER_KEY));

        assertThat(result).isSameAs(mapped);
        verify(profileImageService).attachUserImage(SUB, USER_KEY);
    }

    @Test
    @DisplayName("attaching an organization image uses the JWT subject")
    void attachOrganizationUsesJwtSubject() {
        when(jwt.getSubject()).thenReturn(SUB);
        Organization updated = new Organization(
                SUB, "Green Bean", "We grow trees", "info@greenbean.org",
                "https://greenbean.org", ORG_KEY);
        OrganizationProfileResponse mapped =
                OrganizationProfileResponse.from(updated, "https://signed");
        when(profileImageService.attachOrganizationImage(SUB, ORG_KEY)).thenReturn(updated);
        when(profileResponseMapper.toResponse(updated)).thenReturn(mapped);

        OrganizationProfileResponse result =
                profileImageController.attachOrganizationProfileImage(
                        jwt, new AttachProfileImageRequest(ORG_KEY));

        assertThat(result).isSameAs(mapped);
    }

    @Test
    @DisplayName("routes are mapped as documented")
    void routesAreMapped() throws NoSuchMethodException {
        Method userUpload = ProfileImageController.class.getMethod(
                "createUserProfileImageUploadUrl", Jwt.class, ProfileImageUploadUrlRequest.class);
        Method orgUpload = ProfileImageController.class.getMethod(
                "createOrganizationProfileImageUploadUrl", Jwt.class,
                ProfileImageUploadUrlRequest.class);
        Method userAttach = ProfileImageController.class.getMethod(
                "attachUserProfileImage", Jwt.class, AttachProfileImageRequest.class);
        Method orgAttach = ProfileImageController.class.getMethod(
                "attachOrganizationProfileImage", Jwt.class, AttachProfileImageRequest.class);

        assertThat(userUpload.getAnnotation(PostMapping.class).value())
                .containsExactly("/users/me/profile-image/upload-url");
        assertThat(orgUpload.getAnnotation(PostMapping.class).value())
                .containsExactly("/organizations/me/profile-image/upload-url");
        assertThat(userAttach.getAnnotation(PatchMapping.class).value())
                .containsExactly("/users/me/profile-image");
        assertThat(orgAttach.getAnnotation(PatchMapping.class).value())
                .containsExactly("/organizations/me/profile-image");
    }
}
