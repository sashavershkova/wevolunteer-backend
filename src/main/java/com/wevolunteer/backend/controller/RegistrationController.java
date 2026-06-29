package com.wevolunteer.backend.controller;

import com.wevolunteer.backend.dto.RegisterRequest;
import com.wevolunteer.backend.dto.RegisterResponse;
import com.wevolunteer.backend.service.RegistrationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/registrations")
    public RegisterResponse register(
            @Valid @RequestBody RegisterRequest request) {

        return registrationService.register(request);
    }

    @DeleteMapping("/registrations/{userId}/{opportunityId}")
    public void cancelRegistration(
            @PathVariable String userId,
            @PathVariable String opportunityId) {

        registrationService.cancelRegistration(userId, opportunityId);
    }
}