package com.wevolunteer.backend.service;

import com.wevolunteer.backend.model.Opportunity;
import com.wevolunteer.backend.repository.OpportunityRepository;
import org.springframework.stereotype.Service;
import com.wevolunteer.backend.dto.CreateOpportunityRequest;
import com.wevolunteer.backend.dto.UpdateOpportunityRequest;
import com.wevolunteer.backend.exception.NotFoundException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
public class OpportunityService {

    private final OpportunityRepository opportunityRepository;

    public OpportunityService(OpportunityRepository opportunityRepository) {
        this.opportunityRepository = opportunityRepository;
    }

    public Opportunity getById(String opportunityId) {
        return opportunityRepository.findById(opportunityId)
                .orElseThrow(() -> new NotFoundException("Opportunity not found: " + opportunityId));
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
        validateDateRange(startDate, endDate);
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

        validateDateRange(startDate, endDate);

        return opportunityRepository.findOpenOpportunitiesWithFilters(
                category,
                location,
                organizationId,
                startDate,
                endDate
        );
    }

    public List<Opportunity> getOpportunitiesByOrganizationIdAndStatus(
            String organizationId,
            String status) {

        return opportunityRepository.findByOrganizationIdAndStatus(
                organizationId,
                status
        );
    }

    public void deleteOpportunity(String opportunityId) {
        opportunityRepository.deleteById(opportunityId);
    }

    public Opportunity createOpportunity(
            String organizationId,
            String organizationName,
            CreateOpportunityRequest request) {

        Opportunity opportunity = new Opportunity(
                request.opportunityId(),
                request.title(),
                request.description(),
                request.category(),
                request.location(),
                request.date(),
                "OPEN",
                organizationId,
                organizationName,
                request.capacity(),
                0,
                request.capacity()
        );

        return opportunityRepository.save(opportunity);
    }

    public Opportunity updateOpportunity(
            String opportunityId,
            UpdateOpportunityRequest request) {

        Opportunity existingOpportunity = getById(opportunityId);

        Opportunity updatedOpportunity = new Opportunity(
                opportunityId,
                request.title(),
                request.description(),
                request.category(),
                request.location(),
                request.date(),
                request.status(),
                existingOpportunity.organizationId(),
                existingOpportunity.organizationName(),
                request.capacity(),
                existingOpportunity.registeredCount(),
                request.capacity() - existingOpportunity.registeredCount()
        );

        return opportunityRepository.update(updatedOpportunity);
    }

    public Opportunity closeOpportunity(String opportunityId) {
        return opportunityRepository.close(opportunityId);
    }

    private void validateDateRange(String startDate, String endDate) {
        if (startDate == null || startDate.isBlank() || endDate == null || endDate.isBlank()) {
            return;
        }

        LocalDate start;
        LocalDate end;
        try {
            start = LocalDate.parse(startDate);
            end = LocalDate.parse(endDate);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("startDate and endDate must be valid dates in YYYY-MM-DD format");
        }

        if (end.isBefore(start)) {
            throw new IllegalArgumentException("endDate must be on or after startDate");
        }
    }
}