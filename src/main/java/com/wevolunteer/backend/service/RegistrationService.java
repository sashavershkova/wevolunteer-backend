package com.wevolunteer.backend.service;

import com.wevolunteer.backend.model.Opportunity;
import com.wevolunteer.backend.model.Registration;
import com.wevolunteer.backend.model.User;
import com.wevolunteer.backend.model.Waitlist;
import com.wevolunteer.backend.notification.NotificationEvent;
import com.wevolunteer.backend.notification.NotificationEventType;
import com.wevolunteer.backend.notification.NotificationPublisher;
import com.wevolunteer.backend.repository.OpportunityRepository;
import com.wevolunteer.backend.repository.RegistrationRepository;
import com.wevolunteer.backend.repository.UserRepository;
import com.wevolunteer.backend.repository.WaitlistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.wevolunteer.backend.dto.RegisterRequest;
import com.wevolunteer.backend.dto.RegisterResponse;
import com.wevolunteer.backend.exception.ForbiddenException;
import com.wevolunteer.backend.exception.NotFoundException;

import java.time.Instant;
import java.util.List;

@Service
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    private final RegistrationRepository registrationRepository;
    private final UserRepository userRepository;
    private final OpportunityRepository opportunityRepository;
    private final WaitlistRepository waitlistRepository;
    private final NotificationPublisher notificationPublisher;

    public RegistrationService(
            RegistrationRepository registrationRepository,
            UserRepository userRepository,
            OpportunityRepository opportunityRepository,
            WaitlistRepository waitlistRepository,
            NotificationPublisher notificationPublisher) {

        this.registrationRepository = registrationRepository;
        this.userRepository = userRepository;
        this.opportunityRepository = opportunityRepository;
        this.waitlistRepository = waitlistRepository;
        this.notificationPublisher = notificationPublisher;
    }

    public List<Registration> getRegistrationsByUserId(String userId) {
        return registrationRepository.findByUserId(userId).stream()
                .map(this::withOpportunityTime)
                .toList();
    }

    private Registration withOpportunityTime(Registration registration) {
        Opportunity opportunity = opportunityRepository
                .findById(registration.opportunityId())
                .orElse(null);

        return new Registration(
                registration.userId(),
                registration.opportunityId(),
                registration.title(),
                registration.date(),
                registration.location(),
                registration.organizationId(),
                registration.organizationName(),
                registration.registrationStatus(),
                registration.volunteerName(),
                registration.email(),
                registration.registeredAt(),
                opportunity != null ? opportunity.time() : null,
                opportunity != null ? opportunity.startTime() : null,
                opportunity != null ? opportunity.endTime() : null
        );
    }

    public List<Registration> getRegistrationsByOpportunityId(String opportunityId) {
        return registrationRepository.findByOpportunityId(opportunityId);
    }

    public List<Registration> getRegistrationsForOrganizationOpportunity(
            String opportunityId,
            String organizationId) {

        Opportunity opportunity = opportunityRepository.findById(opportunityId)
                .orElseThrow(() ->
                        new NotFoundException("Opportunity not found: " + opportunityId));

        if (!opportunity.organizationId().equals(organizationId)) {
            throw new ForbiddenException(
                    "Only the organization that owns this opportunity can view its registrations.");
        }

        return registrationRepository.findByOpportunityId(opportunityId);
    }

    public RegisterResponse register(RegisterRequest request) {

        User user = userRepository.findById(request.userId())
                .orElseThrow(() ->
                        new NotFoundException("User not found: " + request.userId()));

        Opportunity opportunity = opportunityRepository.findById(request.opportunityId())
                .orElseThrow(() ->
                        new NotFoundException("Opportunity not found: " + request.opportunityId()));

        registrationRepository.registerUserForOpportunity(
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

        notificationPublisher.publish(new NotificationEvent(
                NotificationEventType.REGISTRATION_CREATED,
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

        return new RegisterResponse(
                "Registration successful",
                user.userId(),
                opportunity.opportunityId(),
                opportunity.registeredCount() + 1,
                opportunity.availableSpots() - 1
        );
    }

    public void cancelRegistration(String userId, String opportunityId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new NotFoundException("User not found: " + userId));

        Opportunity opportunity = opportunityRepository.findById(opportunityId)
                .orElseThrow(() ->
                        new NotFoundException("Opportunity not found: " + opportunityId));

        registrationRepository.cancelRegistration(
                userId,
                opportunityId,
                opportunity.date()
        );

        notificationPublisher.publish(new NotificationEvent(
                NotificationEventType.REGISTRATION_CANCELLED,
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

        promoteNextWaitlistedVolunteer(opportunity);
    }

    /**
     * Best-effort: called only from a volunteer's own {@link #cancelRegistration}, never from
     * {@link #cancelAllRegistrationsForOpportunity} (an organization closing an opportunity has
     * no spot to promote anyone into). The cancellation above has already committed by this
     * point, so any failure here is logged and swallowed rather than thrown - a promotion race
     * or write failure must never make a successful cancellation look like it failed.
     */
    private void promoteNextWaitlistedVolunteer(Opportunity opportunity) {
        List<Waitlist> waitlist = waitlistRepository.findByOpportunityId(opportunity.opportunityId());
        if (waitlist.isEmpty()) {
            return;
        }
        Waitlist next = waitlist.get(0);

        try {
            registrationRepository.registerUserForOpportunity(
                    next.userId(),
                    next.volunteerName(),
                    next.email(),
                    opportunity.opportunityId(),
                    opportunity.title(),
                    opportunity.date(),
                    opportunity.location(),
                    opportunity.organizationId(),
                    opportunity.organizationName()
            );

            waitlistRepository.leaveWaitlist(next.userId(), opportunity.opportunityId(), next.date());

            notificationPublisher.publish(new NotificationEvent(
                    NotificationEventType.WAITLIST_PROMOTED,
                    next.userId(),
                    next.volunteerName(),
                    next.email(),
                    opportunity.opportunityId(),
                    opportunity.title(),
                    opportunity.date(),
                    opportunity.organizationId(),
                    opportunity.organizationName(),
                    Instant.now()
            ));
        } catch (RuntimeException e) {
            log.warn("Skipped waitlist promotion for user={} opportunityId={}: {}",
                    next.userId(), opportunity.opportunityId(), e.getMessage());
        }
    }

    /**
     * Cancels every registration for an opportunity, removing both dual-write DynamoDB items
     * per registration, and publishes one REGISTRATION_CANCELLED_BY_ORGANIZATION event per
     * affected volunteer once their cancellation succeeds. Used when an organization closes an
     * opportunity.
     *
     * <p>Each event describes the durable fact that has already happened for that one volunteer
     * — their registration was cancelled — rather than the opportunity's own status, which is
     * not yet CLOSED at this point and may never become so if a later volunteer's cancellation
     * fails. OPPORTUNITY_CLOSED would be a false claim here, since the caller only calls
     * opportunityRepository.close(...) after every cancellation in this loop has succeeded.
     *
     * <p>The opportunity-side registration item (what findByOpportunityId reads) carries no date
     * attribute, so this uses cancelRegistrationForOpportunityClose, which looks up each
     * registration's real sort key instead of reconstructing it. That same item already carries
     * volunteerName and email (written by registerUserForOpportunity), so each event is built
     * from the Registration already in hand — no additional User lookup per volunteer.
     *
     * <p>Each cancellation is its own transaction, so a failure partway through leaves the
     * remaining registrations (and their events) unpublished rather than corrupting data; the
     * caller is responsible for not treating that as a successful close.
     */
    public void cancelAllRegistrationsForOpportunity(Opportunity opportunity) {
        List<Registration> registrations =
                registrationRepository.findByOpportunityId(opportunity.opportunityId());

        for (Registration registration : registrations) {
            registrationRepository.cancelRegistrationForOpportunityClose(
                    registration.userId(),
                    opportunity.opportunityId()
            );

            notificationPublisher.publish(new NotificationEvent(
                    NotificationEventType.REGISTRATION_CANCELLED_BY_ORGANIZATION,
                    registration.userId(),
                    registration.volunteerName(),
                    registration.email(),
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