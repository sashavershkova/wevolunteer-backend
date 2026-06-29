package com.wevolunteer.backend.service;

import com.wevolunteer.backend.model.User;
import com.wevolunteer.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import com.wevolunteer.backend.dto.CreateUserRequest;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
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
}