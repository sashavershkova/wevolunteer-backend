package com.wevolunteer.backend.controller;

import com.wevolunteer.backend.model.Opportunity;
import com.wevolunteer.backend.service.OpportunityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class OrganizationController {

    private final OpportunityService opportunityService;

    public OrganizationController(OpportunityService opportunityService) {
        this.opportunityService = opportunityService;
    }

    @GetMapping("/organizations/{organizationId}/opportunities")
    public List<Opportunity> getOrganizationOpportunities(
            @PathVariable String organizationId) {

        return opportunityService.getAllOpportunitiesByOrganizationId(organizationId);
    }
}