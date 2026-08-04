package com.wevolunteer.backend.service;

import com.wevolunteer.backend.exception.ConflictException;
import com.wevolunteer.backend.exception.NotFoundException;
import com.wevolunteer.backend.model.Opportunity;
import com.wevolunteer.backend.model.User;
import com.wevolunteer.backend.model.Waitlist;
import com.wevolunteer.backend.repository.OpportunityRepository;
import com.wevolunteer.backend.repository.UserRepository;
import com.wevolunteer.backend.repository.WaitlistRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WaitlistService")
class WaitlistServiceTest {

    private static final String USER_ID = "user-1";
    private static final String OPPORTUNITY_ID = "opp-1";
    private static final String ORG_ID = "org-1";
    private static final String ORG_NAME = "Green Earth";

    @Mock
    private WaitlistRepository waitlistRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OpportunityRepository opportunityRepository;

    @InjectMocks
    private WaitlistService waitlistService;

    @Nested
    @DisplayName("join")
    class Join {

        @Test
        @DisplayName("joins the waitlist when the opportunity is open and full")
        void joinsWhenOpenAndFull() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity("OPEN", 10, 10)));

            waitlistService.join(USER_ID, OPPORTUNITY_ID);

            verify(waitlistRepository).joinWaitlist(
                    USER_ID,
                    "Chelsea Pham",
                    "chelsea@example.com",
                    OPPORTUNITY_ID,
                    "Beach Cleanup",
                    "2026-08-01",
                    "Seattle, WA",
                    ORG_ID,
                    ORG_NAME);
        }

        @Test
        @DisplayName("throws ConflictException and never joins when the opportunity still has open spots")
        void rejectsWhenSpotsAvailable() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity("OPEN", 10, 3)));

            assertThatThrownBy(() -> waitlistService.join(USER_ID, OPPORTUNITY_ID))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("register instead");

            verifyNoInteractions(waitlistRepository);
        }

        @Test
        @DisplayName("throws ConflictException and never joins when the opportunity is closed")
        void rejectsWhenClosed() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity("CLOSED", 10, 10)));

            assertThatThrownBy(() -> waitlistService.join(USER_ID, OPPORTUNITY_ID))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("not open");

            verifyNoInteractions(waitlistRepository);
        }

        @Test
        @DisplayName("throws NotFoundException and never looks up the opportunity when the user is absent")
        void throwsWhenUserMissing() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> waitlistService.join(USER_ID, OPPORTUNITY_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("User not found: " + USER_ID);

            verifyNoInteractions(opportunityRepository, waitlistRepository);
        }

        @Test
        @DisplayName("throws NotFoundException when the opportunity is absent")
        void throwsWhenOpportunityMissing() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
            when(opportunityRepository.findById(OPPORTUNITY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> waitlistService.join(USER_ID, OPPORTUNITY_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Opportunity not found: " + OPPORTUNITY_ID);

            verifyNoInteractions(waitlistRepository);
        }
    }

    @Nested
    @DisplayName("leave")
    class Leave {

        @Test
        @DisplayName("looks up the opportunity's date and delegates to the repository")
        void delegatesToRepository() {
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity("OPEN", 10, 10)));

            waitlistService.leave(USER_ID, OPPORTUNITY_ID);

            verify(waitlistRepository).leaveWaitlist(USER_ID, OPPORTUNITY_ID, "2026-08-01");
        }

        @Test
        @DisplayName("throws NotFoundException when the opportunity is absent")
        void throwsWhenOpportunityMissing() {
            when(opportunityRepository.findById(OPPORTUNITY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> waitlistService.leave(USER_ID, OPPORTUNITY_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Opportunity not found: " + OPPORTUNITY_ID);

            verifyNoInteractions(waitlistRepository);
        }
    }

    @Nested
    @DisplayName("getWaitlistByUserId")
    class GetWaitlistByUserId {

        @Test
        @DisplayName("returns whatever the repository provides")
        void returnsRepositoryResult() {
            Waitlist entry = new Waitlist(
                    USER_ID, OPPORTUNITY_ID, "Beach Cleanup", "2026-08-01", "Seattle, WA",
                    ORG_ID, ORG_NAME, "Chelsea Pham", "chelsea@example.com", "2026-07-01T10:00:00");
            when(waitlistRepository.findByUserId(USER_ID)).thenReturn(List.of(entry));

            List<Waitlist> result = waitlistService.getWaitlistByUserId(USER_ID);

            assertThat(result).containsExactly(entry);
        }
    }

    private static User user() {
        return new User(USER_ID, "Chelsea Pham", "chelsea@example.com", "VOLUNTEER");
    }

    private static Opportunity opportunity(String status, int capacity, int registeredCount) {
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
                capacity,
                registeredCount,
                capacity - registeredCount,
                null, "09:00", "12:00",
                List.of("Sort and organize donations", "Help set up the distribution area"),
                false);
    }
}
