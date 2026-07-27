package com.wevolunteer.backend.controller;

import com.wevolunteer.backend.dto.RegisterRequest;
import com.wevolunteer.backend.dto.RegisterResponse;
import com.wevolunteer.backend.model.Registration;
import com.wevolunteer.backend.service.RegistrationService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    @GetMapping("/registrations/me")
    public List<Registration> getMyRegistrations(
            @AuthenticationPrincipal Jwt jwt) {

        return registrationService.getRegistrationsByUserId(jwt.getSubject());
    }
}