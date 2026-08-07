package com.wevolunteer.backend.service;

import com.wevolunteer.backend.exception.ConflictException;
import com.wevolunteer.backend.exception.ForbiddenException;
import com.wevolunteer.backend.exception.NotFoundException;
import com.wevolunteer.backend.model.Opportunity;
import com.wevolunteer.backend.model.User;
import com.wevolunteer.backend.model.Waitlist;
import com.wevolunteer.backend.notification.NotificationEvent;
import com.wevolunteer.backend.notification.NotificationEventType;
import com.wevolunteer.backend.notification.NotificationPublisher;
import com.wevolunteer.backend.repository.OpportunityRepository;
import com.wevolunteer.backend.repository.UserRepository;
import com.wevolunteer.backend.repository.WaitlistRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class WaitlistService {

    private final WaitlistRepository waitlistRepository;
    private final UserRepository userRepository;
    private final OpportunityRepository opportunityRepository;
    private final NotificationPublisher notificationPublisher;

    public WaitlistService(
            WaitlistRepository waitlistRepository,
            UserRepository userRepository,
            OpportunityRepository opportunityRepository,
            NotificationPublisher notificationPublisher) {

        this.waitlistRepository = waitlistRepository;
        this.userRepository = userRepository;
        this.opportunityRepository = opportunityRepository;
        this.notificationPublisher = notificationPublisher;
    }

    public List<Waitlist> getWaitlistByUserId(String userId) {
        return waitlistRepository.findByUserId(userId);
    }

    /**
     * The opportunity-side sort key is {@code WAITLIST#<joinedAt>#<userId>}, so the repository
     * already returns entries oldest-joined-first - the volunteers' actual waitlist position.
     */
    public List<Waitlist> getWaitlistForOrganizationOpportunity(
            String opportunityId,
            String organizationId) {

        Opportunity opportunity = opportunityRepository.findById(opportunityId)
                .orElseThrow(() ->
                        new NotFoundException("Opportunity not found: " + opportunityId));

        if (!opportunity.organizationId().equals(organizationId)) {
            throw new ForbiddenException(
                    "Only the organization that owns this opportunity can view its waitlist.");
        }

        return waitlistRepository.findByOpportunityId(opportunityId);
    }

    public Waitlist join(String userId, String opportunityId) {
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

        if (OpportunityDatePolicy.isPast(opportunity)) {
            throw new ConflictException("Past opportunities are not open for waitlisting.");
        }

        if (opportunity.availableSpots() > 0) {
            throw new ConflictException(
                    "Opportunity '" + opportunityId + "' still has open spots - register instead of joining the waitlist.");
        }

        Waitlist waitlist = new Waitlist(
                user.userId(),
                opportunity.opportunityId(),
                opportunity.title(),
                opportunity.date(),
                opportunity.location(),
                opportunity.organizationId(),
                opportunity.organizationName(),
                user.name(),
                user.email(),
                LocalDateTime.now().toString()
        );

        waitlistRepository.joinWaitlist(waitlist);

        notificationPublisher.publish(new NotificationEvent(
                NotificationEventType.WAITLIST_JOINED,
                user.userId(),
                user.name(),
                user.email(),
                opportunity.opportunityId(),
                opportunity.title(),
                opportunity.date(),
                opportunity.organizationId(),
                opportunity.organizationName(),
                Instant.now()
        ));

        return waitlist;
    }

    public void leave(String userId, String opportunityId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new NotFoundException("User not found: " + userId));

        Opportunity opportunity = opportunityRepository.findById(opportunityId)
                .orElseThrow(() ->
                        new NotFoundException("Opportunity not found: " + opportunityId));

        waitlistRepository.leaveWaitlist(userId, opportunityId, opportunity.date());

        notificationPublisher.publish(new NotificationEvent(
                NotificationEventType.WAITLIST_LEFT,
                user.userId(),
                user.name(),
                user.email(),
                opportunity.opportunityId(),
                opportunity.title(),
                opportunity.date(),
                opportunity.organizationId(),
                opportunity.organizationName(),
                Instant.now()
        ));
    }

    /**
     * Removes every waitlist entry for an opportunity as part of closing it, and publishes one
     * WAITLIST_CANCELLED_BY_ORGANIZATION event per affected volunteer once their removal
     * succeeds - so a later reopen starts with a genuinely clean waitlist instead of leaving
     * stale entries that would show a volunteer as still "Waitlisted" on an opportunity with
     * open spots, and so waitlisted volunteers hear about the cancellation the same way
     * registered volunteers already do.
     *
     * <p>Each removal is its own transaction, mirroring
     * {@link RegistrationService#cancelAllRegistrationsForOpportunity}: a failure partway
     * through leaves the remaining entries (and their events) untouched rather than corrupting
     * data, and the caller is responsible for not treating that as a successful close.
     */
    public void removeWaitlistForOpportunityClose(Opportunity opportunity) {
        List<Waitlist> entries = waitlistRepository.findByOpportunityId(opportunity.opportunityId());

        for (Waitlist entry : entries) {
            waitlistRepository.leaveWaitlistForOpportunityClose(
                    entry.userId(),
                    opportunity.opportunityId(),
                    opportunity.date(),
                    entry.joinedAt());

            notificationPublisher.publish(new NotificationEvent(
                    NotificationEventType.WAITLIST_CANCELLED_BY_ORGANIZATION,
                    entry.userId(),
                    entry.volunteerName(),
                    entry.email(),
                    opportunity.opportunityId(),
                    opportunity.title(),
                    opportunity.date(),
                    opportunity.organizationId(),
                    opportunity.organizationName(),
                    Instant.now()
            ));
        }
    }
}
