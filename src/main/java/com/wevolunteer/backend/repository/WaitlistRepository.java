package com.wevolunteer.backend.repository;

import com.wevolunteer.backend.model.Waitlist;

import java.util.List;

public interface WaitlistRepository {

    List<Waitlist> findByUserId(String userId);

    List<Waitlist> findByOpportunityId(String opportunityId);

    void joinWaitlist(
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

    void leaveWaitlist(String userId, String opportunityId, String opportunityDate);
}
