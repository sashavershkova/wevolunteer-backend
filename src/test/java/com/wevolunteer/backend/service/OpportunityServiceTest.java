package com.wevolunteer.backend.service;

import com.wevolunteer.backend.dto.CreateOpportunityRequest;
import com.wevolunteer.backend.dto.UpdateOpportunityRequest;
import com.wevolunteer.backend.exception.ForbiddenException;
import com.wevolunteer.backend.exception.NotFoundException;
import com.wevolunteer.backend.model.Opportunity;
import com.wevolunteer.backend.repository.OpportunityRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
        @DisplayName("deleteOpportunity delegates without checking existence first")
        void delegatesDelete() {
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
                    "Seattle, WA", "2026-08-01", 25);
            when(opportunityRepository.save(any(Opportunity.class)))
                    .thenAnswer(call -> call.getArgument(0));

            Opportunity result =
                    opportunityService.createOpportunity(ORG_ID, ORG_NAME, request);

            ArgumentCaptor<Opportunity> captor = ArgumentCaptor.forClass(Opportunity.class);
            verify(opportunityRepository).save(captor.capture());

            assertThat(captor.getValue()).isEqualTo(new Opportunity(
                    OPPORTUNITY_ID, "Beach Cleanup", "Pick up litter", "ENVIRONMENT",
                    "Seattle, WA", "2026-08-01", "OPEN", ORG_ID, ORG_NAME, 25, 0, 25));
            assertThat(result).isEqualTo(captor.getValue());
        }

        @Test
        @DisplayName("takes organization id and name from the caller, not the request")
        void usesCallerOrganizationDetails() {
            CreateOpportunityRequest request = new CreateOpportunityRequest(
                    OPPORTUNITY_ID, "Beach Cleanup", "Pick up litter", "ENVIRONMENT",
                    "Seattle, WA", "2026-08-01", 25);
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
                    "Seattle, WA", "not-a-date", 25);
            when(opportunityRepository.save(any(Opportunity.class)))
                    .thenAnswer(call -> call.getArgument(0));

            assertThatCode(() -> opportunityService.createOpportunity(ORG_ID, ORG_NAME, request))
                    .doesNotThrowAnyException();
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
                    "2026-09-01", "OPEN", 20);

            Opportunity result = opportunityService.updateOpportunity(OPPORTUNITY_ID, request);

            assertThat(result).isEqualTo(new Opportunity(
                    OPPORTUNITY_ID, "New Title", "New description", "EDUCATION", "Tacoma, WA",
                    "2026-09-01", "OPEN", ORG_ID, ORG_NAME, 20, 4, 16));
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
                    "2026-09-01", "OPEN", 20));

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
                            "Seattle, WA", "2026-08-01", "OPEN", newCapacity));

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
                            "Seattle, WA", "2026-08-01", requestedStatus, 10));

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
                            "Seattle, WA", "2026-08-01", "CLOSED", 10));

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
                            "Tacoma, WA", "2026-09-01", "OPEN", 20)))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Opportunity not found: " + OPPORTUNITY_ID);

            verify(opportunityRepository, never()).update(any());
        }
    }

    private static Opportunity opportunity(
            String opportunityId, String status, int capacity, int registeredCount) {

        return new Opportunity(
                opportunityId,
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
                capacity - registeredCount);
    }
}
