package com.wevolunteer.backend.service;

import com.wevolunteer.backend.dto.RegisterRequest;
import com.wevolunteer.backend.dto.RegisterResponse;
import com.wevolunteer.backend.exception.ConflictException;
import com.wevolunteer.backend.exception.NotFoundException;
import com.wevolunteer.backend.model.Opportunity;
import com.wevolunteer.backend.model.Registration;
import com.wevolunteer.backend.model.User;
import com.wevolunteer.backend.repository.OpportunityRepository;
import com.wevolunteer.backend.repository.RegistrationRepository;
import com.wevolunteer.backend.repository.UserRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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

    @InjectMocks
    private RegistrationService registrationService;

    @Nested
    @DisplayName("read queries")
    class ReadQueries {

        @Test
        @DisplayName("getRegistrationsByUserId delegates to the repository")
        void delegatesByUserId() {
            List<Registration> expected = List.of(registration(USER_ID, OPPORTUNITY_ID));
            when(registrationRepository.findByUserId(USER_ID)).thenReturn(expected);

            assertThat(registrationService.getRegistrationsByUserId(USER_ID)).isSameAs(expected);
            verifyNoInteractions(userRepository, opportunityRepository);
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
        @DisplayName("returns an empty list when the user has no registrations")
        void returnsEmptyList() {
            when(registrationRepository.findByUserId(USER_ID)).thenReturn(List.of());

            assertThat(registrationService.getRegistrationsByUserId(USER_ID)).isEmpty();
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

            verifyNoInteractions(opportunityRepository, registrationRepository);
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

            verifyNoInteractions(registrationRepository);
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
                            "9:00 AM - 1:00 PM", List.of("Sort and organize donations", "Help set up the distribution area"))));

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
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity(10, 3)));

            registrationService.cancelRegistration(USER_ID, OPPORTUNITY_ID);

            verify(registrationRepository)
                    .cancelRegistration(USER_ID, OPPORTUNITY_ID, "2026-08-01");
            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("throws NotFoundException and cancels nothing when the opportunity is absent")
        void throwsWhenOpportunityMissing() {
            when(opportunityRepository.findById(OPPORTUNITY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    registrationService.cancelRegistration(USER_ID, OPPORTUNITY_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Opportunity not found: " + OPPORTUNITY_ID);

            verifyNoInteractions(registrationRepository);
        }

        @Test
        @DisplayName("does not verify the user exists before cancelling")
        void doesNotCheckUser() {
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity(10, 3)));

            registrationService.cancelRegistration("unknown-user", OPPORTUNITY_ID);

            verify(userRepository, never()).findById(any());
            verify(registrationRepository)
                    .cancelRegistration("unknown-user", OPPORTUNITY_ID, "2026-08-01");
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
                "9:00 AM - 1:00 PM", List.of("Sort and organize donations", "Help set up the distribution area"));
    }

    private static Registration registration(String userId, String opportunityId) {
        return new Registration(
                userId,
                opportunityId,
                "Beach Cleanup",
                "2026-08-01",
                "Seattle, WA",
                ORG_ID,
                ORG_NAME,
                "ACTIVE",
                "Chelsea Pham",
                "chelsea@example.com",
                "2026-07-24T10:00:00");
    }
}
