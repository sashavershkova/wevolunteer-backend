package com.wevolunteer.backend.controller;

import com.wevolunteer.backend.model.Waitlist;
import com.wevolunteer.backend.service.WaitlistService;
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
@DisplayName("WaitlistController")
class WaitlistControllerTest {

    private static final String USER_ID = "user-1";
    private static final String OPPORTUNITY_ID = "opp-1";

    @Mock
    private WaitlistService waitlistService;

    @Mock
    private Jwt jwt;

    @InjectMocks
    private WaitlistController waitlistController;

    @Test
    @DisplayName("getMyWaitlist resolves the user from the JWT subject and delegates to the service")
    void getMyWaitlistResolvesUserFromJwtSubject() {
        when(jwt.getSubject()).thenReturn(USER_ID);
        List<Waitlist> expected = List.of(waitlist());
        when(waitlistService.getWaitlistByUserId(USER_ID)).thenReturn(expected);

        List<Waitlist> result = waitlistController.getMyWaitlist(jwt);

        assertThat(result).isSameAs(expected);
    }

    @Test
    @DisplayName("joinMyWaitlist resolves the user from the JWT subject and never accepts a client-supplied user id")
    void joinMyWaitlistResolvesUserFromJwtSubject() {
        when(jwt.getSubject()).thenReturn(USER_ID);

        waitlistController.joinMyWaitlist(jwt, OPPORTUNITY_ID);

        verify(waitlistService).join(USER_ID, OPPORTUNITY_ID);
        verifyNoMoreInteractions(waitlistService);
    }

    @Test
    @DisplayName("leaveMyWaitlist resolves the user from the JWT subject and delegates to the service")
    void leaveMyWaitlistResolvesUserFromJwtSubject() {
        when(jwt.getSubject()).thenReturn(USER_ID);

        waitlistController.leaveMyWaitlist(jwt, OPPORTUNITY_ID);

        verify(waitlistService).leave(USER_ID, OPPORTUNITY_ID);
        verifyNoMoreInteractions(waitlistService);
    }

    private static Waitlist waitlist() {
        return new Waitlist(
                USER_ID,
                OPPORTUNITY_ID,
                "Beach Cleanup",
                "2026-08-01",
                "Seattle, WA",
                "org-1",
                "Green Earth",
                "Chelsea Pham",
                "chelsea@example.com",
                "2026-07-29T10:00:00");
    }
}
