package com.wevolunteer.backend.repository;

import com.wevolunteer.backend.model.Waitlist;

import java.util.List;

public interface WaitlistRepository {

    List<Waitlist> findByUserId(String userId);

    List<Waitlist> findByOpportunityId(String opportunityId);

    void joinWaitlist(Waitlist waitlist);

    void leaveWaitlist(String userId, String opportunityId, String opportunityDate);
}
