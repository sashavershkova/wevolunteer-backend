package com.wevolunteer.backend.service;

import com.wevolunteer.backend.model.Registration;
import com.wevolunteer.backend.repository.RegistrationRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RegistrationService {

    private final RegistrationRepository registrationRepository;

    public RegistrationService(RegistrationRepository registrationRepository) {
        this.registrationRepository = registrationRepository;
    }

    public List<Registration> getRegistrationsByUserId(String userId) {
        return registrationRepository.findByUserId(userId);
    }

    public List<Registration> getRegistrationsByOpportunityId(String opportunityId) {
        return registrationRepository.findByOpportunityId(opportunityId);
    }
}