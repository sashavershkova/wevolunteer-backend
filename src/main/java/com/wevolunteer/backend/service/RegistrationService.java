package com.wevolunteer.backend.service;

import com.wevolunteer.backend.model.Opportunity;
import com.wevolunteer.backend.model.Registration;
import com.wevolunteer.backend.model.User;
import com.wevolunteer.backend.repository.OpportunityRepository;
import com.wevolunteer.backend.repository.RegistrationRepository;
import com.wevolunteer.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import com.wevolunteer.backend.dto.RegisterRequest;
import com.wevolunteer.backend.dto.RegisterResponse;
import com.wevolunteer.backend.exception.NotFoundException;

import java.util.List;

@Service
public class RegistrationService {

    private final RegistrationRepository registrationRepository;
    private final UserRepository userRepository;
    private final OpportunityRepository opportunityRepository;

    public RegistrationService(
            RegistrationRepository registrationRepository,
            UserRepository userRepository,
            OpportunityRepository opportunityRepository) {

        this.registrationRepository = registrationRepository;
        this.userRepository = userRepository;
        this.opportunityRepository = opportunityRepository;
    }

    public List<Registration> getRegistrationsByUserId(String userId) {
        return registrationRepository.findByUserId(userId);
    }

    public List<Registration> getRegistrationsByOpportunityId(String opportunityId) {
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
}