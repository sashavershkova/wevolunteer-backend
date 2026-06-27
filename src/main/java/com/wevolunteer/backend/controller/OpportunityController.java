package com.wevolunteer.backend.controller;

import com.wevolunteer.backend.model.Opportunity;
import com.wevolunteer.backend.service.OpportunityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class OpportunityController {

    private final OpportunityService opportunityService;

    public OpportunityController(OpportunityService opportunityService) {
        this.opportunityService = opportunityService;
    }

    @GetMapping("/opportunities/{opportunityId}")
    public Opportunity getOpportunity(@PathVariable String opportunityId) {
        return opportunityService.getById(opportunityId);
    }

    @GetMapping("/opportunities")
    public List<Opportunity> getOpportunities(
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
            return opportunityService.getOpenOpportunitiesWithFilters(
                    category,
                    location,
                    organizationId,
                    startDate,
                    endDate
            );
        }

        if (hasCategory) {
            return opportunityService.getOpportunitiesByCategory(category);
        }

        if (hasLocation) {
            return opportunityService.getOpportunitiesByLocation(location);
        }

        if (hasOrganizationId) {
            return opportunityService.getOpportunitiesByOrganizationId(organizationId);
        }

        if (hasDateRange) {
            return opportunityService.getOpenOpportunitiesByDateRange(startDate, endDate);
        }

        return opportunityService.getOpenOpportunities();
    }
}