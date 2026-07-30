package com.wevolunteer.backend.controller;

import com.wevolunteer.backend.dto.OpportunityResponse;
import com.wevolunteer.backend.model.Opportunity;
import com.wevolunteer.backend.model.Registration;
import com.wevolunteer.backend.service.OpportunityResponseMapper;
import com.wevolunteer.backend.service.OpportunityService;
import com.wevolunteer.backend.service.RegistrationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.wevolunteer.backend.dto.UpdateOpportunityRequest;
import org.springframework.web.bind.annotation.PatchMapping;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

@RestController
public class OpportunityController {

    private final OpportunityService opportunityService;
    private final RegistrationService registrationService;
    private final OpportunityResponseMapper opportunityResponseMapper;

    public OpportunityController(
            OpportunityService opportunityService,
            RegistrationService registrationService,
            OpportunityResponseMapper opportunityResponseMapper) {
        this.opportunityService = opportunityService;
        this.registrationService = registrationService;
        this.opportunityResponseMapper = opportunityResponseMapper;
    }

    @GetMapping("/opportunities/{opportunityId}")
    public OpportunityResponse getOpportunity(@PathVariable String opportunityId) {
        return opportunityResponseMapper.toResponse(
                opportunityService.getById(opportunityId));
    }

    @GetMapping("/opportunities/{opportunityId}/registrations")
    public List<Registration> getOpportunityRegistrations(
            @PathVariable String opportunityId) {

        return registrationService.getRegistrationsByOpportunityId(opportunityId);
    }

    @GetMapping("/opportunities")
    public List<OpportunityResponse> getOpportunities(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String organizationId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        boolean hasCategory = category != null && !category.isBlank();
        boolean hasLocation = location != null && !location.isBlank();
        boolean hasOrganizationId = organizationId != null && !organizationId.isBlank();
        boolean hasDateRange = startDate != null && !startDate.isBlank()
                && endDate != null && !endDate.isBlank();

        int filterCount = 0;

        if (hasCategory) filterCount++;
        if (hasLocation) filterCount++;
        if (hasOrganizationId) filterCount++;
        if (hasDateRange) filterCount++;

        if (filterCount > 1) {
            return opportunityResponseMapper.toResponses(opportunityService.getOpenOpportunitiesWithFilters(
                    category,
                    location,
                    organizationId,
                    startDate,
                    endDate
            ));
        }

        if (hasCategory) {
            return opportunityResponseMapper.toResponses(opportunityService.getOpportunitiesByCategory(category));
        }

        if (hasLocation) {
            return opportunityResponseMapper.toResponses(opportunityService.getOpportunitiesByLocation(location));
        }

        if (hasOrganizationId) {
            return opportunityResponseMapper.toResponses(opportunityService.getOpportunitiesByOrganizationId(organizationId));
        }

        if (hasDateRange) {
            return opportunityResponseMapper.toResponses(opportunityService.getOpenOpportunitiesByDateRange(startDate, endDate));
        }

        return opportunityResponseMapper.toResponses(opportunityService.getOpenOpportunities());
    }

    @PatchMapping("/opportunities/{opportunityId}")
    public OpportunityResponse updateOpportunity(
            @PathVariable String opportunityId,
            @Valid @RequestBody UpdateOpportunityRequest request) {

        return opportunityResponseMapper.toResponse(
                opportunityService.updateOpportunity(opportunityId, request));
    }

    @PatchMapping("/opportunities/{opportunityId}/close")
    public OpportunityResponse closeOpportunity(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String opportunityId) {

        return opportunityResponseMapper.toResponse(
                opportunityService.closeOpportunity(opportunityId, jwt.getSubject()));
    }
}