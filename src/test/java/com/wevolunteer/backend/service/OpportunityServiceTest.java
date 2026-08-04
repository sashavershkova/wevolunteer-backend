package com.wevolunteer.backend.service;

import com.wevolunteer.backend.dto.CreateOpportunityRequest;
import com.wevolunteer.backend.dto.UpdateOpportunityRequest;
import com.wevolunteer.backend.exception.ConflictException;
import com.wevolunteer.backend.exception.ForbiddenException;
import com.wevolunteer.backend.exception.NotFoundException;
import com.wevolunteer.backend.model.Opportunity;
import com.wevolunteer.backend.repository.OpportunityRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OpportunityService")
class OpportunityServiceTest {

    private static final String OPPORTUNITY_ID = "opp-1";
    private static final String ORG_ID = "org-1";
    private static final String ORG_NAME = "Green Earth";

    @Mock
    private OpportunityRepository opportunityRepository;

    @Mock
    private RegistrationService registrationService;

    @InjectMocks
    private OpportunityService opportunityService;

    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        @DisplayName("returns the opportunity when the repository finds it")
        void returnsOpportunityWhenFound() {
            Opportunity opportunity = opportunity(OPPORTUNITY_ID, "OPEN", 10, 0);
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity));

            assertThat(opportunityService.getById(OPPORTUNITY_ID)).isSameAs(opportunity);
        }

        @Test
        @DisplayName("throws NotFoundException with the id in the message when absent")
        void throwsWhenMissing() {
            when(opportunityRepository.findById(OPPORTUNITY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> opportunityService.getById(OPPORTUNITY_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Opportunity not found: " + OPPORTUNITY_ID);
        }
    }

    @Nested
    @DisplayName("simple query delegation")
    class QueryDelegation {

        @Test
        @DisplayName("getOpenOpportunities delegates to findOpenOpportunities")
        void delegatesOpenOpportunities() {
            List<Opportunity> expected = List.of(opportunity(OPPORTUNITY_ID, "OPEN", 10, 0));
            when(opportunityRepository.findOpenOpportunities()).thenReturn(expected);

            assertThat(opportunityService.getOpenOpportunities()).isSameAs(expected);
        }

        @Test
        @DisplayName("getOpportunitiesByCategory passes the category through unchanged")
        void delegatesByCategory() {
            List<Opportunity> expected = List.of(opportunity(OPPORTUNITY_ID, "OPEN", 10, 0));
            when(opportunityRepository.findByCategory("ENVIRONMENT")).thenReturn(expected);

            assertThat(opportunityService.getOpportunitiesByCategory("ENVIRONMENT"))
                    .isSameAs(expected);
        }

        @Test
        @DisplayName("getOpportunitiesByLocation passes the location through unchanged")
        void delegatesByLocation() {
            List<Opportunity> expected = List.of(opportunity(OPPORTUNITY_ID, "OPEN", 10, 0));
            when(opportunityRepository.findByLocation("Seattle")).thenReturn(expected);

            assertThat(opportunityService.getOpportunitiesByLocation("Seattle"))
                    .isSameAs(expected);
        }

        @Test
        @DisplayName("getOpportunitiesByOrganizationId uses the OPEN-only repository method")
        void delegatesByOrganizationId() {
            List<Opportunity> expected = List.of(opportunity(OPPORTUNITY_ID, "OPEN", 10, 0));
            when(opportunityRepository.findByOrganizationId(ORG_ID)).thenReturn(expected);

            assertThat(opportunityService.getOpportunitiesByOrganizationId(ORG_ID))
                    .isSameAs(expected);
            verify(opportunityRepository, never()).findAllByOrganizationId(anyString());
        }

        @Test
        @DisplayName("getAllOpportunitiesByOrganizationId uses the all-statuses repository method")
        void delegatesAllByOrganizationId() {
            List<Opportunity> expected = List.of(
                    opportunity(OPPORTUNITY_ID, "OPEN", 10, 0),
                    opportunity("opp-2", "CLOSED", 5, 5));
            when(opportunityRepository.findAllByOrganizationId(ORG_ID)).thenReturn(expected);

            assertThat(opportunityService.getAllOpportunitiesByOrganizationId(ORG_ID))
                    .isSameAs(expected);
            verify(opportunityRepository, never()).findByOrganizationId(anyString());
        }

        @Test
        @DisplayName("getOpportunitiesByOrganizationIdAndStatus passes both arguments through")
        void delegatesByOrganizationIdAndStatus() {
            List<Opportunity> expected = List.of(opportunity("opp-2", "CLOSED", 5, 5));
            when(opportunityRepository.findByOrganizationIdAndStatus(ORG_ID, "CLOSED"))
                    .thenReturn(expected);

            assertThat(opportunityService.getOpportunitiesByOrganizationIdAndStatus(ORG_ID, "CLOSED"))
                    .isSameAs(expected);
        }

        @Test
        @DisplayName("returns the empty list the repository returns without substituting null")
        void returnsEmptyList() {
            when(opportunityRepository.findOpenOpportunities()).thenReturn(List.of());

            assertThat(opportunityService.getOpenOpportunities()).isEmpty();
        }

        @Test
        @DisplayName("single-argument deleteOpportunity delegates without checking existence "
                + "first (used only for organization-account cascade cleanup)")
        void delegatesUnconditionalDelete() {
            opportunityService.deleteOpportunity(OPPORTUNITY_ID);

            verify(opportunityRepository).deleteById(OPPORTUNITY_ID);
            verify(opportunityRepository, never()).findById(anyString());
        }
    }

    @Nested
    @DisplayName("closeOpportunity")
    class CloseOpportunity {

        @Test
        @DisplayName("owning organization can close its opportunity")
        void ownerCanClose() {
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity(OPPORTUNITY_ID, "OPEN", 10, 3)));
            Opportunity closed = opportunity(OPPORTUNITY_ID, "CLOSED", 10, 3);
            when(opportunityRepository.close(OPPORTUNITY_ID)).thenReturn(closed);

            assertThat(opportunityService.closeOpportunity(OPPORTUNITY_ID, ORG_ID))
                    .isSameAs(closed);
        }

        @Test
        @DisplayName("successful close delegates to the repository exactly once")
        void delegatesCloseExactlyOnce() {
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity(OPPORTUNITY_ID, "OPEN", 10, 3)));
            when(opportunityRepository.close(OPPORTUNITY_ID))
                    .thenReturn(opportunity(OPPORTUNITY_ID, "CLOSED", 10, 3));

            opportunityService.closeOpportunity(OPPORTUNITY_ID, ORG_ID);

            verify(opportunityRepository, times(1)).close(OPPORTUNITY_ID);
        }

        @Test
        @DisplayName("a caller from a different organization cannot close it")
        void rejectsNonOwner() {
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity(OPPORTUNITY_ID, "OPEN", 10, 3)));

            assertThatThrownBy(() ->
                    opportunityService.closeOpportunity(OPPORTUNITY_ID, "org-99"))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Only the organization that owns this opportunity can close it.");
        }

        @Test
        @DisplayName("does not call repository close when ownership validation fails")
        void skipsCloseWhenNotOwner() {
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity(OPPORTUNITY_ID, "OPEN", 10, 3)));

            assertThatThrownBy(() ->
                    opportunityService.closeOpportunity(OPPORTUNITY_ID, "org-99"))
                    .isInstanceOf(ForbiddenException.class);

            verify(opportunityRepository, never()).close(anyString());
        }

        @Test
        @DisplayName("throws NotFoundException and never checks ownership when the opportunity is absent")
        void throwsWhenMissing() {
            when(opportunityRepository.findById(OPPORTUNITY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    opportunityService.closeOpportunity(OPPORTUNITY_ID, ORG_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Opportunity not found: " + OPPORTUNITY_ID);

            verify(opportunityRepository, never()).close(anyString());
        }

        @Test
        @DisplayName("remains idempotent when the owner closes an already-closed opportunity")
        void idempotentForOwner() {
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity(OPPORTUNITY_ID, "CLOSED", 10, 3)));
            Opportunity closed = opportunity(OPPORTUNITY_ID, "CLOSED", 10, 3);
            when(opportunityRepository.close(OPPORTUNITY_ID)).thenReturn(closed);

            assertThat(opportunityService.closeOpportunity(OPPORTUNITY_ID, ORG_ID))
                    .isSameAs(closed);
            verify(opportunityRepository, times(1)).close(OPPORTUNITY_ID);
        }

        @Test
        @DisplayName("cancels all registrations for the opportunity before closing it")
        void cancelsRegistrationsBeforeClosing() {
            Opportunity open = opportunity(OPPORTUNITY_ID, "OPEN", 20, 2);
            when(opportunityRepository.findById(OPPORTUNITY_ID)).thenReturn(Optional.of(open));
            when(opportunityRepository.close(OPPORTUNITY_ID))
                    .thenReturn(opportunity(OPPORTUNITY_ID, "CLOSED", 20, 0));

            opportunityService.closeOpportunity(OPPORTUNITY_ID, ORG_ID);

            InOrder inOrder = inOrder(registrationService, opportunityRepository);
            inOrder.verify(registrationService).cancelAllRegistrationsForOpportunity(open);
            inOrder.verify(opportunityRepository).close(OPPORTUNITY_ID);
        }

        @Test
        @DisplayName("returns an opportunity with registeredCount reset to 0")
        void returnsZeroedRegisteredCount() {
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity(OPPORTUNITY_ID, "OPEN", 20, 2)));
            when(opportunityRepository.close(OPPORTUNITY_ID))
                    .thenReturn(opportunity(OPPORTUNITY_ID, "CLOSED", 20, 0));

            Opportunity result = opportunityService.closeOpportunity(OPPORTUNITY_ID, ORG_ID);

            assertThat(result.registeredCount()).isZero();
        }

        @Test
        @DisplayName("succeeds when the opportunity has no registrations to cancel")
        void succeedsWithNoRegistrations() {
            Opportunity open = opportunity(OPPORTUNITY_ID, "OPEN", 20, 0);
            when(opportunityRepository.findById(OPPORTUNITY_ID)).thenReturn(Optional.of(open));
            when(opportunityRepository.close(OPPORTUNITY_ID))
                    .thenReturn(opportunity(OPPORTUNITY_ID, "CLOSED", 20, 0));

            assertThatCode(() -> opportunityService.closeOpportunity(OPPORTUNITY_ID, ORG_ID))
                    .doesNotThrowAnyException();

            verify(registrationService).cancelAllRegistrationsForOpportunity(open);
            verify(opportunityRepository).close(OPPORTUNITY_ID);
        }

        @Test
        @DisplayName("does not attempt registration cleanup when ownership validation fails")
        void skipsCleanupWhenNotOwner() {
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity(OPPORTUNITY_ID, "OPEN", 10, 3)));

            assertThatThrownBy(() ->
                    opportunityService.closeOpportunity(OPPORTUNITY_ID, "org-99"))
                    .isInstanceOf(ForbiddenException.class);

            verifyNoInteractions(registrationService);
        }

        @Test
        @DisplayName("propagates a registration cleanup failure and never closes the opportunity")
        void propagatesCleanupFailureWithoutClosing() {
            Opportunity open = opportunity(OPPORTUNITY_ID, "OPEN", 20, 2);
            when(opportunityRepository.findById(OPPORTUNITY_ID)).thenReturn(Optional.of(open));
            doThrow(new RuntimeException("DynamoDB transaction failed"))
                    .when(registrationService)
                    .cancelAllRegistrationsForOpportunity(open);

            assertThatThrownBy(() ->
                    opportunityService.closeOpportunity(OPPORTUNITY_ID, ORG_ID))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("DynamoDB transaction failed");

            verify(opportunityRepository, never()).close(anyString());
        }
    }

    @Nested
    @DisplayName("deleteOpportunity")
    class DeleteOpportunity {

        @Test
        @DisplayName("owning organization can delete a closed, unregistered, future opportunity")
        void ownerCanDelete() {
            // A relative date, not a fixed literal: a hardcoded "future" date eventually becomes
            // the past and starts failing this test on the wrong grounds (the date check, not
            // ownership/status, which is what this test actually covers).
            String futureDate = java.time.LocalDate.now().plusDays(1).toString();
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity(OPPORTUNITY_ID, "CLOSED", 10, 0, futureDate)));

            opportunityService.deleteOpportunity(OPPORTUNITY_ID, ORG_ID);

            verify(opportunityRepository, times(1)).deleteById(OPPORTUNITY_ID);
        }

        @Test
        @DisplayName("a caller from a different organization cannot delete it")
        void rejectsNonOwner() {
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity(OPPORTUNITY_ID, "CLOSED", 10, 0, "2026-08-01")));

            assertThatThrownBy(() ->
                    opportunityService.deleteOpportunity(OPPORTUNITY_ID, "org-99"))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("Only the organization that owns this opportunity can delete it.");

            verify(opportunityRepository, never()).deleteById(anyString());
        }

        @Test
        @DisplayName("throws NotFoundException and never checks ownership when the opportunity is absent")
        void throwsWhenMissing() {
            when(opportunityRepository.findById(OPPORTUNITY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    opportunityService.deleteOpportunity(OPPORTUNITY_ID, ORG_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Opportunity not found: " + OPPORTUNITY_ID);

            verify(opportunityRepository, never()).deleteById(anyString());
        }

        @Test
        @DisplayName("rejects deletion of an OPEN opportunity")
        void rejectsWhenNotClosed() {
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity(OPPORTUNITY_ID, "OPEN", 10, 0, "2026-08-01")));

            assertThatThrownBy(() ->
                    opportunityService.deleteOpportunity(OPPORTUNITY_ID, ORG_ID))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Only closed opportunities can be deleted.");

            verify(opportunityRepository, never()).deleteById(anyString());
        }

        @Test
        @DisplayName("rejects deletion when the opportunity still has registrations")
        void rejectsWhenHasRegistrations() {
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity(OPPORTUNITY_ID, "CLOSED", 10, 3, "2026-08-01")));

            assertThatThrownBy(() ->
                    opportunityService.deleteOpportunity(OPPORTUNITY_ID, ORG_ID))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Opportunities with existing registrations cannot be deleted.");

            verify(opportunityRepository, never()).deleteById(anyString());
        }

        @Test
        @DisplayName("rejects deletion of a past opportunity")
        void rejectsWhenDateInPast() {
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity(OPPORTUNITY_ID, "CLOSED", 10, 0, "2020-01-01")));

            assertThatThrownBy(() ->
                    opportunityService.deleteOpportunity(OPPORTUNITY_ID, ORG_ID))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Past opportunities cannot be deleted.");

            verify(opportunityRepository, never()).deleteById(anyString());
        }

        @Test
        @DisplayName("allows deletion when the opportunity date is exactly today")
        void allowsWhenDateIsToday() {
            String today = java.time.LocalDate.now().toString();
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity(OPPORTUNITY_ID, "CLOSED", 10, 0, today)));

            assertThatCode(() -> opportunityService.deleteOpportunity(OPPORTUNITY_ID, ORG_ID))
                    .doesNotThrowAnyException();

            verify(opportunityRepository, times(1)).deleteById(OPPORTUNITY_ID);
        }
    }

    @Nested
    @DisplayName("date range validation")
    class DateRangeValidation {

        @Test
        @DisplayName("accepts a valid range and delegates to the repository")
        void acceptsValidRange() {
            List<Opportunity> expected = List.of(opportunity(OPPORTUNITY_ID, "OPEN", 10, 0));
            when(opportunityRepository.findOpenOpportunitiesByDateRange("2026-08-01", "2026-08-31"))
                    .thenReturn(expected);

            assertThat(opportunityService
                    .getOpenOpportunitiesByDateRange("2026-08-01", "2026-08-31"))
                    .isSameAs(expected);
        }

        @Test
        @DisplayName("accepts a range where start equals end")
        void acceptsSingleDayRange() {
            when(opportunityRepository.findOpenOpportunitiesByDateRange("2026-08-01", "2026-08-01"))
                    .thenReturn(List.of());

            assertThatCode(() -> opportunityService
                    .getOpenOpportunitiesByDateRange("2026-08-01", "2026-08-01"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("rejects an end date before the start date")
        void rejectsInvertedRange() {
            assertThatThrownBy(() -> opportunityService
                    .getOpenOpportunitiesByDateRange("2026-08-31", "2026-08-01"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("endDate must be on or after startDate");

            verifyNoInteractions(opportunityRepository);
        }

        @ParameterizedTest(name = "rejects malformed date \"{0}\"")
        @ValueSource(strings = {"08-01-2026", "2026/08/01", "not-a-date", "2026-13-01", "2026-02-30"})
        @DisplayName("rejects dates that are not ISO yyyy-MM-dd")
        void rejectsMalformedDates(String badDate) {
            assertThatThrownBy(() -> opportunityService
                    .getOpenOpportunitiesByDateRange(badDate, "2026-08-31"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("startDate and endDate must be valid dates in YYYY-MM-DD format");

            verifyNoInteractions(opportunityRepository);
        }

        @ParameterizedTest(name = "skips validation when startDate is \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("skips validation entirely when one bound is missing")
        void skipsValidationWhenBoundMissing(String startDate) {
            when(opportunityRepository.findOpenOpportunitiesByDateRange(startDate, "not-a-date"))
                    .thenReturn(List.of());

            assertThatCode(() -> opportunityService
                    .getOpenOpportunitiesByDateRange(startDate, "not-a-date"))
                    .doesNotThrowAnyException();

            verify(opportunityRepository).findOpenOpportunitiesByDateRange(startDate, "not-a-date");
        }
    }

    @Nested
    @DisplayName("getOpenOpportunitiesWithFilters")
    class FilteredSearch {

        @Test
        @DisplayName("forwards all five filter arguments in order")
        void forwardsAllFilters() {
            List<Opportunity> expected = List.of(opportunity(OPPORTUNITY_ID, "OPEN", 10, 0));
            when(opportunityRepository.findOpenOpportunitiesWithFilters(
                    "ENVIRONMENT", "Seattle", ORG_ID, "2026-08-01", "2026-08-31"))
                    .thenReturn(expected);

            assertThat(opportunityService.getOpenOpportunitiesWithFilters(
                    "ENVIRONMENT", "Seattle", ORG_ID, "2026-08-01", "2026-08-31"))
                    .isSameAs(expected);
        }

        @Test
        @DisplayName("forwards null filters untouched")
        void forwardsNullFilters() {
            when(opportunityRepository.findOpenOpportunitiesWithFilters(null, null, null, null, null))
                    .thenReturn(List.of());

            assertThat(opportunityService
                    .getOpenOpportunitiesWithFilters(null, null, null, null, null))
                    .isEmpty();
        }

        @Test
        @DisplayName("validates the date range before querying")
        void validatesDatesBeforeQuerying() {
            assertThatThrownBy(() -> opportunityService.getOpenOpportunitiesWithFilters(
                    "ENVIRONMENT", "Seattle", ORG_ID, "2026-08-31", "2026-08-01"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("endDate must be on or after startDate");

            verifyNoInteractions(opportunityRepository);
        }
    }

    @Nested
    @DisplayName("createOpportunity")
    class CreateOpportunity {

        @Test
        @DisplayName("forces status OPEN, zero registrations and availableSpots equal to capacity")
        void derivesInitialState() {
            CreateOpportunityRequest request = new CreateOpportunityRequest(
                    OPPORTUNITY_ID, "Beach Cleanup", "Pick up litter", "ENVIRONMENT",
                    "Seattle, WA", "2026-08-01", 25, "09:00", "12:00", List.of("Sort and organize donations", "Help set up the distribution area"), false);
            when(opportunityRepository.save(any(Opportunity.class)))
                    .thenAnswer(call -> call.getArgument(0));

            Opportunity result =
                    opportunityService.createOpportunity(ORG_ID, ORG_NAME, request);

            ArgumentCaptor<Opportunity> captor = ArgumentCaptor.forClass(Opportunity.class);
            verify(opportunityRepository).save(captor.capture());

            assertThat(captor.getValue()).isEqualTo(new Opportunity(
                    OPPORTUNITY_ID, "Beach Cleanup", "Pick up litter", "ENVIRONMENT",
                    "Seattle, WA", "2026-08-01", "OPEN", ORG_ID, ORG_NAME, 25, 0, 25,
                    null, "09:00", "12:00", List.of("Sort and organize donations", "Help set up the distribution area"), false));
            assertThat(result).isEqualTo(captor.getValue());
        }

        @Test
        @DisplayName("takes organization id and name from the caller, not the request")
        void usesCallerOrganizationDetails() {
            CreateOpportunityRequest request = new CreateOpportunityRequest(
                    OPPORTUNITY_ID, "Beach Cleanup", "Pick up litter", "ENVIRONMENT",
                    "Seattle, WA", "2026-08-01", 25, "09:00", "12:00", List.of("Sort and organize donations", "Help set up the distribution area"), false);
            when(opportunityRepository.save(any(Opportunity.class)))
                    .thenAnswer(call -> call.getArgument(0));

            Opportunity result =
                    opportunityService.createOpportunity("org-99", "Other Org", request);

            assertThat(result.organizationId()).isEqualTo("org-99");
            assertThat(result.organizationName()).isEqualTo("Other Org");
        }

        @Test
        @DisplayName("does not validate the date on create")
        void doesNotValidateDate() {
            CreateOpportunityRequest request = new CreateOpportunityRequest(
                    OPPORTUNITY_ID, "Beach Cleanup", "Pick up litter", "ENVIRONMENT",
                    "Seattle, WA", "not-a-date", 25, "09:00", "12:00", List.of("Sort and organize donations", "Help set up the distribution area"), false);
            when(opportunityRepository.save(any(Opportunity.class)))
                    .thenAnswer(call -> call.getArgument(0));

            assertThatCode(() -> opportunityService.createOpportunity(ORG_ID, ORG_NAME, request))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("passes request.startTime() and request.endTime() into the saved Opportunity")
        void passesTimeThrough() {
            CreateOpportunityRequest request = new CreateOpportunityRequest(
                    OPPORTUNITY_ID, "Beach Cleanup", "Pick up litter", "ENVIRONMENT",
                    "Seattle, WA", "2026-08-01", 25, "14:30", "16:00", List.of("Sort and organize donations", "Help set up the distribution area"), false);
            when(opportunityRepository.save(any(Opportunity.class)))
                    .thenAnswer(call -> call.getArgument(0));

            Opportunity result = opportunityService.createOpportunity(ORG_ID, ORG_NAME, request);

            assertThat(result.startTime()).isEqualTo("14:30");
            assertThat(result.endTime()).isEqualTo("16:00");
        }

        @Test
        @DisplayName("leaves the legacy time field null instead of inventing a combined string")
        void leavesLegacyTimeNull() {
            CreateOpportunityRequest request = new CreateOpportunityRequest(
                    OPPORTUNITY_ID, "Beach Cleanup", "Pick up litter", "ENVIRONMENT",
                    "Seattle, WA", "2026-08-01", 25, "09:00", "12:00", List.of("Sort and organize donations", "Help set up the distribution area"), false);
            when(opportunityRepository.save(any(Opportunity.class)))
                    .thenAnswer(call -> call.getArgument(0));

            Opportunity result = opportunityService.createOpportunity(ORG_ID, ORG_NAME, request);

            assertThat(result.time()).isNull();
        }
    }

    @Nested
    @DisplayName("updateOpportunity")
    class UpdateOpportunity {

        @Test
        @DisplayName("preserves organization details and registered count from the stored record")
        void preservesImmutableFields() {
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity(OPPORTUNITY_ID, "OPEN", 10, 4)));
            when(opportunityRepository.update(any(Opportunity.class)))
                    .thenAnswer(call -> call.getArgument(0));

            UpdateOpportunityRequest request = new UpdateOpportunityRequest(
                    "New Title", "New description", "EDUCATION", "Tacoma, WA",
                    "2026-09-01", "OPEN", 20, "09:00", "12:00", List.of("Sort and organize donations", "Help set up the distribution area"), false);

            Opportunity result = opportunityService.updateOpportunity(OPPORTUNITY_ID, request);

            assertThat(result).isEqualTo(new Opportunity(
                    OPPORTUNITY_ID, "New Title", "New description", "EDUCATION", "Tacoma, WA",
                    "2026-09-01", "OPEN", ORG_ID, ORG_NAME, 20, 4, 16, null, "09:00", "12:00", List.of("Sort and organize donations", "Help set up the distribution area"), false));
        }

        @Test
        @DisplayName("reads the existing record before writing the update")
        void readsBeforeWriting() {
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity(OPPORTUNITY_ID, "OPEN", 10, 4)));
            when(opportunityRepository.update(any(Opportunity.class)))
                    .thenAnswer(call -> call.getArgument(0));

            opportunityService.updateOpportunity(OPPORTUNITY_ID, new UpdateOpportunityRequest(
                    "New Title", "New description", "EDUCATION", "Tacoma, WA",
                    "2026-09-01", "OPEN", 20, "09:00", "12:00", List.of("Sort and organize donations", "Help set up the distribution area"), false));

            InOrder inOrder = inOrder(opportunityRepository);
            inOrder.verify(opportunityRepository).findById(OPPORTUNITY_ID);
            inOrder.verify(opportunityRepository).update(any(Opportunity.class));
        }

        @ParameterizedTest(name = "capacity {0} with {1} registered leaves {2} available")
        @CsvSource({"20, 4, 16", "10, 10, 0", "5, 8, -3"})
        @DisplayName("recomputes availableSpots as capacity minus registeredCount")
        void recomputesAvailableSpots(int newCapacity, int registeredCount, int expectedAvailable) {
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity(OPPORTUNITY_ID, "OPEN", 10, registeredCount)));
            when(opportunityRepository.update(any(Opportunity.class)))
                    .thenAnswer(call -> call.getArgument(0));

            Opportunity result = opportunityService.updateOpportunity(
                    OPPORTUNITY_ID,
                    new UpdateOpportunityRequest("Beach Cleanup", "Pick up litter", "ENVIRONMENT",
                            "Seattle, WA", "2026-08-01", "OPEN", newCapacity, "09:00", "12:00", List.of("Sort and organize donations", "Help set up the distribution area"), false));

            assertThat(result.registeredCount()).isEqualTo(registeredCount);
            assertThat(result.availableSpots()).isEqualTo(expectedAvailable);
        }

        @ParameterizedTest(name = "storedStatus={0}, requestedStatus={1} -> status remains {0}")
        @CsvSource({"OPEN, CLOSED", "CLOSED, OPEN", "OPEN, OPEN", "CLOSED, CLOSED"})
        @DisplayName("always preserves the stored status, ignoring the status field in the request")
        void preservesStoredStatusRegardlessOfRequest(String storedStatus, String requestedStatus) {
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity(OPPORTUNITY_ID, storedStatus, 10, 4)));
            when(opportunityRepository.update(any(Opportunity.class)))
                    .thenAnswer(call -> call.getArgument(0));

            Opportunity result = opportunityService.updateOpportunity(
                    OPPORTUNITY_ID,
                    new UpdateOpportunityRequest("Beach Cleanup", "Pick up litter", "ENVIRONMENT",
                            "Seattle, WA", "2026-08-01", requestedStatus, 10, "09:00", "12:00", List.of("Sort and organize donations", "Help set up the distribution area"), false));

            assertThat(result.status()).isEqualTo(storedStatus);
        }

        @Test
        @DisplayName("cannot be used to close an opportunity in place of the dedicated close endpoint")
        void cannotCloseThroughGeneralUpdate() {
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity(OPPORTUNITY_ID, "OPEN", 10, 4)));
            when(opportunityRepository.update(any(Opportunity.class)))
                    .thenAnswer(call -> call.getArgument(0));

            Opportunity result = opportunityService.updateOpportunity(
                    OPPORTUNITY_ID,
                    new UpdateOpportunityRequest("Beach Cleanup", "Pick up litter", "ENVIRONMENT",
                            "Seattle, WA", "2026-08-01", "CLOSED", 10, "09:00", "12:00", List.of("Sort and organize donations", "Help set up the distribution area"), false));

            assertThat(result.status()).isEqualTo("OPEN");

            ArgumentCaptor<Opportunity> captor = ArgumentCaptor.forClass(Opportunity.class);
            verify(opportunityRepository).update(captor.capture());
            assertThat(captor.getValue().status()).isEqualTo("OPEN");
        }

        @Test
        @DisplayName("throws NotFoundException and skips the write when the opportunity is absent")
        void throwsWhenMissing() {
            when(opportunityRepository.findById(OPPORTUNITY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> opportunityService.updateOpportunity(
                    OPPORTUNITY_ID,
                    new UpdateOpportunityRequest("New Title", "New description", "EDUCATION",
                            "Tacoma, WA", "2026-09-01", "OPEN", 20, "09:00", "12:00", List.of("Sort and organize donations", "Help set up the distribution area"), false)))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Opportunity not found: " + OPPORTUNITY_ID);

            verify(opportunityRepository, never()).update(any());
        }

        @Test
        @DisplayName("passes request.startTime() and request.endTime() into the updated Opportunity")
        void passesTimeThrough() {
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity(OPPORTUNITY_ID, "OPEN", 10, 4)));
            when(opportunityRepository.update(any(Opportunity.class)))
                    .thenAnswer(call -> call.getArgument(0));

            UpdateOpportunityRequest request = new UpdateOpportunityRequest(
                    "New Title", "New description", "EDUCATION", "Tacoma, WA",
                    "2026-09-01", "OPEN", 20, "16:45", "18:00", List.of("Sort and organize donations", "Help set up the distribution area"), false);

            Opportunity result = opportunityService.updateOpportunity(OPPORTUNITY_ID, request);

            assertThat(result.startTime()).isEqualTo("16:45");
            assertThat(result.endTime()).isEqualTo("18:00");
        }

        @Test
        @DisplayName("clears the legacy time field instead of carrying stale text forward")
        void clearsLegacyTimeField() {
            Opportunity existingWithLegacyTime = new Opportunity(
                    OPPORTUNITY_ID, "Beach Cleanup", "Pick up litter", "ENVIRONMENT",
                    "Seattle, WA", "2026-08-01", "OPEN", ORG_ID, ORG_NAME, 10, 4, 6,
                    "9:00 AM - 1:00 PM", null, null, List.of(), false);
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(existingWithLegacyTime));
            when(opportunityRepository.update(any(Opportunity.class)))
                    .thenAnswer(call -> call.getArgument(0));

            UpdateOpportunityRequest request = new UpdateOpportunityRequest(
                    "New Title", "New description", "EDUCATION", "Tacoma, WA",
                    "2026-09-01", "OPEN", 20, "09:00", "12:00", List.of("Sort and organize donations", "Help set up the distribution area"), false);

            Opportunity result = opportunityService.updateOpportunity(OPPORTUNITY_ID, request);

            assertThat(result.time()).isNull();
        }
    }

    @Nested
    @DisplayName("imageKey")
    class ImageKey {

        @Test
        @DisplayName("updateOpportunity carries the existing image key forward")
        void carriesImageKeyForward() {
            Opportunity existing = withImageKey(opportunity(OPPORTUNITY_ID, "OPEN", 10, 3), IMAGE_KEY);
            when(opportunityRepository.findById(OPPORTUNITY_ID)).thenReturn(Optional.of(existing));
            when(opportunityRepository.update(any(Opportunity.class)))
                    .thenAnswer(call -> call.getArgument(0));

            opportunityService.updateOpportunity(OPPORTUNITY_ID, updateRequest());

            ArgumentCaptor<Opportunity> captor = ArgumentCaptor.forClass(Opportunity.class);
            verify(opportunityRepository).update(captor.capture());

            assertThat(captor.getValue().imageKey()).isEqualTo(IMAGE_KEY);
        }

        @Test
        @DisplayName("updateOpportunity leaves the image key null when there was none")
        void leavesImageKeyNull() {
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity(OPPORTUNITY_ID, "OPEN", 10, 3)));
            when(opportunityRepository.update(any(Opportunity.class)))
                    .thenAnswer(call -> call.getArgument(0));

            opportunityService.updateOpportunity(OPPORTUNITY_ID, updateRequest());

            ArgumentCaptor<Opportunity> captor = ArgumentCaptor.forClass(Opportunity.class);
            verify(opportunityRepository).update(captor.capture());

            assertThat(captor.getValue().imageKey()).isNull();
        }

        @Test
        @DisplayName("updateOpportunity still applies the requested field changes")
        void stillAppliesRequestedChanges() {
            Opportunity existing = withImageKey(opportunity(OPPORTUNITY_ID, "OPEN", 10, 3), IMAGE_KEY);
            when(opportunityRepository.findById(OPPORTUNITY_ID)).thenReturn(Optional.of(existing));
            when(opportunityRepository.update(any(Opportunity.class)))
                    .thenAnswer(call -> call.getArgument(0));

            opportunityService.updateOpportunity(OPPORTUNITY_ID, updateRequest());

            ArgumentCaptor<Opportunity> captor = ArgumentCaptor.forClass(Opportunity.class);
            verify(opportunityRepository).update(captor.capture());

            assertThat(captor.getValue().title()).isEqualTo("Updated Title");
            assertThat(captor.getValue().imageKey()).isEqualTo(IMAGE_KEY);
        }

        private UpdateOpportunityRequest updateRequest() {
            return new UpdateOpportunityRequest(
                    "Updated Title", "Updated description", "ENVIRONMENT", "Seattle, WA",
                    "2026-09-01", "OPEN", 12, "09:00", "12:00", List.of("Do things"), false);
        }
    }

    @Nested
    @DisplayName("time field validation")
    class TimeFieldValidation {

        @Test
        @DisplayName("CreateOpportunityRequest rejects a blank startTime, which Spring maps to 400 Bad Request")
        void blankCreateStartTimeFailsValidation() {
            CreateOpportunityRequest request = new CreateOpportunityRequest(
                    OPPORTUNITY_ID, "Beach Cleanup", "Pick up litter", "ENVIRONMENT",
                    "Seattle, WA", "2026-08-01", 25, " ", "12:00",
                    List.of("Sort and organize donations"), false);

            Set<ConstraintViolation<CreateOpportunityRequest>> violations = validator().validate(request);

            assertThat(violations)
                    .anyMatch(v -> v.getPropertyPath().toString().equals("startTime"));
        }

        @Test
        @DisplayName("CreateOpportunityRequest rejects a blank endTime, which Spring maps to 400 Bad Request")
        void blankCreateEndTimeFailsValidation() {
            CreateOpportunityRequest request = new CreateOpportunityRequest(
                    OPPORTUNITY_ID, "Beach Cleanup", "Pick up litter", "ENVIRONMENT",
                    "Seattle, WA", "2026-08-01", 25, "09:00", " ",
                    List.of("Sort and organize donations"), false);

            Set<ConstraintViolation<CreateOpportunityRequest>> violations = validator().validate(request);

            assertThat(violations)
                    .anyMatch(v -> v.getPropertyPath().toString().equals("endTime"));
        }

        @Test
        @DisplayName("UpdateOpportunityRequest rejects a blank startTime, which Spring maps to 400 Bad Request")
        void blankUpdateStartTimeFailsValidation() {
            UpdateOpportunityRequest request = new UpdateOpportunityRequest(
                    "New Title", "New description", "EDUCATION", "Tacoma, WA",
                    "2026-09-01", "OPEN", 20, " ", "12:00",
                    List.of("Sort and organize donations"), false);

            Set<ConstraintViolation<UpdateOpportunityRequest>> violations = validator().validate(request);

            assertThat(violations)
                    .anyMatch(v -> v.getPropertyPath().toString().equals("startTime"));
        }

        @Test
        @DisplayName("UpdateOpportunityRequest rejects a blank endTime, which Spring maps to 400 Bad Request")
        void blankUpdateEndTimeFailsValidation() {
            UpdateOpportunityRequest request = new UpdateOpportunityRequest(
                    "New Title", "New description", "EDUCATION", "Tacoma, WA",
                    "2026-09-01", "OPEN", 20, "09:00", " ",
                    List.of("Sort and organize donations"), false);

            Set<ConstraintViolation<UpdateOpportunityRequest>> violations = validator().validate(request);

            assertThat(violations)
                    .anyMatch(v -> v.getPropertyPath().toString().equals("endTime"));
        }

        private Validator validator() {
            return Validation.buildDefaultValidatorFactory().getValidator();
        }
    }

    @Nested
    @DisplayName("time range validation")
    class TimeRangeValidation {

        @Test
        @DisplayName("createOpportunity accepts a valid range where endTime is later than startTime")
        void createAcceptsValidRange() {
            CreateOpportunityRequest request = new CreateOpportunityRequest(
                    OPPORTUNITY_ID, "Beach Cleanup", "Pick up litter", "ENVIRONMENT",
                    "Seattle, WA", "2026-08-01", 25, "09:00", "12:00",
                    List.of("Sort and organize donations"), false);
            when(opportunityRepository.save(any(Opportunity.class)))
                    .thenAnswer(call -> call.getArgument(0));

            assertThatCode(() -> opportunityService.createOpportunity(ORG_ID, ORG_NAME, request))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("createOpportunity rejects an endTime equal to startTime")
        void createRejectsEqualTimes() {
            CreateOpportunityRequest request = new CreateOpportunityRequest(
                    OPPORTUNITY_ID, "Beach Cleanup", "Pick up litter", "ENVIRONMENT",
                    "Seattle, WA", "2026-08-01", 25, "09:00", "09:00",
                    List.of("Sort and organize donations"), false);

            assertThatThrownBy(() -> opportunityService.createOpportunity(ORG_ID, ORG_NAME, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("End time must be later than start time.");

            verifyNoInteractions(opportunityRepository);
        }

        @Test
        @DisplayName("createOpportunity rejects an endTime earlier than startTime")
        void createRejectsInvertedRange() {
            CreateOpportunityRequest request = new CreateOpportunityRequest(
                    OPPORTUNITY_ID, "Beach Cleanup", "Pick up litter", "ENVIRONMENT",
                    "Seattle, WA", "2026-08-01", 25, "12:00", "09:00",
                    List.of("Sort and organize donations"), false);

            assertThatThrownBy(() -> opportunityService.createOpportunity(ORG_ID, ORG_NAME, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("End time must be later than start time.");

            verifyNoInteractions(opportunityRepository);
        }

        @Test
        @DisplayName("updateOpportunity accepts a valid range where endTime is later than startTime")
        void updateAcceptsValidRange() {
            when(opportunityRepository.findById(OPPORTUNITY_ID))
                    .thenReturn(Optional.of(opportunity(OPPORTUNITY_ID, "OPEN", 10, 4)));
            when(opportunityRepository.update(any(Opportunity.class)))
                    .thenAnswer(call -> call.getArgument(0));

            UpdateOpportunityRequest request = new UpdateOpportunityRequest(
                    "Beach Cleanup", "Pick up litter", "ENVIRONMENT", "Seattle, WA",
                    "2026-08-01", "OPEN", 10, "09:00", "12:00",
                    List.of("Sort and organize donations"), false);

            assertThatCode(() -> opportunityService.updateOpportunity(OPPORTUNITY_ID, request))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("updateOpportunity rejects an endTime equal to startTime and never reads or writes the record")
        void updateRejectsEqualTimes() {
            UpdateOpportunityRequest request = new UpdateOpportunityRequest(
                    "Beach Cleanup", "Pick up litter", "ENVIRONMENT", "Seattle, WA",
                    "2026-08-01", "OPEN", 10, "09:00", "09:00",
                    List.of("Sort and organize donations"), false);

            assertThatThrownBy(() -> opportunityService.updateOpportunity(OPPORTUNITY_ID, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("End time must be later than start time.");

            verifyNoInteractions(opportunityRepository);
        }

        @Test
        @DisplayName("updateOpportunity rejects an endTime earlier than startTime and never reads or writes the record")
        void updateRejectsInvertedRange() {
            UpdateOpportunityRequest request = new UpdateOpportunityRequest(
                    "Beach Cleanup", "Pick up litter", "ENVIRONMENT", "Seattle, WA",
                    "2026-08-01", "OPEN", 10, "12:00", "09:00",
                    List.of("Sort and organize donations"), false);

            assertThatThrownBy(() -> opportunityService.updateOpportunity(OPPORTUNITY_ID, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("End time must be later than start time.");

            verifyNoInteractions(opportunityRepository);
        }
    }

    private static final String IMAGE_KEY =
            "organizations/org-1/opportunities/550e8400-e29b-41d4-a716-446655440000.jpg";

    private static Opportunity withImageKey(Opportunity opportunity, String imageKey) {
        return new Opportunity(
                opportunity.opportunityId(), opportunity.title(), opportunity.description(),
                opportunity.category(), opportunity.location(), opportunity.date(),
                opportunity.status(), opportunity.organizationId(), opportunity.organizationName(),
                opportunity.capacity(), opportunity.registeredCount(), opportunity.availableSpots(),
                opportunity.time(), opportunity.startTime(), opportunity.endTime(),
                opportunity.whatYoullDo(), opportunity.recurring(), imageKey);
    }

    private static Opportunity opportunity(
            String opportunityId, String status, int capacity, int registeredCount) {

        return opportunity(opportunityId, status, capacity, registeredCount, "2026-08-01");
    }

    private static Opportunity opportunity(
            String opportunityId, String status, int capacity, int registeredCount, String date) {

        return new Opportunity(
                opportunityId,
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
                null, "09:00", "12:00", List.of("Sort and organize donations", "Help set up the distribution area"), false);
    }
}
