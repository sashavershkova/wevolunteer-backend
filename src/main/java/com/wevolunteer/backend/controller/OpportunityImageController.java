package com.wevolunteer.backend.controller;

import com.wevolunteer.backend.dto.AttachOpportunityImageRequest;
import com.wevolunteer.backend.dto.OpportunityImageUploadUrlRequest;
import com.wevolunteer.backend.dto.OpportunityImageUploadUrlResponse;
import com.wevolunteer.backend.model.Opportunity;
import com.wevolunteer.backend.service.OpportunityImageService;
import com.wevolunteer.backend.service.OrganizationService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints for uploading opportunity images to S3.
 *
 * <p>Kept separate from {@link OrganizationController} so image handling has one home as more
 * operations are added, in the same way favorites live in their own controller.
 */
@RestController
public class OpportunityImageController {

    private final OpportunityImageService opportunityImageService;
    private final OrganizationService organizationService;

    public OpportunityImageController(
            OpportunityImageService opportunityImageService,
            OrganizationService organizationService) {

        this.opportunityImageService = opportunityImageService;
        this.organizationService = organizationService;
    }

    /**
     * Issues a short-lived pre-signed URL for uploading one opportunity image.
     *
     * <p>The organization is taken from the access token, never from the request body, so the
     * generated key always falls inside the caller's own prefix.
     */
    @PostMapping("/organizations/me/opportunity-images/upload-url")
    public OpportunityImageUploadUrlResponse createOpportunityImageUploadUrl(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody OpportunityImageUploadUrlRequest request) {

        String organizationId = jwt.getSubject();

        // Fails with 404 unless the caller really has an organization profile, so a volunteer
        // cannot obtain upload URLs under an organization prefix.
        organizationService.getById(organizationId);

        return opportunityImageService.createUploadUrl(
                organizationId,
                request.contentType()
        );
    }

    /**
     * Attaches an already-uploaded image to one of the caller's opportunities.
     *
     * <p>Separate from the upload-URL call on purpose: issuing a URL does not prove the browser
     * finished uploading, so nothing is persisted until the client confirms with this request.
     * Also used to replace an existing image.
     */
    @PatchMapping("/organizations/me/opportunities/{opportunityId}/image")
    public Opportunity attachOpportunityImage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String opportunityId,
            @Valid @RequestBody AttachOpportunityImageRequest request) {

        return opportunityImageService.attachImage(
                jwt.getSubject(),
                opportunityId,
                request.objectKey()
        );
    }
}
