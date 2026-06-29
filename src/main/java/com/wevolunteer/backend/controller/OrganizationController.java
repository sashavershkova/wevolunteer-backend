package com.wevolunteer.backend.controller;

import com.wevolunteer.backend.model.Opportunity;
import com.wevolunteer.backend.model.Organization;
import com.wevolunteer.backend.service.OpportunityService;
import com.wevolunteer.backend.service.OrganizationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.wevolunteer.backend.dto.CreateOrganizationRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.wevolunteer.backend.dto.UpdateOrganizationRequest;
import org.springframework.web.bind.annotation.PatchMapping;

import java.util.List;

@RestController
public class OrganizationController {

    private final OrganizationService organizationService;
    private final OpportunityService opportunityService;

    public OrganizationController(
            OrganizationService organizationService,
            OpportunityService opportunityService) {
        this.organizationService = organizationService;
        this.opportunityService = opportunityService;
    }

    @GetMapping("/organizations/{organizationId}")
    public Organization getOrganization(@PathVariable String organizationId) {
        return organizationService.getById(organizationId);
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
    public Organization createOrganization(
            @Valid @RequestBody CreateOrganizationRequest request) {

        return organizationService.createOrganization(request);
    }

    @PatchMapping("/organizations/{organizationId}")
    public Organization updateOrganization(
            @PathVariable String organizationId,
            @Valid @RequestBody UpdateOrganizationRequest request) {
        return organizationService.updateOrganization(organizationId, request);

}
}