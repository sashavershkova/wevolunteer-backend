package com.wevolunteer.backend.controller;

import com.wevolunteer.backend.dto.AttachProfileImageRequest;
import com.wevolunteer.backend.dto.OrganizationProfileResponse;
import com.wevolunteer.backend.dto.ProfileImageUploadUrlRequest;
import com.wevolunteer.backend.dto.ProfileImageUploadUrlResponse;
import com.wevolunteer.backend.dto.UserProfileResponse;
import com.wevolunteer.backend.service.ProfileImageService;
import com.wevolunteer.backend.service.ProfileResponseMapper;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Profile image endpoints for volunteers and organizations.
 *
 * <p>Parallel routes rather than one role-aware route, matching the existing split between
 * {@code /users/me} and {@code /organizations/me}.
 */
@RestController
public class ProfileImageController {

    private final ProfileImageService profileImageService;
    private final ProfileResponseMapper profileResponseMapper;

    public ProfileImageController(
            ProfileImageService profileImageService,
            ProfileResponseMapper profileResponseMapper) {

        this.profileImageService = profileImageService;
        this.profileResponseMapper = profileResponseMapper;
    }

    @PostMapping("/users/me/profile-image/upload-url")
    public ProfileImageUploadUrlResponse createUserProfileImageUploadUrl(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProfileImageUploadUrlRequest request) {

        return profileImageService.createUserUploadUrl(
                jwt.getSubject(), request.contentType());
    }

    @PatchMapping("/users/me/profile-image")
    public UserProfileResponse attachUserProfileImage(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AttachProfileImageRequest request) {

        return profileResponseMapper.toResponse(
                profileImageService.attachUserImage(jwt.getSubject(), request.objectKey()));
    }

    @PostMapping("/organizations/me/profile-image/upload-url")
    public ProfileImageUploadUrlResponse createOrganizationProfileImageUploadUrl(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProfileImageUploadUrlRequest request) {

        return profileImageService.createOrganizationUploadUrl(
                jwt.getSubject(), request.contentType());
    }

    @PatchMapping("/organizations/me/profile-image")
    public OrganizationProfileResponse attachOrganizationProfileImage(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AttachProfileImageRequest request) {

        return profileResponseMapper.toResponse(
                profileImageService.attachOrganizationImage(
                        jwt.getSubject(), request.objectKey()));
    }
}
