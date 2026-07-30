package com.wevolunteer.backend.controller;

import com.wevolunteer.backend.model.Opportunity;
import com.wevolunteer.backend.model.Organization;
import com.wevolunteer.backend.model.Registration;
import com.wevolunteer.backend.service.OpportunityService;
import com.wevolunteer.backend.service.OrganizationService;
import com.wevolunteer.backend.service.RegistrationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.wevolunteer.backend.dto.CreateOrganizationRequest;
import com.wevolunteer.backend.dto.OrganizationProfileResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.wevolunteer.backend.dto.UpdateOrganizationRequest;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import com.wevolunteer.backend.dto.CreateOpportunityRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;

@RestController
public class OrganizationController {

    private final OrganizationService organizationService;
    private final OpportunityService opportunityService;
    private final RegistrationService registrationService;

    public OrganizationController(
            OrganizationService organizationService,
            OpportunityService opportunityService,
            RegistrationService registrationService) {
        this.organizationService = organizationService;
        this.opportunityService = opportunityService;
        this.registrationService = registrationService;
    }

    @GetMapping("/organizations/me")
    public OrganizationProfileResponse getCurrentOrganization(
            @AuthenticationPrincipal Jwt jwt) {

        return OrganizationProfileResponse.from(
                organizationService.getById(jwt.getSubject()));
    }

    @GetMapping("/organizations/{organizationId}")
    public OrganizationProfileResponse getOrganization(
            @PathVariable String organizationId) {

        return OrganizationProfileResponse.from(
                organizationService.getById(organizationId));
    }

    @GetMapping("/organizations/me/opportunities")
    public List<Opportunity> getMyOrganizationOpportunities(
            @AuthenticationPrincipal Jwt jwt) {

        return opportunityService.getAllOpportunitiesByOrganizationId(jwt.getSubject());
    }

    @GetMapping("/organizations/{organizationId}/opportunities")
    public List<Opportunity> getOrganizationOpportunities(
            @PathVariable String organizationId,
            @RequestParam(required = false) String status) {

        if (status != null && !status.isBlank()) {
            return opportunityService.getOpportunitiesByOrganizationIdAndStatus(
                    organizationId,
                    status
            );
        }

        return opportunityService.getAllOpportunitiesByOrganizationId(organizationId);
    }

    @PostMapping("/organizations")
    public OrganizationProfileResponse createOrganization(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateOrganizationRequest request) {

        return OrganizationProfileResponse.from(
                organizationService.createOrganization(jwt.getSubject(), request));
    }

    @PatchMapping("/organizations/me")
    public OrganizationProfileResponse updateCurrentOrganization(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateOrganizationRequest request) {

        return OrganizationProfileResponse.from(
                organizationService.updateOrganization(jwt.getSubject(), request));
    }

    @PatchMapping("/organizations/{organizationId}")
    public OrganizationProfileResponse updateOrganization(
            @PathVariable String organizationId,
            @Valid @RequestBody UpdateOrganizationRequest request) {

        return OrganizationProfileResponse.from(
                organizationService.updateOrganization(organizationId, request));
    }

    @DeleteMapping("/organizations/{organizationId}")
    public void deleteOrganization(@PathVariable String organizationId) {
        organizationService.deleteOrganization(organizationId);
    }

    @PostMapping("/organizations/me/opportunities")
    public Opportunity createOpportunity(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateOpportunityRequest request) {

        String organizationId = jwt.getSubject();
        Organization organization = organizationService.getById(organizationId);

        return opportunityService.createOpportunity(
                organizationId,
                organization.name(),
                request
        );
    }

    @DeleteMapping("/organizations/me/opportunities/{opportunityId}")
    public void deleteOpportunity(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String opportunityId) {

        opportunityService.deleteOpportunity(opportunityId, jwt.getSubject());
    }

    @GetMapping("/organizations/me/opportunities/{opportunityId}/registrations")
    public List<Registration> getOrganizationOpportunityRegistrations(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String opportunityId) {

        return registrationService.getRegistrationsForOrganizationOpportunity(
                opportunityId, jwt.getSubject());
    }
}