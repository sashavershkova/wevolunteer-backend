package com.wevolunteer.backend.service;

import com.wevolunteer.backend.dto.RegisterRequest;
import com.wevolunteer.backend.dto.RegisterResponse;
import com.wevolunteer.backend.exception.ConflictException;
import com.wevolunteer.backend.exception.ForbiddenException;
import com.wevolunteer.backend.exception.NotFoundException;
import com.wevolunteer.backend.model.Opportunity;
import com.wevolunteer.backend.model.Registration;
import com.wevolunteer.backend.model.User;
import com.wevolunteer.backend.model.Waitlist;
import com.wevolunteer.backend.notification.NotificationEvent;
import com.wevolunteer.backend.notification.NotificationEventType;
import com.wevolunteer.backend.notification.NotificationPublisher;
import com.wevolunteer.backend.repository.OpportunityRepository;
import com.wevolunteer.backend.repository.RegistrationRepository;
import com.wevolunteer.backend.repository.UserRepository;
import com.wevolunteer.backend.repository.WaitlistRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RegistrationService")
class RegistrationServiceTest {

    private static final String USER_ID = "user-1";
    private static final String OPPORTUNITY_ID = "opp-1";
    private static final String ORG_ID = "org-1";
    private static final String ORG_NAME = "Green Earth";

    @Mock
    private RegistrationRepository registrationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OpportunityRepository opportunityRepository;

    @Mock
    private NotificationPublisher notificationPublisher;

    @Mock
    private WaitlistRepository waitlistRepository;

    @InjectMocks
    private RegistrationService registrationService;

    @Nested
    @DisplayName("read queries")
    class ReadQueries {

        @Test
        @DisplayName("getRegistrationsByUserId enriches a registration with its opportunity's structured time")
        void enrichesWithStructuredTime() {
            when(registrationRepository.findByUserId(USER_ID))
                    .thenReturn(List.of(registration(USER_ID, OPPORTUNITY_ID)));
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunityWithTime(null, "09:00", "12:00")));

            Registration enriched = registrationService.getRegistrationsByUserId(USER_ID).get(0);

            assertThat(enriched.startTime()).isEqualTo("09:00");
            assertThat(enriched.endTime()).isEqualTo("12:00");
            assertThat(enriched.time()).isNull();
        }

        @Test
        @DisplayName("getRegistrationsByUserId enriches a registration with the opportunity's legacy time when startTime/endTime are absent")
        void enrichesWithLegacyTime() {
            when(registrationRepository.findByUserId(USER_ID))
                    .thenReturn(List.of(registration(USER_ID, OPPORTUNITY_ID)));
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunityWithTime("9:00 AM - 12:00 PM", null, null)));

            Registration enriched = registrationService.getRegistrationsByUserId(USER_ID).get(0);

            assertThat(enriched.time()).isEqualTo("9:00 AM - 12:00 PM");
            assertThat(enriched.startTime()).isNull();
            assertThat(enriched.endTime()).isNull();
        }

        @Test
        @DisplayName("getRegistrationsByUserId returns null time fields when the opportunity has no time information")
        void enrichesWithNoTime() {
            when(registrationRepository.findByUserId(USER_ID))
                    .thenReturn(List.of(registration(USER_ID, OPPORTUNITY_ID)));
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunityWithTime(null, null, null)));

            Registration enriched = registrationService.getRegistrationsByUserId(USER_ID).get(0);

            assertThat(enriched.time()).isNull();
            assertThat(enriched.startTime()).isNull();
            assertThat(enriched.endTime()).isNull();
        }

        @Test
        @DisplayName("getRegistrationsByUserId still returns a stale registration with null time fields when its opportunity is gone")
        void returnsRegistrationWithNullTimeWhenOpportunityMissing() {
            when(registrationRepository.findByUserId(USER_ID))
                    .thenReturn(List.of(registration(USER_ID, OPPORTUNITY_ID)));
            when(opportunityRepository.findById(OPPORTUNITY_ID)).thenReturn(Optional.empty());

            List<Registration> result = registrationService.getRegistrationsByUserId(USER_ID);

            assertThat(result).hasSize(1);
            Registration enriched = result.get(0);
            assertThat(enriched.opportunityId()).isEqualTo(OPPORTUNITY_ID);
            assertThat(enriched.time()).isNull();
            assertThat(enriched.startTime()).isNull();
            assertThat(enriched.endTime()).isNull();
        }

        @Test
        @DisplayName("getRegistrationsByOpportunityId delegates to the repository")
        void delegatesByOpportunityId() {
            List<Registration> expected = List.of(registration(USER_ID, OPPORTUNITY_ID));
            when(registrationRepository.findByOpportunityId(OPPORTUNITY_ID)).thenReturn(expected);

            assertThat(registrationService.getRegistrationsByOpportunityId(OPPORTUNITY_ID))
                    .isSameAs(expected);
            verifyNoInteractions(userRepository, opportunityRepository);
        }

        @Test
        @DisplayName("returns an empty list without querying opportunities when the user has no registrations")
        void returnsEmptyList() {
            when(registrationRepository.findByUserId(USER_ID)).thenReturn(List.of());

            assertThat(registrationService.getRegistrationsByUserId(USER_ID)).isEmpty();
            verifyNoInteractions(opportunityRepository);
        }
    }

    @Nested
    @DisplayName("getRegistrationsForOrganizationOpportunity")
    class OrganizationOpportunityRegistrations {

        @Test
        @DisplayName("owner receives the registrations for their opportunity")
        void ownerReceivesRegistrations() {
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity(10, 3)));
            List<Registration> expected = List.of(registration(USER_ID, OPPORTUNITY_ID));
            when(registrationRepository.findByOpportunityId(OPPORTUNITY_ID)).thenReturn(expected);

            assertThat(registrationService
                    .getRegistrationsForOrganizationOpportunity(OPPORTUNITY_ID, ORG_ID))
                    .isSameAs(expected);
        }

        @Test
        @DisplayName("passes the exact opportunityId through to the registration repository")
        void passesOpportunityIdThrough() {
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity(10, 3)));
            when(registrationRepository.findByOpportunityId(OPPORTUNITY_ID)).thenReturn(List.of());

            registrationService.getRegistrationsForOrganizationOpportunity(OPPORTUNITY_ID, ORG_ID);

            verify(registrationRepository).findByOpportunityId(OPPORTUNITY_ID);
        }

        @Test
        @DisplayName("throws NotFoundException when the opportunity does not exist")
        void throwsWhenOpportunityMissing() {
            when(opportunityRepository.findById(OPPORTUNITY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> registrationService
                    .getRegistrationsForOrganizationOpportunity(OPPORTUNITY_ID, ORG_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Opportunity not found: " + OPPORTUNITY_ID);

            verifyNoInteractions(registrationRepository);
        }

        @Test
        @DisplayName("a caller from a different organization receives ForbiddenException")
        void rejectsNonOwner() {
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity(10, 3)));

            assertThatThrownBy(() -> registrationService
                    .getRegistrationsForOrganizationOpportunity(OPPORTUNITY_ID, "org-99"))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Only the organization that owns this opportunity can view "
                            + "its registrations.");
        }

        @Test
        @DisplayName("does not query the registration repository when ownership fails")
        void skipsQueryWhenNotOwner() {
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity(10, 3)));

            assertThatThrownBy(() -> registrationService
                    .getRegistrationsForOrganizationOpportunity(OPPORTUNITY_ID, "org-99"))
                    .isInstanceOf(ForbiddenException.class);

            verify(registrationRepository, never()).findByOpportunityId(anyString());
        }
    }

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("flattens the user and opportunity into the repository call")
        void flattensArgumentsForRepository() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity(10, 3)));

            registrationService.register(new RegisterRequest(USER_ID, OPPORTUNITY_ID));

            verify(registrationRepository).registerUserForOpportunity(
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
        @DisplayName("publishes exactly one REGISTRATION_CREATED event built from the current user and opportunity")
        void publishesRegistrationCreatedEvent() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity(10, 3)));

            Instant before = Instant.now();
            registrationService.register(new RegisterRequest(USER_ID, OPPORTUNITY_ID));
            Instant after = Instant.now();

            ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
            verify(notificationPublisher).publish(captor.capture());

            NotificationEvent event = captor.getValue();
            assertThat(event.eventType()).isEqualTo(NotificationEventType.REGISTRATION_CREATED);
            assertThat(event.userId()).isEqualTo(USER_ID);
            assertThat(event.volunteerName()).isEqualTo("Chelsea Pham");
            assertThat(event.volunteerEmail()).isEqualTo("chelsea@example.com");
            assertThat(event.opportunityId()).isEqualTo(OPPORTUNITY_ID);
            assertThat(event.opportunityTitle()).isEqualTo("Beach Cleanup");
            assertThat(event.opportunityDate()).isEqualTo("2026-08-01");
            assertThat(event.organizationId()).isEqualTo(ORG_ID);
            assertThat(event.organizationName()).isEqualTo(ORG_NAME);
            assertThat(event.timestamp()).isNotNull().isBetween(before, after);
        }

        @Test
        @DisplayName("publishes the notification only after the registration repository call succeeds")
        void publishesAfterRepositoryCall() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity(10, 3)));

            registrationService.register(new RegisterRequest(USER_ID, OPPORTUNITY_ID));

            InOrder inOrder = inOrder(registrationRepository, notificationPublisher);
            inOrder.verify(registrationRepository).registerUserForOpportunity(
                    anyString(), anyString(), anyString(), anyString(), anyString(),
                    anyString(), anyString(), anyString(), anyString());
            inOrder.verify(notificationPublisher).publish(any(NotificationEvent.class));
        }

        @Test
        @DisplayName("does not publish a notification when the repository rejects the registration")
        void doesNotPublishWhenRepositoryRejectsRegistration() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity(10, 10)));
            doThrow(new ConflictException("Opportunity '" + OPPORTUNITY_ID
                    + "' is no longer open or has reached capacity."))
                    .when(registrationRepository).registerUserForOpportunity(
                            anyString(), anyString(), anyString(), anyString(), anyString(),
                            anyString(), anyString(), anyString(), anyString());

            assertThatThrownBy(() ->
                    registrationService.register(new RegisterRequest(USER_ID, OPPORTUNITY_ID)))
                    .isInstanceOf(ConflictException.class);

            verifyNoInteractions(notificationPublisher);
        }

        @Test
        @DisplayName("returns a success response with counts projected one step forward")
        void returnsProjectedCounts() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity(10, 3)));

            RegisterResponse response =
                    registrationService.register(new RegisterRequest(USER_ID, OPPORTUNITY_ID));

            assertThat(response).isEqualTo(new RegisterResponse(
                    "Registration successful", USER_ID, OPPORTUNITY_ID, 4, 6));
        }

        @Test
        @DisplayName("reports zero remaining spots when the registration fills the last place")
        void reportsZeroRemainingSpots() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity(10, 9)));

            RegisterResponse response =
                    registrationService.register(new RegisterRequest(USER_ID, OPPORTUNITY_ID));

            assertThat(response.registeredCount()).isEqualTo(10);
            assertThat(response.availableSpots()).isZero();
        }

        @Test
        @DisplayName("throws NotFoundException and never looks up the opportunity when the user is absent")
        void throwsWhenUserMissing() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    registrationService.register(new RegisterRequest(USER_ID, OPPORTUNITY_ID)))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("User not found: " + USER_ID);

            verifyNoInteractions(opportunityRepository, registrationRepository, notificationPublisher);
        }

        @Test
        @DisplayName("throws NotFoundException and writes nothing when the opportunity is absent")
        void throwsWhenOpportunityMissing() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
            when(opportunityRepository.findById(OPPORTUNITY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    registrationService.register(new RegisterRequest(USER_ID, OPPORTUNITY_ID)))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Opportunity not found: " + OPPORTUNITY_ID);

            verifyNoInteractions(registrationRepository, notificationPublisher);
        }

        @Test
        @DisplayName("propagates a ConflictException from the repository as-is")
        void propagatesConflict() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity(10, 10)));
            doThrow(new ConflictException("Opportunity '" + OPPORTUNITY_ID
                    + "' is no longer open or has reached capacity."))
                    .when(registrationRepository).registerUserForOpportunity(
                            anyString(), anyString(), anyString(), anyString(), anyString(),
                            anyString(), anyString(), anyString(), anyString());

            assertThatThrownBy(() ->
                    registrationService.register(new RegisterRequest(USER_ID, OPPORTUNITY_ID)))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("no longer open or has reached capacity");
        }

        @Test
        @DisplayName("does not reject a closed or full opportunity itself, leaving that to the repository")
        void leavesCapacityEnforcementToRepository() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(new Opportunity(
                            OPPORTUNITY_ID, "Beach Cleanup", "Pick up litter", "ENVIRONMENT",
                            "Seattle, WA", "2026-08-01", "CLOSED", ORG_ID, ORG_NAME, 10, 10, 0,
                            null, "09:00", "12:00", List.of("Sort and organize donations", "Help set up the distribution area"), false)));

            registrationService.register(new RegisterRequest(USER_ID, OPPORTUNITY_ID));

            verify(registrationRepository).registerUserForOpportunity(
                    anyString(), anyString(), anyString(), anyString(), anyString(),
                    anyString(), anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("cancelRegistration")
    class CancelRegistration {

        @Test
        @DisplayName("looks the opportunity up to supply its date as the sort key")
        void suppliesOpportunityDate() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity(10, 3)));

            registrationService.cancelRegistration(USER_ID, OPPORTUNITY_ID);

            verify(registrationRepository)
                    .cancelRegistration(USER_ID, OPPORTUNITY_ID, "2026-08-01");
        }

        @Test
        @DisplayName("throws NotFoundException and cancels nothing when the opportunity is absent")
        void throwsWhenOpportunityMissing() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
            when(opportunityRepository.findById(OPPORTUNITY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    registrationService.cancelRegistration(USER_ID, OPPORTUNITY_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Opportunity not found: " + OPPORTUNITY_ID);

            verifyNoInteractions(registrationRepository, notificationPublisher);
        }

        @Test
        @DisplayName("throws NotFoundException and looks up nothing else when the user is absent")
        void throwsWhenUserMissing() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    registrationService.cancelRegistration(USER_ID, OPPORTUNITY_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("User not found: " + USER_ID);

            verifyNoInteractions(opportunityRepository, registrationRepository, notificationPublisher);
        }

        @Test
        @DisplayName("publishes exactly one REGISTRATION_CANCELLED event built from the current user and opportunity")
        void publishesRegistrationCancelledEvent() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity(10, 3)));

            Instant before = Instant.now();
            registrationService.cancelRegistration(USER_ID, OPPORTUNITY_ID);
            Instant after = Instant.now();

            ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
            verify(notificationPublisher).publish(captor.capture());

            NotificationEvent event = captor.getValue();
            assertThat(event.eventType()).isEqualTo(NotificationEventType.REGISTRATION_CANCELLED);
            assertThat(event.userId()).isEqualTo(USER_ID);
            assertThat(event.volunteerName()).isEqualTo("Chelsea Pham");
            assertThat(event.volunteerEmail()).isEqualTo("chelsea@example.com");
            assertThat(event.opportunityId()).isEqualTo(OPPORTUNITY_ID);
            assertThat(event.opportunityTitle()).isEqualTo("Beach Cleanup");
            assertThat(event.opportunityDate()).isEqualTo("2026-08-01");
            assertThat(event.organizationId()).isEqualTo(ORG_ID);
            assertThat(event.organizationName()).isEqualTo(ORG_NAME);
            assertThat(event.timestamp()).isNotNull().isBetween(before, after);
        }

        @Test
        @DisplayName("publishes the notification only after the cancellation repository call succeeds")
        void publishesAfterRepositoryCall() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity(10, 3)));

            registrationService.cancelRegistration(USER_ID, OPPORTUNITY_ID);

            InOrder inOrder = inOrder(registrationRepository, notificationPublisher);
            inOrder.verify(registrationRepository)
                    .cancelRegistration(USER_ID, OPPORTUNITY_ID, "2026-08-01");
            inOrder.verify(notificationPublisher).publish(any(NotificationEvent.class));
        }

        @Test
        @DisplayName("does not publish a notification when the repository rejects the cancellation")
        void doesNotPublishWhenRepositoryFails() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity(10, 3)));
            doThrow(new ConflictException("Registration not found for user '" + USER_ID
                    + "' and opportunity '" + OPPORTUNITY_ID + "'."))
                    .when(registrationRepository)
                    .cancelRegistration(USER_ID, OPPORTUNITY_ID, "2026-08-01");

            assertThatThrownBy(() ->
                    registrationService.cancelRegistration(USER_ID, OPPORTUNITY_ID))
                    .isInstanceOf(ConflictException.class);

            verifyNoInteractions(notificationPublisher);
        }

        @Test
        @DisplayName("promotes the oldest waitlisted volunteer into the freed spot")
        void promotesOldestWaitlistedVolunteerWhenWaitlistNonEmpty() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity(10, 3)));
            when(waitlistRepository.findByOpportunityId(OPPORTUNITY_ID)).thenReturn(List.of(
                    waitlistEntry("user-2", "Jordan Miles", "jordan@example.com"),
                    waitlistEntry("user-3", "Amara Kim", "amara@example.com")));

            registrationService.cancelRegistration(USER_ID, OPPORTUNITY_ID);

            verify(registrationRepository).registerUserForOpportunity(
                    "user-2",
                    "Jordan Miles",
                    "jordan@example.com",
                    OPPORTUNITY_ID,
                    "Beach Cleanup",
                    "2026-08-01",
                    "Seattle, WA",
                    ORG_ID,
                    ORG_NAME);
            verify(waitlistRepository).leaveWaitlist("user-2", OPPORTUNITY_ID, "2026-08-01");

            ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
            verify(notificationPublisher, times(2)).publish(captor.capture());

            NotificationEvent promotedEvent = captor.getAllValues().get(1);
            assertThat(promotedEvent.eventType()).isEqualTo(NotificationEventType.WAITLIST_PROMOTED);
            assertThat(promotedEvent.userId()).isEqualTo("user-2");
            assertThat(promotedEvent.volunteerName()).isEqualTo("Jordan Miles");
            assertThat(promotedEvent.volunteerEmail()).isEqualTo("jordan@example.com");
            assertThat(promotedEvent.opportunityId()).isEqualTo(OPPORTUNITY_ID);
            assertThat(promotedEvent.opportunityTitle()).isEqualTo("Beach Cleanup");
            assertThat(promotedEvent.opportunityDate()).isEqualTo("2026-08-01");
            assertThat(promotedEvent.organizationId()).isEqualTo(ORG_ID);
            assertThat(promotedEvent.organizationName()).isEqualTo(ORG_NAME);
        }

        @Test
        @DisplayName("does nothing extra when the waitlist is empty")
        void doesNothingExtraWhenWaitlistEmpty() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity(10, 3)));
            when(waitlistRepository.findByOpportunityId(OPPORTUNITY_ID)).thenReturn(List.of());

            registrationService.cancelRegistration(USER_ID, OPPORTUNITY_ID);

            verify(registrationRepository, never()).registerUserForOpportunity(
                    anyString(), anyString(), anyString(), anyString(), anyString(),
                    anyString(), anyString(), anyString(), anyString());
            verify(waitlistRepository, never())
                    .leaveWaitlist(anyString(), anyString(), anyString());
            verify(notificationPublisher, times(1)).publish(any(NotificationEvent.class));
        }

        @Test
        @DisplayName("cancellation still succeeds when the promotion write is rejected")
        void cancellationSucceedsWhenPromotionWriteThrowsConflictException() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity(10, 3)));
            when(waitlistRepository.findByOpportunityId(OPPORTUNITY_ID))
                    .thenReturn(List.of(waitlistEntry("user-2", "Jordan Miles", "jordan@example.com")));
            doThrow(new ConflictException("Opportunity '" + OPPORTUNITY_ID
                    + "' is no longer open or has reached capacity."))
                    .when(registrationRepository).registerUserForOpportunity(
                            "user-2", "Jordan Miles", "jordan@example.com", OPPORTUNITY_ID,
                            "Beach Cleanup", "2026-08-01", "Seattle, WA", ORG_ID, ORG_NAME);

            registrationService.cancelRegistration(USER_ID, OPPORTUNITY_ID);

            verify(waitlistRepository, never())
                    .leaveWaitlist(anyString(), anyString(), anyString());

            ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
            verify(notificationPublisher, times(1)).publish(captor.capture());
            assertThat(captor.getValue().eventType())
                    .isEqualTo(NotificationEventType.REGISTRATION_CANCELLED);
        }
    }

    @Nested
    @DisplayName("cancelAllRegistrationsForOpportunity")
    class CancelAllRegistrationsForOpportunity {

        @Test
        @DisplayName("cancels every registration found for the opportunity by userId alone, "
                + "even when the opportunity-side item carries no date")
        void cancelsEveryRegistration() {
            // Opportunity-side items never carry a date attribute in production; date is null
            // here deliberately to prove the close path no longer depends on it.
            when(registrationRepository.findByOpportunityId(OPPORTUNITY_ID)).thenReturn(List.of(
                    registrationWithDate("user-1", OPPORTUNITY_ID, null),
                    registrationWithDate("user-2", OPPORTUNITY_ID, null)));

            registrationService.cancelAllRegistrationsForOpportunity(opportunity(10, 2));

            verify(registrationRepository).cancelRegistrationForOpportunityClose("user-1", OPPORTUNITY_ID);
            verify(registrationRepository).cancelRegistrationForOpportunityClose("user-2", OPPORTUNITY_ID);
            verify(registrationRepository, never())
                    .cancelRegistration(anyString(), anyString(), anyString());
            verifyNoInteractions(opportunityRepository, waitlistRepository);
        }

        @Test
        @DisplayName("succeeds without cancelling or publishing anything when there are no registrations")
        void succeedsWithNoRegistrations() {
            when(registrationRepository.findByOpportunityId(OPPORTUNITY_ID)).thenReturn(List.of());

            registrationService.cancelAllRegistrationsForOpportunity(opportunity(10, 0));

            verify(registrationRepository, never())
                    .cancelRegistrationForOpportunityClose(anyString(), anyString());
            verifyNoInteractions(notificationPublisher);
        }

        @Test
        @DisplayName("publishes exactly one REGISTRATION_CANCELLED_BY_ORGANIZATION event "
                + "for a single affected volunteer")
        void publishesOneEventForOneVolunteer() {
            when(registrationRepository.findByOpportunityId(OPPORTUNITY_ID)).thenReturn(List.of(
                    registrationForClose("user-1", "Alice", "alice@example.com")));

            Instant before = Instant.now();
            registrationService.cancelAllRegistrationsForOpportunity(opportunity(10, 1));
            Instant after = Instant.now();

            ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
            verify(notificationPublisher).publish(captor.capture());

            NotificationEvent event = captor.getValue();
            assertThat(event.eventType())
                    .isEqualTo(NotificationEventType.REGISTRATION_CANCELLED_BY_ORGANIZATION);
            assertThat(event.userId()).isEqualTo("user-1");
            assertThat(event.volunteerName()).isEqualTo("Alice");
            assertThat(event.volunteerEmail()).isEqualTo("alice@example.com");
            assertThat(event.opportunityId()).isEqualTo(OPPORTUNITY_ID);
            assertThat(event.opportunityTitle()).isEqualTo("Beach Cleanup");
            assertThat(event.opportunityDate()).isEqualTo("2026-08-01");
            assertThat(event.organizationId()).isEqualTo(ORG_ID);
            assertThat(event.organizationName()).isEqualTo(ORG_NAME);
            assertThat(event.timestamp()).isNotNull().isBetween(before, after);
        }

        @Test
        @DisplayName("publishes one REGISTRATION_CANCELLED_BY_ORGANIZATION event per affected "
                + "volunteer, each carrying that volunteer's own data")
        void publishesOneEventPerVolunteer() {
            when(registrationRepository.findByOpportunityId(OPPORTUNITY_ID)).thenReturn(List.of(
                    registrationForClose("user-1", "Alice", "alice@example.com"),
                    registrationForClose("user-2", "Bob", "bob@example.com"),
                    registrationForClose("user-3", "Cara", "cara@example.com")));

            registrationService.cancelAllRegistrationsForOpportunity(opportunity(10, 3));

            ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
            verify(notificationPublisher, times(3)).publish(captor.capture());

            assertThat(captor.getAllValues())
                    .extracting(
                            NotificationEvent::userId,
                            NotificationEvent::volunteerName,
                            NotificationEvent::volunteerEmail)
                    .containsExactlyInAnyOrder(
                            tuple("user-1", "Alice", "alice@example.com"),
                            tuple("user-2", "Bob", "bob@example.com"),
                            tuple("user-3", "Cara", "cara@example.com"));
        }

        @Test
        @DisplayName("publishes a volunteer's event only after that volunteer's cancellation succeeds")
        void publishesAfterEachCancellationSucceeds() {
            when(registrationRepository.findByOpportunityId(OPPORTUNITY_ID)).thenReturn(List.of(
                    registrationForClose("user-1", "Alice", "alice@example.com")));

            registrationService.cancelAllRegistrationsForOpportunity(opportunity(10, 1));

            InOrder inOrder = inOrder(registrationRepository, notificationPublisher);
            inOrder.verify(registrationRepository)
                    .cancelRegistrationForOpportunityClose("user-1", OPPORTUNITY_ID);
            inOrder.verify(notificationPublisher).publish(any(NotificationEvent.class));
        }

        @Test
        @DisplayName("propagates a failure from the repository instead of continuing or swallowing it, "
                + "and never publishes anything for that volunteer")
        void propagatesRepositoryFailure() {
            when(registrationRepository.findByOpportunityId(OPPORTUNITY_ID)).thenReturn(List.of(
                    registration("user-1", OPPORTUNITY_ID),
                    registration("user-2", OPPORTUNITY_ID)));
            doThrow(new RuntimeException("DynamoDB transaction failed"))
                    .when(registrationRepository)
                    .cancelRegistrationForOpportunityClose("user-1", OPPORTUNITY_ID);

            assertThatThrownBy(() ->
                    registrationService.cancelAllRegistrationsForOpportunity(opportunity(10, 2)))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("DynamoDB transaction failed");

            verifyNoInteractions(notificationPublisher);
        }

        @Test
        @DisplayName("keeps the event already published for an earlier volunteer "
                + "when a later volunteer's cancellation fails")
        void keepsEarlierPublishWhenLaterCancellationFails() {
            when(registrationRepository.findByOpportunityId(OPPORTUNITY_ID)).thenReturn(List.of(
                    registrationForClose("user-1", "Alice", "alice@example.com"),
                    registrationForClose("user-2", "Bob", "bob@example.com")));
            doNothing()
                    .when(registrationRepository)
                    .cancelRegistrationForOpportunityClose("user-1", OPPORTUNITY_ID);
            doThrow(new RuntimeException("DynamoDB transaction failed"))
                    .when(registrationRepository)
                    .cancelRegistrationForOpportunityClose("user-2", OPPORTUNITY_ID);

            assertThatThrownBy(() ->
                    registrationService.cancelAllRegistrationsForOpportunity(opportunity(10, 2)))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("DynamoDB transaction failed");

            ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
            verify(notificationPublisher, times(1)).publish(captor.capture());
            assertThat(captor.getValue().userId()).isEqualTo("user-1");
        }
    }

    private static User user() {
        return new User(USER_ID, "Chelsea Pham", "chelsea@example.com", "VOLUNTEER");
    }

    private static Opportunity opportunity(int capacity, int registeredCount) {
        return new Opportunity(
                OPPORTUNITY_ID,
                "Beach Cleanup",
                "Pick up litter",
                "ENVIRONMENT",
                "Seattle, WA",
                "2026-08-01",
                "OPEN",
                ORG_ID,
                ORG_NAME,
                capacity,
                registeredCount,
                capacity - registeredCount,
                null, "09:00", "12:00", List.of("Sort and organize donations", "Help set up the distribution area"), false);
    }

    private static Opportunity opportunityWithTime(String time, String startTime, String endTime) {
        return new Opportunity(
                OPPORTUNITY_ID,
                "Beach Cleanup",
                "Pick up litter",
                "ENVIRONMENT",
                "Seattle, WA",
                "2026-08-01",
                "OPEN",
                ORG_ID,
                ORG_NAME,
                10,
                3,
                7,
                time, startTime, endTime,
                List.of("Sort and organize donations", "Help set up the distribution area"), false);
    }

    private static Waitlist waitlistEntry(String userId, String volunteerName, String email) {
        return new Waitlist(
                userId,
                OPPORTUNITY_ID,
                "Beach Cleanup",
                "2026-08-01",
                "Seattle, WA",
                ORG_ID,
                ORG_NAME,
                volunteerName,
                email,
                "2026-07-01T10:00:00");
    }

    private static Registration registration(String userId, String opportunityId) {
        return registrationWithDate(userId, opportunityId, "2026-08-01");
    }

    private static Registration registrationWithDate(String userId, String opportunityId, String date) {
        return new Registration(
                userId,
                opportunityId,
                "Beach Cleanup",
                date,
                "Seattle, WA",
                ORG_ID,
                ORG_NAME,
                "ACTIVE",
                "Chelsea Pham",
                "chelsea@example.com",
                "2026-07-24T10:00:00",
                null, null, null);
    }

    /**
     * An opportunity-side registration item as findByOpportunityId actually returns it: it
     * carries volunteerName/email (written by registerUserForOpportunity) but no title/date/
     * location/organizationId/organizationName, since those attributes live only on the
     * user-side item.
     */
    private static Registration registrationForClose(String userId, String volunteerName, String email) {
        return new Registration(
                userId,
                OPPORTUNITY_ID,
                null, null, null, null, null,
                "ACTIVE",
                volunteerName,
                email,
                "2026-07-24T10:00:00",
                null, null, null);
    }
}
