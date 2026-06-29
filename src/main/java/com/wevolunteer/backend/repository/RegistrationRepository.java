package com.wevolunteer.backend.repository;

import com.wevolunteer.backend.model.Registration;

import java.util.List;

public interface RegistrationRepository {

    List<Registration> findByUserId(String userId);

    List<Registration> findByOpportunityId(String opportunityId);

    void registerUserForOpportunity(
            String userId,
            String userName,
            String userEmail,
            String opportunityId,
            String opportunityTitle,
            String opportunityDate,
            String opportunityLocation,
            String organizationId,
            String organizationName
    );
}