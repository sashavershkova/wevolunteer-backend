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
            @RequestParam(required = false) String category) {

        if (category != null && !category.isBlank()) {
            return opportunityService.getOpportunitiesByCategory(category);
        }

        return opportunityService.getOpenOpportunities();
    }
}