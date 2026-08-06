package com.wevolunteer.backend.service;

import com.wevolunteer.backend.exception.ConflictException;
import com.wevolunteer.backend.exception.ForbiddenException;
import com.wevolunteer.backend.exception.NotFoundException;
import com.wevolunteer.backend.model.Opportunity;
import com.wevolunteer.backend.model.User;
import com.wevolunteer.backend.model.Waitlist;
import com.wevolunteer.backend.notification.NotificationEvent;
import com.wevolunteer.backend.notification.NotificationEventType;
import com.wevolunteer.backend.notification.NotificationPublisher;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
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

    // A relative date, not a fixed literal: a hardcoded future date eventually becomes the past
    // and starts failing every one of these tests on the wrong grounds (the new expiration
    // check, not whatever each test actually covers).
    private static final String OPPORTUNITY_DATE = LocalDate.now().plusDays(7).toString();

    @Mock
    private WaitlistRepository waitlistRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OpportunityRepository opportunityRepository;

    @Mock
    private NotificationPublisher notificationPublisher;

    @InjectMocks
    private WaitlistService waitlistService;

    @Nested
    @DisplayName("join")
    class Join {

        @Test
        @DisplayName("joins the waitlist when the opportunity is open and full, and returns the created entry")
        void joinsWhenOpenAndFull() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity("OPEN", 10, 10)));

            Waitlist result = waitlistService.join(USER_ID, OPPORTUNITY_ID);

            ArgumentCaptor<Waitlist> captor = ArgumentCaptor.forClass(Waitlist.class);
            verify(waitlistRepository).joinWaitlist(captor.capture());

            Waitlist written = captor.getValue();
            assertThat(written.userId()).isEqualTo(USER_ID);
            assertThat(written.opportunityId()).isEqualTo(OPPORTUNITY_ID);
            assertThat(written.title()).isEqualTo("Beach Cleanup");
            assertThat(written.date()).isEqualTo(OPPORTUNITY_DATE);
            assertThat(written.location()).isEqualTo("Seattle, WA");
            assertThat(written.organizationId()).isEqualTo(ORG_ID);
            assertThat(written.organizationName()).isEqualTo(ORG_NAME);
            assertThat(written.volunteerName()).isEqualTo("Chelsea Pham");
            assertThat(written.email()).isEqualTo("chelsea@example.com");
            assertThat(written.joinedAt()).isNotNull();

            assertThat(result).isEqualTo(written);
        }

        @Test
        @DisplayName("publishes exactly one WAITLIST_JOINED event built from the current user and opportunity")
        void publishesWaitlistJoinedEvent() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity("OPEN", 10, 10)));

            Instant before = Instant.now();
            waitlistService.join(USER_ID, OPPORTUNITY_ID);
            Instant after = Instant.now();

            ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
            verify(notificationPublisher).publish(captor.capture());

            NotificationEvent event = captor.getValue();
            assertThat(event.eventType()).isEqualTo(NotificationEventType.WAITLIST_JOINED);
            assertThat(event.userId()).isEqualTo(USER_ID);
            assertThat(event.volunteerName()).isEqualTo("Chelsea Pham");
            assertThat(event.volunteerEmail()).isEqualTo("chelsea@example.com");
            assertThat(event.opportunityId()).isEqualTo(OPPORTUNITY_ID);
            assertThat(event.opportunityTitle()).isEqualTo("Beach Cleanup");
            assertThat(event.opportunityDate()).isEqualTo(OPPORTUNITY_DATE);
            assertThat(event.organizationId()).isEqualTo(ORG_ID);
            assertThat(event.organizationName()).isEqualTo(ORG_NAME);
            assertThat(event.timestamp()).isNotNull().isBetween(before, after);
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

            verifyNoInteractions(waitlistRepository, notificationPublisher);
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

            verifyNoInteractions(waitlistRepository, notificationPublisher);
        }

        @Test
        @DisplayName("joins the waitlist when the opportunity's event date is today")
        void joinsWhenDateIsToday() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(
                            opportunity("OPEN", 10, 10, LocalDate.now().toString())));

            Waitlist result = waitlistService.join(USER_ID, OPPORTUNITY_ID);

            assertThat(result).isNotNull();
            verify(waitlistRepository).joinWaitlist(any(Waitlist.class));
        }

        @Test
        @DisplayName("throws ConflictException and never joins when the opportunity's event date has passed")
        void rejectsWhenExpired() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity(
                            "OPEN", 10, 10, LocalDate.now().minusDays(1).toString())));

            assertThatThrownBy(() -> waitlistService.join(USER_ID, OPPORTUNITY_ID))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Past opportunities are not open for waitlisting.");

            verifyNoInteractions(waitlistRepository, notificationPublisher);
        }

        @Test
        @DisplayName("throws NotFoundException and never looks up the opportunity when the user is absent")
        void throwsWhenUserMissing() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> waitlistService.join(USER_ID, OPPORTUNITY_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("User not found: " + USER_ID);

            verifyNoInteractions(opportunityRepository, waitlistRepository, notificationPublisher);
        }

        @Test
        @DisplayName("throws NotFoundException when the opportunity is absent")
        void throwsWhenOpportunityMissing() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
            when(opportunityRepository.findById(OPPORTUNITY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> waitlistService.join(USER_ID, OPPORTUNITY_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Opportunity not found: " + OPPORTUNITY_ID);

            verifyNoInteractions(waitlistRepository, notificationPublisher);
        }
    }

    @Nested
    @DisplayName("leave")
    class Leave {

        @Test
        @DisplayName("looks up the opportunity's date and delegates to the repository")
        void delegatesToRepository() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity("OPEN", 10, 10)));

            waitlistService.leave(USER_ID, OPPORTUNITY_ID);

            verify(waitlistRepository).leaveWaitlist(USER_ID, OPPORTUNITY_ID, OPPORTUNITY_DATE);
        }

        @Test
        @DisplayName("publishes exactly one WAITLIST_LEFT event built from the current user and opportunity")
        void publishesWaitlistLeftEvent() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity("OPEN", 10, 10)));

            Instant before = Instant.now();
            waitlistService.leave(USER_ID, OPPORTUNITY_ID);
            Instant after = Instant.now();

            ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
            verify(notificationPublisher).publish(captor.capture());

            NotificationEvent event = captor.getValue();
            assertThat(event.eventType()).isEqualTo(NotificationEventType.WAITLIST_LEFT);
            assertThat(event.userId()).isEqualTo(USER_ID);
            assertThat(event.volunteerName()).isEqualTo("Chelsea Pham");
            assertThat(event.volunteerEmail()).isEqualTo("chelsea@example.com");
            assertThat(event.opportunityId()).isEqualTo(OPPORTUNITY_ID);
            assertThat(event.opportunityTitle()).isEqualTo("Beach Cleanup");
            assertThat(event.opportunityDate()).isEqualTo(OPPORTUNITY_DATE);
            assertThat(event.organizationId()).isEqualTo(ORG_ID);
            assertThat(event.organizationName()).isEqualTo(ORG_NAME);
            assertThat(event.timestamp()).isNotNull().isBetween(before, after);
        }

        @Test
        @DisplayName("throws NotFoundException and never looks up the opportunity when the user is absent")
        void throwsWhenUserMissing() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> waitlistService.leave(USER_ID, OPPORTUNITY_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("User not found: " + USER_ID);

            verifyNoInteractions(opportunityRepository, waitlistRepository, notificationPublisher);
        }

        @Test
        @DisplayName("throws NotFoundException when the opportunity is absent")
        void throwsWhenOpportunityMissing() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
            when(opportunityRepository.findById(OPPORTUNITY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> waitlistService.leave(USER_ID, OPPORTUNITY_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Opportunity not found: " + OPPORTUNITY_ID);

            verifyNoInteractions(waitlistRepository, notificationPublisher);
        }
    }

    @Nested
    @DisplayName("getWaitlistForOrganizationOpportunity")
    class GetWaitlistForOrganizationOpportunity {

        @Test
        @DisplayName("owner receives the waitlist for their opportunity")
        void ownerReceivesWaitlist() {
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity("OPEN", 10, 10)));
            List<Waitlist> expected = List.of(new Waitlist(
                    USER_ID, OPPORTUNITY_ID, "Beach Cleanup", OPPORTUNITY_DATE, "Seattle, WA",
                    ORG_ID, ORG_NAME, "Chelsea Pham", "chelsea@example.com", "2026-07-01T10:00:00"));
            when(waitlistRepository.findByOpportunityId(OPPORTUNITY_ID)).thenReturn(expected);

            assertThat(waitlistService
                    .getWaitlistForOrganizationOpportunity(OPPORTUNITY_ID, ORG_ID))
                    .isSameAs(expected);
        }

        @Test
        @DisplayName("passes the exact opportunityId through to the waitlist repository")
        void passesOpportunityIdThrough() {
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity("OPEN", 10, 10)));
            when(waitlistRepository.findByOpportunityId(OPPORTUNITY_ID)).thenReturn(List.of());

            waitlistService.getWaitlistForOrganizationOpportunity(OPPORTUNITY_ID, ORG_ID);

            verify(waitlistRepository).findByOpportunityId(OPPORTUNITY_ID);
        }

        @Test
        @DisplayName("throws NotFoundException when the opportunity does not exist")
        void throwsWhenOpportunityMissing() {
            when(opportunityRepository.findById(OPPORTUNITY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> waitlistService
                    .getWaitlistForOrganizationOpportunity(OPPORTUNITY_ID, ORG_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Opportunity not found: " + OPPORTUNITY_ID);

            verifyNoInteractions(waitlistRepository);
        }

        @Test
        @DisplayName("a caller from a different organization receives ForbiddenException")
        void rejectsNonOwner() {
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity("OPEN", 10, 10)));

            assertThatThrownBy(() -> waitlistService
                    .getWaitlistForOrganizationOpportunity(OPPORTUNITY_ID, "org-99"))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Only the organization that owns this opportunity can view "
                            + "its waitlist.");
        }

        @Test
        @DisplayName("does not query the waitlist repository when ownership fails")
        void skipsQueryWhenNotOwner() {
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity("OPEN", 10, 10)));

            assertThatThrownBy(() -> waitlistService
                    .getWaitlistForOrganizationOpportunity(OPPORTUNITY_ID, "org-99"))
                    .isInstanceOf(ForbiddenException.class);

            verify(waitlistRepository, never()).findByOpportunityId(anyString());
        }
    }

    @Nested
    @DisplayName("getWaitlistByUserId")
    class GetWaitlistByUserId {

        @Test
        @DisplayName("returns whatever the repository provides")
        void returnsRepositoryResult() {
            Waitlist entry = new Waitlist(
                    USER_ID, OPPORTUNITY_ID, "Beach Cleanup", OPPORTUNITY_DATE, "Seattle, WA",
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
        return opportunity(status, capacity, registeredCount, OPPORTUNITY_DATE);
    }

    private static Opportunity opportunity(String status, int capacity, int registeredCount, String date) {
        return new Opportunity(
                OPPORTUNITY_ID,
                "Beach Cleanup",
                "Pick up litter",
                "ENVIRONMENT",
                "Seattle, WA",
                date,
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
