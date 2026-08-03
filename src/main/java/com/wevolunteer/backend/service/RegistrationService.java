package com.wevolunteer.backend.service;

import com.wevolunteer.backend.model.Opportunity;
import com.wevolunteer.backend.model.Registration;
import com.wevolunteer.backend.model.User;
import com.wevolunteer.backend.notification.NotificationEvent;
import com.wevolunteer.backend.notification.NotificationEventType;
import com.wevolunteer.backend.notification.NotificationPublisher;
import com.wevolunteer.backend.repository.OpportunityRepository;
import com.wevolunteer.backend.repository.RegistrationRepository;
import com.wevolunteer.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import com.wevolunteer.backend.dto.RegisterRequest;
import com.wevolunteer.backend.dto.RegisterResponse;
import com.wevolunteer.backend.exception.ForbiddenException;
import com.wevolunteer.backend.exception.NotFoundException;

import java.time.Instant;
import java.util.List;

@Service
public class RegistrationService {

    private final RegistrationRepository registrationRepository;
    private final UserRepository userRepository;
    private final OpportunityRepository opportunityRepository;
    private final NotificationPublisher notificationPublisher;

    public RegistrationService(
            RegistrationRepository registrationRepository,
            UserRepository userRepository,
            OpportunityRepository opportunityRepository,
            NotificationPublisher notificationPublisher) {

        this.registrationRepository = registrationRepository;
        this.userRepository = userRepository;
        this.opportunityRepository = opportunityRepository;
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
        Opportunity opportunity = opportunityRepository.findById(opportunityId)
                .orElseThrow(() ->
                        new NotFoundException("Opportunity not found: " + opportunityId));

        registrationRepository.cancelRegistration(
                userId,
                opportunityId,
                opportunity.date()
        );
    }

    /**
     * Cancels every registration for an opportunity, removing both dual-write DynamoDB items
     * per registration. Used when an organization closes an opportunity. The opportunity-side
     * registration item (what findByOpportunityId reads) carries no date attribute, so this uses
     * cancelRegistrationForOpportunityClose, which looks up each registration's real sort key
     * instead of reconstructing it. Each cancellation is its own transaction, so a failure
     * partway through leaves the remaining registrations intact rather than corrupting data;
     * the caller is responsible for not treating that as a successful close.
     */
    public void cancelAllRegistrationsForOpportunity(String opportunityId) {
        List<Registration> registrations = registrationRepository.findByOpportunityId(opportunityId);

        for (Registration registration : registrations) {
            registrationRepository.cancelRegistrationForOpportunityClose(
                    registration.userId(),
                    opportunityId
            );
        }
    }
}