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

    void cancelRegistration(String userId, String opportunityId, String opportunityDate);

    /**
     * Cancels a registration when the caller only knows userId and opportunityId (e.g. an
     * organization closing an opportunity) and cannot supply the registration's original date.
     * The opportunity-side registration item carries no date attribute, so this looks up the
     * user-side item's real sort key instead of reconstructing it.
     */
    void cancelRegistrationForOpportunityClose(String userId, String opportunityId);
}