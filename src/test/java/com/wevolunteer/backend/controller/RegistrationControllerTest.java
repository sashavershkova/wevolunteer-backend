package com.wevolunteer.backend.controller;

import com.wevolunteer.backend.model.Registration;
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
@DisplayName("RegistrationController")
class RegistrationControllerTest {

    private static final String USER_ID = "user-1";
    private static final String OPPORTUNITY_ID = "opp-1";

    @Mock
    private RegistrationService registrationService;

    @Mock
    private Jwt jwt;

    @InjectMocks
    private RegistrationController registrationController;

    @Test
    @DisplayName("getMyRegistrations resolves the user from the JWT subject and delegates to the service")
    void resolvesUserFromJwtSubject() {
        when(jwt.getSubject()).thenReturn(USER_ID);
        List<Registration> expected = List.of(registration());
        when(registrationService.getRegistrationsByUserId(USER_ID)).thenReturn(expected);

        List<Registration> result = registrationController.getMyRegistrations(jwt);

        assertThat(result).isSameAs(expected);
    }

    @Test
    @DisplayName("getMyRegistrations returns an empty list when the volunteer has no registrations")
    void returnsEmptyListWhenNoRegistrations() {
        when(jwt.getSubject()).thenReturn(USER_ID);
        when(registrationService.getRegistrationsByUserId(USER_ID)).thenReturn(List.of());

        List<Registration> result = registrationController.getMyRegistrations(jwt);

        assertThat(result).isEmpty();
        verifyNoMoreInteractions(registrationService);
    }

    @Test
    @DisplayName("cancelMyRegistration resolves the user from the JWT subject and delegates to the service")
    void cancellationUsesJwtSubject() {
        when(jwt.getSubject()).thenReturn(USER_ID);

        registrationController.cancelMyRegistration(jwt, OPPORTUNITY_ID);

        verify(registrationService).cancelRegistration(USER_ID, OPPORTUNITY_ID);
        verifyNoMoreInteractions(registrationService);
    }

    private static Registration registration() {
        return new Registration(
                USER_ID,
                OPPORTUNITY_ID,
                "Beach Cleanup",
                "2026-08-01",
                "Seattle, WA",
                "org-1",
                "Green Earth",
                "ACTIVE",
                "Chelsea Pham",
                "chelsea@example.com",
                "2026-07-24T10:00:00",
                null, null, null);
    }
}
