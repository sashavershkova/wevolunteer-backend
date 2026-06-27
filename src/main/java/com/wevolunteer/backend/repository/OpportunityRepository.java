package com.wevolunteer.backend.repository;

import com.wevolunteer.backend.model.Opportunity;

import java.util.List;
import java.util.Optional;

public interface OpportunityRepository {
    Optional<Opportunity> findById(String opportunityId);

    List<Opportunity> findOpenOpportunities();

    List<Opportunity> findByCategory(String category);

    List<Opportunity> findByLocation(String location);

    List<Opportunity> findByOrganizationId(String organizationId); // OPEN only

    List<Opportunity> findAllByOrganizationId(String organizationId); // OPEN and CLOSED

    List<Opportunity> findOpenOpportunitiesByDateRange(String startDate, String endDate);
}