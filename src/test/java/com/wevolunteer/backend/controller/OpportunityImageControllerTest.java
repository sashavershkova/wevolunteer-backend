package com.wevolunteer.backend.controller;

import com.wevolunteer.backend.dto.AttachOpportunityImageRequest;
import com.wevolunteer.backend.dto.OpportunityImageUploadUrlRequest;
import com.wevolunteer.backend.dto.OpportunityImageUploadUrlResponse;
import com.wevolunteer.backend.exception.NotFoundException;
import com.wevolunteer.backend.model.Opportunity;
import com.wevolunteer.backend.service.OpportunityImageService;
import com.wevolunteer.backend.service.OrganizationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OpportunityImageController")
class OpportunityImageControllerTest {

    private static final String ORGANIZATION_ID = "org-1";
    private static final String UPLOAD_KEY =
            "organizations/org-1/opportunities/550e8400-e29b-41d4-a716-446655440000.jpg";

    @Mock
    private OpportunityImageService opportunityImageService;

    @Mock
    private OrganizationService organizationService;

    @Mock
    private Jwt jwt;

    @InjectMocks
    private OpportunityImageController opportunityImageController;

    @Test
    @DisplayName("passes the JWT subject as the organization, never a value from the body")
    void usesJwtSubjectAsOrganization() {
        when(jwt.getSubject()).thenReturn(ORGANIZATION_ID);
        OpportunityImageUploadUrlResponse expected =
                new OpportunityImageUploadUrlResponse(UPLOAD_KEY, "https://signed", 900L);
        when(opportunityImageService.createUploadUrl(ORGANIZATION_ID, "image/jpeg"))
                .thenReturn(expected);

        OpportunityImageUploadUrlResponse result =
                opportunityImageController.createOpportunityImageUploadUrl(
                        jwt, new OpportunityImageUploadUrlRequest("image/jpeg"));

        assertThat(result).isSameAs(expected);
        verify(opportunityImageService).createUploadUrl(ORGANIZATION_ID, "image/jpeg");
    }

    @Test
    @DisplayName("confirms the caller has an organization profile before issuing a URL")
    void verifiesOrganizationExistsFirst() {
        when(jwt.getSubject()).thenReturn(ORGANIZATION_ID);
        when(opportunityImageService.createUploadUrl(anyString(), anyString()))
                .thenReturn(new OpportunityImageUploadUrlResponse(
                        UPLOAD_KEY, "https://signed", 900L));

        opportunityImageController.createOpportunityImageUploadUrl(
                jwt, new OpportunityImageUploadUrlRequest("image/jpeg"));

        InOrder inOrder = inOrder(organizationService, opportunityImageService);
        inOrder.verify(organizationService).getById(ORGANIZATION_ID);
        inOrder.verify(opportunityImageService).createUploadUrl(ORGANIZATION_ID, "image/jpeg");
    }

    @Test
    @DisplayName("does not issue a URL when the caller has no organization profile")
    void doesNotIssueUrlForNonOrganization() {
        when(jwt.getSubject()).thenReturn(ORGANIZATION_ID);
        when(organizationService.getById(ORGANIZATION_ID))
                .thenThrow(new NotFoundException("Organization not found: " + ORGANIZATION_ID));

        assertThatThrownBy(() -> opportunityImageController.createOpportunityImageUploadUrl(
                jwt, new OpportunityImageUploadUrlRequest("image/jpeg")))
                .isInstanceOf(NotFoundException.class);

        verifyNoInteractions(opportunityImageService);
    }

    @Test
    @DisplayName("is mapped to POST /organizations/me/opportunity-images/upload-url")
    void isMappedToMeRoute() throws NoSuchMethodException {
        Method method = OpportunityImageController.class.getMethod(
                "createOpportunityImageUploadUrl", Jwt.class,
                OpportunityImageUploadUrlRequest.class);

        PostMapping mapping = method.getAnnotation(PostMapping.class);

        assertThat(mapping.value())
                .containsExactly("/organizations/me/opportunity-images/upload-url");
    }

    @Test
    @DisplayName("attachOpportunityImage takes the organization from the JWT, not the request")
    void attachUsesJwtSubject() {
        when(jwt.getSubject()).thenReturn(ORGANIZATION_ID);
        Opportunity expected = opportunity(UPLOAD_KEY);
        when(opportunityImageService.attachImage(ORGANIZATION_ID, "opp-1", UPLOAD_KEY))
                .thenReturn(expected);

        Opportunity result = opportunityImageController.attachOpportunityImage(
                jwt, "opp-1", new AttachOpportunityImageRequest(UPLOAD_KEY));

        assertThat(result).isSameAs(expected);
        verify(opportunityImageService).attachImage(ORGANIZATION_ID, "opp-1", UPLOAD_KEY);
    }

    @Test
    @DisplayName("attachOpportunityImage is mapped to PATCH /organizations/me/opportunities/{opportunityId}/image")
    void attachIsMappedToMeRoute() throws NoSuchMethodException {
        Method method = OpportunityImageController.class.getMethod(
                "attachOpportunityImage", Jwt.class, String.class,
                AttachOpportunityImageRequest.class);

        PatchMapping mapping = method.getAnnotation(PatchMapping.class);

        assertThat(mapping.value())
                .containsExactly("/organizations/me/opportunities/{opportunityId}/image");
    }

    private static Opportunity opportunity(String imageKey) {
        return new Opportunity(
                "opp-1", "Beach Cleanup", "Pick up litter", "ENVIRONMENT",
                "Seattle, WA", "2026-08-01", "OPEN", ORGANIZATION_ID, "Green Earth",
                10, 3, 7, null, "09:00", "13:00",
                List.of("Sort donations"), false, imageKey);
    }
}
