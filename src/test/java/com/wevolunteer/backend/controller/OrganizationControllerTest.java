package com.wevolunteer.backend.controller;

import com.wevolunteer.backend.dto.CreateOpportunityRequest;
import com.wevolunteer.backend.model.Opportunity;
import com.wevolunteer.backend.model.Organization;
import com.wevolunteer.backend.service.OpportunityService;
import com.wevolunteer.backend.service.OrganizationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.lang.reflect.Method;
import java.util.List;

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
                "Environment", "Seattle, WA", "2026-08-01", 10);

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
                10);
    }
}
