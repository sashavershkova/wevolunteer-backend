package com.wevolunteer.backend.repository;

import com.wevolunteer.backend.model.Waitlist;

import java.util.List;

public interface WaitlistRepository {

    List<Waitlist> findByUserId(String userId);

    List<Waitlist> findByOpportunityId(String opportunityId);

    void joinWaitlist(Waitlist waitlist);

    void leaveWaitlist(String userId, String opportunityId, String opportunityDate);

    /**
     * Removes one waitlist entry (both dual-write DynamoDB items) as part of closing the
     * opportunity it belongs to. Unlike {@link #leaveWaitlist}, this takes {@code joinedAt}
     * directly instead of looking up the opportunity-side sort key by query - the caller
     * already has it from {@link #findByOpportunityId}, which reads every entry for the
     * opportunity being closed anyway.
     */
    void leaveWaitlistForOpportunityClose(
            String userId, String opportunityId, String opportunityDate, String joinedAt);
}
