package com.wevolunteer.backend.service;

import com.wevolunteer.backend.model.Opportunity;
import com.wevolunteer.backend.repository.OpportunityRepository;
import org.springframework.stereotype.Service;
import com.wevolunteer.backend.dto.CreateOpportunityRequest;
import com.wevolunteer.backend.dto.UpdateOpportunityRequest;
import com.wevolunteer.backend.exception.ForbiddenException;
import com.wevolunteer.backend.exception.NotFoundException;

import java.time.LocalDate;
import java.time.LocalTime;
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

        validateTimeRange(request.startTime(), request.endTime());

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
                request.capacity(),
                // Legacy free-text time is never invented for new opportunities; startTime/endTime
                // are the source of truth going forward.
                null,
                request.startTime(),
                request.endTime(),
                request.whatYoullDo(),
                request.recurring()
        );

        return opportunityRepository.save(opportunity);
    }

    public Opportunity updateOpportunity(
            String opportunityId,
            UpdateOpportunityRequest request) {

        validateTimeRange(request.startTime(), request.endTime());

        Opportunity existingOpportunity = getById(opportunityId);

        Opportunity updatedOpportunity = new Opportunity(
                opportunityId,
                request.title(),
                request.description(),
                request.category(),
                request.location(),
                request.date(),
                existingOpportunity.status(),
                existingOpportunity.organizationId(),
                existingOpportunity.organizationName(),
                request.capacity(),
                existingOpportunity.registeredCount(),
                request.capacity() - existingOpportunity.registeredCount(),
                // The edit now always supplies structured startTime/endTime, so the legacy
                // free-text time no longer describes the current opportunity - clear it rather
                // than carrying stale text forward.
                null,
                request.startTime(),
                request.endTime(),
                request.whatYoullDo(),
                request.recurring(),
                // update() rewrites the whole item, and UpdateOpportunityRequest carries no image
                // field, so the existing key must be carried forward explicitly or every edit
                // would erase the opportunity's image.
                existingOpportunity.imageKey()
        );

        return opportunityRepository.update(updatedOpportunity);
    }

    public Opportunity closeOpportunity(String opportunityId, String organizationId) {
        Opportunity existingOpportunity = getById(opportunityId);

        if (!existingOpportunity.organizationId().equals(organizationId)) {
            throw new ForbiddenException(
                    "Only the organization that owns this opportunity can close it.");
        }

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

    // MVP assumes an opportunity starts and ends on the same date; overnight ranges (e.g.
    // 22:00-02:00) are not supported.
    private void validateTimeRange(String startTime, String endTime) {
        LocalTime start;
        LocalTime end;
        try {
            start = LocalTime.parse(startTime);
            end = LocalTime.parse(endTime);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("startTime and endTime must be valid times in HH:mm format");
        }

        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("End time must be later than start time.");
        }
    }
}