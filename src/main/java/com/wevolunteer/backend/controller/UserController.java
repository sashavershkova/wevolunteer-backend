package com.wevolunteer.backend.controller;

import com.wevolunteer.backend.dto.CreateUserRequest;
import com.wevolunteer.backend.dto.UserProfileResponse;
import com.wevolunteer.backend.dto.UpdateUserRequest;
import com.wevolunteer.backend.model.Registration;
import com.wevolunteer.backend.service.RegistrationService;
import com.wevolunteer.backend.service.ProfileResponseMapper;
import com.wevolunteer.backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class UserController {

    private final UserService userService;
    private final RegistrationService registrationService;
    private final ProfileResponseMapper profileResponseMapper;

    public UserController(
            UserService userService,
            RegistrationService registrationService,
            ProfileResponseMapper profileResponseMapper
    ) {
        this.userService = userService;
        this.registrationService = registrationService;
        this.profileResponseMapper = profileResponseMapper;
    }

    @GetMapping("/users/{userId}")
    public UserProfileResponse getUser(@PathVariable String userId) {
        return profileResponseMapper.toResponse(userService.getById(userId));
    }

    @GetMapping("/users/{userId}/registrations")
    public List<Registration> getUserRegistrations(@PathVariable String userId) {
        return registrationService.getRegistrationsByUserId(userId);
    }

    @PostMapping("/users")
    public UserProfileResponse createUser(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateUserRequest request
    ) {
        return profileResponseMapper.toResponse(
                userService.createUser(jwt.getSubject(), request));
    }

    @GetMapping("/users/me")
    public UserProfileResponse getCurrentUser(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return profileResponseMapper.toResponse(userService.getById(jwt.getSubject()));
    }

    @PatchMapping("/users/{userId}")
    public UserProfileResponse updateUser(
            @PathVariable String userId,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        return profileResponseMapper.toResponse(userService.updateUser(userId, request));
    }

    @DeleteMapping("/users/{userId}")
    public void deleteUser(@PathVariable String userId) {
        userService.deleteUser(userId);
    }
}