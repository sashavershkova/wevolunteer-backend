package com.wevolunteer.backend.controller;

import com.wevolunteer.backend.dto.CreateUserRequest;
import com.wevolunteer.backend.model.Registration;
import com.wevolunteer.backend.model.User;
import com.wevolunteer.backend.service.RegistrationService;
import com.wevolunteer.backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.wevolunteer.backend.dto.UpdateUserRequest;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;

import java.util.List;

@RestController
public class UserController {

    private final UserService userService;
    private final RegistrationService registrationService;

    public UserController(
            UserService userService,
            RegistrationService registrationService) {
        this.userService = userService;
        this.registrationService = registrationService;
    }

    @GetMapping("/users/{userId}")
    public User getUser(@PathVariable String userId) {
        return userService.getById(userId);
    }

    @GetMapping("/users/{userId}/registrations")
    public List<Registration> getUserRegistrations(@PathVariable String userId) {
        return registrationService.getRegistrationsByUserId(userId);
    }

    @PostMapping("/users")
    public User createUser(@Valid @RequestBody CreateUserRequest request) {
        return userService.createUser(request);
    }

    @PatchMapping("/users/{userId}")
    public User updateUser(
            @PathVariable String userId,
            @Valid @RequestBody UpdateUserRequest request) {

        return userService.updateUser(userId, request);
    }

    @DeleteMapping("/users/{userId}")
    public void deleteUser(@PathVariable String userId) {
        userService.deleteUser(userId);
    }
}