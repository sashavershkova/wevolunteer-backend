package com.wevolunteer.backend.controller;

import com.wevolunteer.backend.model.Opportunity;
import com.wevolunteer.backend.model.Organization;
import com.wevolunteer.backend.model.Registration;
import com.wevolunteer.backend.model.Waitlist;
import com.wevolunteer.backend.service.OpportunityResponseMapper;
import com.wevolunteer.backend.service.OpportunityService;
import com.wevolunteer.backend.service.ProfileResponseMapper;
import com.wevolunteer.backend.service.OrganizationService;
import com.wevolunteer.backend.service.RegistrationService;
import com.wevolunteer.backend.service.WaitlistService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.wevolunteer.backend.dto.CreateOrganizationRequest;
import com.wevolunteer.backend.dto.OpportunityResponse;
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
    private final WaitlistService waitlistService;
    private final OpportunityResponseMapper opportunityResponseMapper;
    private final ProfileResponseMapper profileResponseMapper;

    public OrganizationController(
            OrganizationService organizationService,
            OpportunityService opportunityService,
            RegistrationService registrationService,
            WaitlistService waitlistService,
            OpportunityResponseMapper opportunityResponseMapper,
            ProfileResponseMapper profileResponseMapper) {
        this.organizationService = organizationService;
        this.opportunityService = opportunityService;
        this.registrationService = registrationService;
        this.waitlistService = waitlistService;
        this.opportunityResponseMapper = opportunityResponseMapper;
        this.profileResponseMapper = profileResponseMapper;
    }

    @GetMapping("/organizations/me")
    public OrganizationProfileResponse getCurrentOrganization(
            @AuthenticationPrincipal Jwt jwt) {

        return profileResponseMapper.toResponse(
                organizationService.getById(jwt.getSubject()));
    }

    @GetMapping("/organizations/{organizationId}")
    public OrganizationProfileResponse getOrganization(
            @PathVariable String organizationId) {

        return profileResponseMapper.toResponse(
                organizationService.getById(organizationId));
    }

    @GetMapping("/organizations/me/opportunities")
    public List<OpportunityResponse> getMyOrganizationOpportunities(
            @AuthenticationPrincipal Jwt jwt) {

        return opportunityResponseMapper.toResponses(opportunityService.getAllOpportunitiesByOrganizationId(jwt.getSubject()));
    }

    @GetMapping("/organizations/{organizationId}/opportunities")
    public List<OpportunityResponse> getOrganizationOpportunities(
            @PathVariable String organizationId,
            @RequestParam(required = false) String status) {

        if (status != null && !status.isBlank()) {
            return opportunityResponseMapper.toResponses(opportunityService.getOpportunitiesByOrganizationIdAndStatus(
                    organizationId,
                    status
            ));
        }

        return opportunityResponseMapper.toResponses(opportunityService.getAllOpportunitiesByOrganizationId(organizationId));
    }

    @PostMapping("/organizations")
    public OrganizationProfileResponse createOrganization(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateOrganizationRequest request) {

        return profileResponseMapper.toResponse(
                organizationService.createOrganization(jwt.getSubject(), request));
    }

    @PatchMapping("/organizations/me")
    public OrganizationProfileResponse updateCurrentOrganization(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateOrganizationRequest request) {

        return profileResponseMapper.toResponse(
                organizationService.updateOrganization(jwt.getSubject(), request));
    }

    @PatchMapping("/organizations/{organizationId}")
    public OrganizationProfileResponse updateOrganization(
            @PathVariable String organizationId,
            @Valid @RequestBody UpdateOrganizationRequest request) {

        return profileResponseMapper.toResponse(
                organizationService.updateOrganization(organizationId, request));
    }

    @DeleteMapping("/organizations/{organizationId}")
    public void deleteOrganization(@PathVariable String organizationId) {
        organizationService.deleteOrganization(organizationId);
    }

    @PostMapping("/organizations/me/opportunities")
    public OpportunityResponse createOpportunity(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateOpportunityRequest request) {

        String organizationId = jwt.getSubject();
        Organization organization = organizationService.getById(organizationId);

        return opportunityResponseMapper.toResponse(opportunityService.createOpportunity(
                organizationId,
                organization.name(),
                request
        ));
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

    @GetMapping("/organizations/me/opportunities/{opportunityId}/waitlist")
    public List<Waitlist> getOrganizationOpportunityWaitlist(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String opportunityId) {

        return waitlistService.getWaitlistForOrganizationOpportunity(
                opportunityId, jwt.getSubject());
    }
}