package com.wevolunteer.backend.service;

import com.wevolunteer.backend.model.Opportunity;
import com.wevolunteer.backend.model.Organization;
import com.wevolunteer.backend.model.Registration;
import com.wevolunteer.backend.repository.OrganizationRepository;

import java.util.List;

import org.springframework.stereotype.Service;
import com.wevolunteer.backend.dto.CreateOrganizationRequest;
import com.wevolunteer.backend.dto.UpdateOrganizationRequest;

import java.util.List;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OpportunityService opportunityService;
    private final RegistrationService registrationService;

    public OrganizationService(
            OrganizationRepository organizationRepository,
            OpportunityService opportunityService,
            RegistrationService registrationService) {

        this.organizationRepository = organizationRepository;
        this.opportunityService = opportunityService;
        this.registrationService = registrationService;
    }

    public Organization getById(String organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found: " + organizationId));
    }

    public Organization createOrganization(CreateOrganizationRequest request) {
        Organization organization = new Organization(
                request.organizationId(),
                request.name(),
                request.description(),
                request.email(),
                request.website()
        );

        return organizationRepository.save(organization);
    }

    public Organization updateOrganization(
            String organizationId,
            UpdateOrganizationRequest request) {

        Organization organization = new Organization(
                organizationId,
                request.name(),
                request.description(),
                request.email(),
                request.website()
        );

        return organizationRepository.update(organization);
    }

    public void deleteOrganization(String organizationId) {

        List<Opportunity> opportunities =
                opportunityService.getAllOpportunitiesByOrganizationId(organizationId);

        for (Opportunity opportunity : opportunities) {

            List<Registration> registrations =
                    registrationService.getRegistrationsByOpportunityId(
                            opportunity.opportunityId());

            for (Registration registration : registrations) {
                registrationService.cancelRegistration(
                        registration.userId(),
                        opportunity.opportunityId()
                );
            }

            opportunityService.deleteOpportunity(
                    opportunity.opportunityId()
            );
        }

        organizationRepository.deleteById(organizationId);
    }
}