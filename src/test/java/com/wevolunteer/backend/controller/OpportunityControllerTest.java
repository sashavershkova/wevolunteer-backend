package com.wevolunteer.backend.controller;

import com.wevolunteer.backend.dto.OpportunityResponse;
import com.wevolunteer.backend.model.Opportunity;
import com.wevolunteer.backend.service.OpportunityResponseMapper;
import com.wevolunteer.backend.service.OpportunityService;
import com.wevolunteer.backend.service.RegistrationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OpportunityController")
class OpportunityControllerTest {

    private static final String OPPORTUNITY_ID = "opp-1";
    private static final String ORG_ID = "org-1";
    private static final String ORG_NAME = "Green Earth";

    @Mock
    private OpportunityService opportunityService;

    @Mock
    private OpportunityResponseMapper opportunityResponseMapper;

    @Mock
    private RegistrationService registrationService;

    @Mock
    private Jwt jwt;

    @InjectMocks
    private OpportunityController opportunityController;

    @Test
    @DisplayName("closeOpportunity resolves the organization from the JWT subject and delegates to the service")
    void resolvesOrganizationFromJwtSubject() {
        when(jwt.getSubject()).thenReturn(ORG_ID);
        Opportunity closed = opportunity("CLOSED");
        when(opportunityService.closeOpportunity(OPPORTUNITY_ID, ORG_ID)).thenReturn(closed);

        OpportunityResponse mapped = OpportunityResponse.from(closed, null);
        when(opportunityResponseMapper.toResponse(closed)).thenReturn(mapped);

        OpportunityResponse result = opportunityController.closeOpportunity(jwt, OPPORTUNITY_ID);

        assertThat(result).isSameAs(mapped);
    }

    @Test
    @DisplayName("closeOpportunity does not call the service with any other organization id")
    void doesNotSubstituteAnotherOrganizationId() {
        when(jwt.getSubject()).thenReturn(ORG_ID);
        when(opportunityService.closeOpportunity(OPPORTUNITY_ID, ORG_ID))
                .thenReturn(opportunity("CLOSED"));

        opportunityController.closeOpportunity(jwt, OPPORTUNITY_ID);

        verify(opportunityService).closeOpportunity(OPPORTUNITY_ID, ORG_ID);
        verifyNoMoreInteractions(opportunityService);
    }

    private static Opportunity opportunity(String status) {
        return new Opportunity(
                OPPORTUNITY_ID,
                "Beach Cleanup",
                "Pick up litter",
                "ENVIRONMENT",
                "Seattle, WA",
                "2026-08-01",
                status,
                ORG_ID,
                ORG_NAME,
                10,
                3,
                7,
                null, "09:00", "12:00", List.of("Sort and organize donations", "Help set up the distribution area"), false);
    }
}
