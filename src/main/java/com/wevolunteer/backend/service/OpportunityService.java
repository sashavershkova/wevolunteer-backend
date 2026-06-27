package com.wevolunteer.backend.service;

import com.wevolunteer.backend.model.Opportunity;
import com.wevolunteer.backend.repository.OpportunityRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OpportunityService {

    private final OpportunityRepository opportunityRepository;

    public OpportunityService(OpportunityRepository opportunityRepository) {
        this.opportunityRepository = opportunityRepository;
    }

    public Opportunity getById(String opportunityId) {
        return opportunityRepository.findById(opportunityId)
                .orElseThrow(() -> new RuntimeException("Opportunity not found: " + opportunityId));
    }

    public List<Opportunity> getOpenOpportunities() {
        return opportunityRepository.findOpenOpportunities();
    }

    public List<Opportunity> getOpportunitiesByCategory(String category) {
        return opportunityRepository.findByCategory(category);
    }

    public List<Opportunity> getOpportunitiesByLocation(String location) {
        return opportunityRepository.findByLocation(location);
    }

    public List<Opportunity> getOpportunitiesByOrganizationId(String organizationId) {
        return opportunityRepository.findByOrganizationId(organizationId);
    }

    public List<Opportunity> getOpenOpportunitiesByDateRange(String startDate, String endDate) {
        return opportunityRepository.findOpenOpportunitiesByDateRange(startDate, endDate);
    }

    public List<Opportunity> getAllOpportunitiesByOrganizationId(String organizationId) {
        return opportunityRepository.findAllByOrganizationId(organizationId);
    }

    public List<Opportunity> getOpenOpportunitiesWithFilters(
            String category,
            String location,
            String organizationId,
            String startDate,
            String endDate) {

        return opportunityRepository.findOpenOpportunitiesWithFilters(
                category,
                location,
                organizationId,
                startDate,
                endDate
        );
    }
}