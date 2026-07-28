package com.wevolunteer.backend.controller;

import com.wevolunteer.backend.model.Opportunity;
import com.wevolunteer.backend.service.OpportunityService;
import com.wevolunteer.backend.service.OrganizationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
