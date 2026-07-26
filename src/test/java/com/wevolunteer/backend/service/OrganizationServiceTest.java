package com.wevolunteer.backend.service;

import com.wevolunteer.backend.dto.CreateOrganizationRequest;
import com.wevolunteer.backend.dto.UpdateOrganizationRequest;
import com.wevolunteer.backend.exception.NotFoundException;
import com.wevolunteer.backend.model.Opportunity;
import com.wevolunteer.backend.model.Organization;
import com.wevolunteer.backend.model.Registration;
import com.wevolunteer.backend.repository.OrganizationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrganizationService")
class OrganizationServiceTest {

    private static final String ORG_ID = "org-1";

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private OpportunityService opportunityService;

    @Mock
    private RegistrationService registrationService;

    @InjectMocks
    private OrganizationService organizationService;

    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        @DisplayName("returns the organization when the repository finds it")
        void returnsOrganizationWhenFound() {
            Organization organization = organization(ORG_ID);
            when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(organization));

            assertThat(organizationService.getById(ORG_ID)).isSameAs(organization);
        }

        @Test
        @DisplayName("throws NotFoundException with the id in the message when absent")
        void throwsWhenMissing() {
            when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> organizationService.getById(ORG_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Organization not found: " + ORG_ID);
        }
    }

    @Nested
    @DisplayName("createOrganization")
    class CreateOrganization {

        @Test
        @DisplayName("maps every request field onto the saved organization")
        void mapsRequestAndSaves() {
            CreateOrganizationRequest request = new CreateOrganizationRequest(
                    "Green Earth", "We clean beaches", "info@greenearth.org",
                    "https://greenearth.org");
            when(organizationRepository.save(any(Organization.class)))
                    .thenAnswer(call -> call.getArgument(0));

            Organization result = organizationService.createOrganization(ORG_ID, request);

            ArgumentCaptor<Organization> captor = ArgumentCaptor.forClass(Organization.class);
            verify(organizationRepository).save(captor.capture());

            assertThat(captor.getValue()).isEqualTo(new Organization(
                    ORG_ID, "Green Earth", "We clean beaches", "info@greenearth.org",
                    "https://greenearth.org"));
            assertThat(result).isEqualTo(captor.getValue());
        }

        @Test
        @DisplayName("passes optional description and website through as null")
        void allowsNullOptionalFields() {
            CreateOrganizationRequest request = new CreateOrganizationRequest(
                    "Green Earth", null, "info@greenearth.org", null);
            when(organizationRepository.save(any(Organization.class)))
                    .thenAnswer(call -> call.getArgument(0));

            Organization result = organizationService.createOrganization(ORG_ID, request);

            assertThat(result.description()).isNull();
            assertThat(result.website()).isNull();
        }

        @Test
        @DisplayName("does not check for an existing organization first")
        void doesNotCheckExistence() {
            CreateOrganizationRequest request = new CreateOrganizationRequest(
                    "Green Earth", null, "info@greenearth.org", null);
            when(organizationRepository.save(any(Organization.class)))
                    .thenAnswer(call -> call.getArgument(0));

            organizationService.createOrganization(ORG_ID, request);

            verify(organizationRepository, never()).findById(any());
        }
    }

    @Nested
    @DisplayName("updateOrganization")
    class UpdateOrganization {

        @Test
        @DisplayName("verifies the organization exists, then updates it with the request fields")
        void checksExistenceThenUpdates() {
            UpdateOrganizationRequest request = new UpdateOrganizationRequest(
                    "New Name", "New description", "new@greenearth.org", "https://new.org");
            when(organizationRepository.findById(ORG_ID))
                    .thenReturn(Optional.of(organization(ORG_ID)));
            when(organizationRepository.update(any(Organization.class)))
                    .thenAnswer(call -> call.getArgument(0));

            Organization result = organizationService.updateOrganization(ORG_ID, request);

            InOrder inOrder = inOrder(organizationRepository);
            inOrder.verify(organizationRepository).findById(ORG_ID);
            inOrder.verify(organizationRepository).update(any(Organization.class));

            assertThat(result).isEqualTo(new Organization(
                    ORG_ID, "New Name", "New description", "new@greenearth.org",
                    "https://new.org"));
        }

        @Test
        @DisplayName("keeps the path id rather than any id on the stored record")
        void keepsPathId() {
            UpdateOrganizationRequest request = new UpdateOrganizationRequest(
                    "New Name", null, "new@greenearth.org", null);
            when(organizationRepository.findById(ORG_ID))
                    .thenReturn(Optional.of(organization(ORG_ID)));
            when(organizationRepository.update(any(Organization.class)))
                    .thenAnswer(call -> call.getArgument(0));

            assertThat(organizationService.updateOrganization(ORG_ID, request).organizationId())
                    .isEqualTo(ORG_ID);
        }

        @Test
        @DisplayName("throws NotFoundException and skips the update when absent")
        void throwsWhenMissing() {
            UpdateOrganizationRequest request = new UpdateOrganizationRequest(
                    "New Name", null, "new@greenearth.org", null);
            when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> organizationService.updateOrganization(ORG_ID, request))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Organization not found: " + ORG_ID);

            verify(organizationRepository, never()).update(any());
        }
    }

    @Nested
    @DisplayName("deleteOrganization")
    class DeleteOrganization {

        @Test
        @DisplayName("cancels registrations, deletes opportunities, then deletes the organization")
        void cascadesInOrder() {
            Opportunity first = opportunity("opp-1");
            Opportunity second = opportunity("opp-2");

            when(organizationRepository.findById(ORG_ID))
                    .thenReturn(Optional.of(organization(ORG_ID)));
            when(opportunityService.getAllOpportunitiesByOrganizationId(ORG_ID))
                    .thenReturn(List.of(first, second));
            when(registrationService.getRegistrationsByOpportunityId("opp-1"))
                    .thenReturn(List.of(registration("user-1", "opp-1"), registration("user-2", "opp-1")));
            when(registrationService.getRegistrationsByOpportunityId("opp-2"))
                    .thenReturn(List.of(registration("user-3", "opp-2")));

            organizationService.deleteOrganization(ORG_ID);

            InOrder inOrder = inOrder(organizationRepository, opportunityService, registrationService);
            inOrder.verify(organizationRepository).findById(ORG_ID);
            inOrder.verify(opportunityService).getAllOpportunitiesByOrganizationId(ORG_ID);
            inOrder.verify(registrationService).getRegistrationsByOpportunityId("opp-1");
            inOrder.verify(registrationService).cancelRegistration("user-1", "opp-1");
            inOrder.verify(registrationService).cancelRegistration("user-2", "opp-1");
            inOrder.verify(opportunityService).deleteOpportunity("opp-1");
            inOrder.verify(registrationService).getRegistrationsByOpportunityId("opp-2");
            inOrder.verify(registrationService).cancelRegistration("user-3", "opp-2");
            inOrder.verify(opportunityService).deleteOpportunity("opp-2");
            inOrder.verify(organizationRepository).deleteById(ORG_ID);
            inOrder.verifyNoMoreInteractions();
        }

        @Test
        @DisplayName("deletes an opportunity that has no registrations")
        void deletesOpportunityWithoutRegistrations() {
            when(organizationRepository.findById(ORG_ID))
                    .thenReturn(Optional.of(organization(ORG_ID)));
            when(opportunityService.getAllOpportunitiesByOrganizationId(ORG_ID))
                    .thenReturn(List.of(opportunity("opp-1")));
            when(registrationService.getRegistrationsByOpportunityId("opp-1"))
                    .thenReturn(List.of());

            organizationService.deleteOrganization(ORG_ID);

            verify(registrationService, never()).cancelRegistration(any(), any());
            verify(opportunityService).deleteOpportunity("opp-1");
            verify(organizationRepository).deleteById(ORG_ID);
        }

        @Test
        @DisplayName("deletes the organization directly when it owns no opportunities")
        void deletesWhenNoOpportunities() {
            when(organizationRepository.findById(ORG_ID))
                    .thenReturn(Optional.of(organization(ORG_ID)));
            when(opportunityService.getAllOpportunitiesByOrganizationId(ORG_ID))
                    .thenReturn(List.of());

            organizationService.deleteOrganization(ORG_ID);

            verifyNoInteractions(registrationService);
            verify(opportunityService, never()).deleteOpportunity(any());
            verify(organizationRepository).deleteById(ORG_ID);
        }

        @Test
        @DisplayName("throws NotFoundException and touches nothing when the organization is absent")
        void throwsWhenMissing() {
            when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> organizationService.deleteOrganization(ORG_ID))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Organization not found: " + ORG_ID);

            verifyNoInteractions(opportunityService, registrationService);
            verify(organizationRepository, never()).deleteById(any());
        }

        @Test
        @DisplayName("stops the cascade and leaves the organization when an opportunity delete fails")
        void propagatesOpportunityDeleteFailure() {
            when(organizationRepository.findById(ORG_ID))
                    .thenReturn(Optional.of(organization(ORG_ID)));
            when(opportunityService.getAllOpportunitiesByOrganizationId(ORG_ID))
                    .thenReturn(List.of(opportunity("opp-1"), opportunity("opp-2")));
            when(registrationService.getRegistrationsByOpportunityId("opp-1"))
                    .thenReturn(List.of());
            org.mockito.Mockito.doThrow(new IllegalStateException("dynamo unavailable"))
                    .when(opportunityService).deleteOpportunity("opp-1");

            assertThatThrownBy(() -> organizationService.deleteOrganization(ORG_ID))
                    .isInstanceOf(IllegalStateException.class);

            verify(registrationService, never()).getRegistrationsByOpportunityId("opp-2");
            verify(organizationRepository, never()).deleteById(any());
        }
    }

    private static Organization organization(String organizationId) {
        return new Organization(
                organizationId,
                "Green Earth",
                "We clean beaches",
                "info@greenearth.org",
                "https://greenearth.org");
    }

    private static Opportunity opportunity(String opportunityId) {
        return new Opportunity(
                opportunityId,
                "Beach Cleanup",
                "Pick up litter",
                "ENVIRONMENT",
                "Seattle, WA",
                "2026-08-01",
                "OPEN",
                ORG_ID,
                "Green Earth",
                10,
                0,
                10);
    }

    private static Registration registration(String userId, String opportunityId) {
        return new Registration(
                userId,
                opportunityId,
                "Beach Cleanup",
                "2026-08-01",
                "Seattle, WA",
                ORG_ID,
                "Green Earth",
                "ACTIVE",
                "Volunteer Name",
                "volunteer@example.com",
                "2026-07-24T10:00:00");
    }
}
