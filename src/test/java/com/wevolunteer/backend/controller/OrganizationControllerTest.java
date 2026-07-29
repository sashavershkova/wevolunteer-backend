package com.wevolunteer.backend.controller;

import com.wevolunteer.backend.dto.CreateOpportunityRequest;
import com.wevolunteer.backend.dto.UpdateOrganizationRequest;
import com.wevolunteer.backend.model.Opportunity;
import com.wevolunteer.backend.model.Organization;
import com.wevolunteer.backend.service.OpportunityService;
import com.wevolunteer.backend.service.OrganizationService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrganizationController")
class OrganizationControllerTest {

    private static final String ORGANIZATION_ID = "org-1";

    @Mock
    private OrganizationService organizationService;

    @Mock
    private OpportunityService opportunityService;

    @Mock
    private Jwt jwt;

    @InjectMocks
    private OrganizationController organizationController;

    @Test
    @DisplayName("getMyOrganizationOpportunities resolves the organization from the JWT subject and delegates to the service")
    void resolvesOrganizationFromJwtSubject() {
        when(jwt.getSubject()).thenReturn(ORGANIZATION_ID);
        List<Opportunity> expected = List.of(opportunity());
        when(opportunityService.getAllOpportunitiesByOrganizationId(ORGANIZATION_ID)).thenReturn(expected);

        List<Opportunity> result = organizationController.getMyOrganizationOpportunities(jwt);

        assertThat(result).isSameAs(expected);
    }

    @Test
    @DisplayName("createOpportunity uses the JWT subject as the organization ID, loads the organization by it, and passes the organization name to OpportunityService")
    void createOpportunityUsesJwtSubjectAsOrganizationId() {
        when(jwt.getSubject()).thenReturn(ORGANIZATION_ID);
        Organization organization = new Organization(
                ORGANIZATION_ID, "Green Earth", "desc", "org@example.com", "https://example.com");
        when(organizationService.getById(ORGANIZATION_ID)).thenReturn(organization);

        CreateOpportunityRequest request = new CreateOpportunityRequest(
                "opp-1", "Beach Cleanup", "Help clean the beach",
                "Environment", "Seattle, WA", "2026-08-01", 10, "09:00", "12:00", List.of("Sort and organize donations", "Help set up the distribution area"), false);

        Opportunity expected = opportunity();
        when(opportunityService.createOpportunity(ORGANIZATION_ID, "Green Earth", request))
                .thenReturn(expected);

        Opportunity result = organizationController.createOpportunity(jwt, request);

        verify(organizationService).getById(ORGANIZATION_ID);
        verify(opportunityService).createOpportunity(ORGANIZATION_ID, "Green Earth", request);
        assertThat(result).isSameAs(expected);
    }

    @Test
    @DisplayName("the old path-variable createOpportunity endpoint no longer exists")
    void oldPathVariableEndpointNoLongerExists() {
        assertThatThrownBy(() -> OrganizationController.class.getMethod(
                "createOpportunity", String.class, CreateOpportunityRequest.class))
                .isInstanceOf(NoSuchMethodException.class);
    }

    @Test
    @DisplayName("createOpportunity is mapped to POST /organizations/me/opportunities")
    void createOpportunityIsMappedToMeRoute() throws NoSuchMethodException {
        Method method = OrganizationController.class.getMethod(
                "createOpportunity", Jwt.class, CreateOpportunityRequest.class);
        org.springframework.web.bind.annotation.PostMapping mapping =
                method.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class);

        assertThat(mapping.value()).containsExactly("/organizations/me/opportunities");
    }

    @Test
    @DisplayName("updateCurrentOrganization uses the JWT subject as the organization ID and delegates to the service")
    void updateCurrentOrganizationUsesJwtSubjectAsOrganizationId() {
        when(jwt.getSubject()).thenReturn(ORGANIZATION_ID);
        UpdateOrganizationRequest request = new UpdateOrganizationRequest(
                "New Name", "New description", "new@example.com", "https://new.example.com");
        Organization updated = new Organization(
                ORGANIZATION_ID, "New Name", "New description", "new@example.com",
                "https://new.example.com");
        when(organizationService.updateOrganization(ORGANIZATION_ID, request)).thenReturn(updated);

        var result = organizationController.updateCurrentOrganization(jwt, request);

        verify(organizationService).updateOrganization(ORGANIZATION_ID, request);
        assertThat(result.organizationId()).isEqualTo(ORGANIZATION_ID);
        assertThat(result.name()).isEqualTo("New Name");
        assertThat(result.email()).isEqualTo("new@example.com");
    }

    @Test
    @DisplayName("updateCurrentOrganization is mapped to PATCH /organizations/me and has no path variable for organization ID")
    void updateCurrentOrganizationIsMappedToMeRouteWithNoIdParameter() throws NoSuchMethodException {
        Method method = OrganizationController.class.getMethod(
                "updateCurrentOrganization", Jwt.class, UpdateOrganizationRequest.class);
        org.springframework.web.bind.annotation.PatchMapping mapping =
                method.getAnnotation(org.springframework.web.bind.annotation.PatchMapping.class);

        assertThat(mapping.value()).containsExactly("/organizations/me");
        assertThat(method.getParameterTypes()).containsExactly(Jwt.class, UpdateOrganizationRequest.class);
    }

    @Test
    @DisplayName("updateCurrentOrganization has no overload accepting a client-supplied organization ID")
    void updateCurrentOrganizationCannotChooseADifferentOrganizationId() {
        assertThatThrownBy(() -> OrganizationController.class.getMethod(
                "updateCurrentOrganization", String.class, UpdateOrganizationRequest.class))
                .isInstanceOf(NoSuchMethodException.class);
    }

    @Test
    @DisplayName("UpdateOrganizationRequest rejects a blank name, which Spring maps to 400 Bad Request")
    void blankNameFailsValidation() {
        UpdateOrganizationRequest request = new UpdateOrganizationRequest(
                " ", "description", "valid@example.com", "https://example.com");

        Set<ConstraintViolation<UpdateOrganizationRequest>> violations = validator().validate(request);

        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("name"));
    }

    @Test
    @DisplayName("UpdateOrganizationRequest rejects an invalid email, which Spring maps to 400 Bad Request")
    void invalidEmailFailsValidation() {
        UpdateOrganizationRequest request = new UpdateOrganizationRequest(
                "Green Earth", "description", "not-an-email", "https://example.com");

        Set<ConstraintViolation<UpdateOrganizationRequest>> violations = validator().validate(request);

        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    private static Validator validator() {
        return Validation.buildDefaultValidatorFactory().getValidator();
    }

    private static Opportunity opportunity() {
        return new Opportunity(
                "opp-1",
                "Beach Cleanup",
                "Help clean the beach",
                "Environment",
                "Seattle, WA",
                "2026-08-01",
                "OPEN",
                ORGANIZATION_ID,
                "Green Earth",
                10,
                0,
                10,
                null, "09:00", "12:00", List.of("Sort and organize donations", "Help set up the distribution area"), false);
    }
}
