package com.wevolunteer.backend.service;

import com.wevolunteer.backend.dto.CreateUserRequest;
import com.wevolunteer.backend.dto.UpdateUserRequest;
import com.wevolunteer.backend.model.Registration;
import com.wevolunteer.backend.model.User;
import com.wevolunteer.backend.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RegistrationService registrationService;

    public UserService(
            UserRepository userRepository,
            RegistrationService registrationService) {
        this.userRepository = userRepository;
        this.registrationService = registrationService;
    }

    public User getById(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
    }

    public User createUser(CreateUserRequest request) {
        User user = new User(
                request.userId(),
                request.name(),
                request.email(),
                request.role()
        );

        return userRepository.save(user);
    }

    public User updateUser(String userId, UpdateUserRequest request) {
        User user = new User(
                userId,
                request.name(),
                request.email(),
                request.role()
        );

        return userRepository.update(user);
    }

    public void deleteUser(String userId) {
        List<Registration> registrations = registrationService.getRegistrationsByUserId(userId);

        for (Registration registration : registrations) {
            registrationService.cancelRegistration(
                    userId,
                    registration.opportunityId()
            );
        }

        userRepository.deleteById(userId);
    }
}