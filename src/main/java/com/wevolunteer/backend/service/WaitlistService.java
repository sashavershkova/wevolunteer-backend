package com.wevolunteer.backend.service;

import com.wevolunteer.backend.exception.ConflictException;
import com.wevolunteer.backend.exception.NotFoundException;
import com.wevolunteer.backend.model.Opportunity;
import com.wevolunteer.backend.model.User;
import com.wevolunteer.backend.model.Waitlist;
import com.wevolunteer.backend.repository.OpportunityRepository;
import com.wevolunteer.backend.repository.UserRepository;
import com.wevolunteer.backend.repository.WaitlistRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WaitlistService {

    private final WaitlistRepository waitlistRepository;
    private final UserRepository userRepository;
    private final OpportunityRepository opportunityRepository;

    public WaitlistService(
            WaitlistRepository waitlistRepository,
            UserRepository userRepository,
            OpportunityRepository opportunityRepository) {

        this.waitlistRepository = waitlistRepository;
        this.userRepository = userRepository;
        this.opportunityRepository = opportunityRepository;
    }

    public List<Waitlist> getWaitlistByUserId(String userId) {
        return waitlistRepository.findByUserId(userId);
    }

    public void join(String userId, String opportunityId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new NotFoundException("User not found: " + userId));

        Opportunity opportunity = opportunityRepository.findById(opportunityId)
                .orElseThrow(() ->
                        new NotFoundException("Opportunity not found: " + opportunityId));

        if (!"OPEN".equals(opportunity.status())) {
            throw new ConflictException(
                    "Opportunity '" + opportunityId + "' is not open for registration.");
        }

        if (opportunity.availableSpots() > 0) {
            throw new ConflictException(
                    "Opportunity '" + opportunityId + "' still has open spots - register instead of joining the waitlist.");
        }

        waitlistRepository.joinWaitlist(
                user.userId(),
                user.name(),
                user.email(),
                opportunity.opportunityId(),
                opportunity.title(),
                opportunity.date(),
                opportunity.location(),
                opportunity.organizationId(),
                opportunity.organizationName()
        );
    }

    public void leave(String userId, String opportunityId) {
        Opportunity opportunity = opportunityRepository.findById(opportunityId)
                .orElseThrow(() ->
                        new NotFoundException("Opportunity not found: " + opportunityId));

        waitlistRepository.leaveWaitlist(userId, opportunityId, opportunity.date());
    }
}
