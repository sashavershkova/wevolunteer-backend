package com.wevolunteer.backend.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AuthController {

    @GetMapping("/auth/me")
    public Map<String, String> getCurrentUser(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return Map.of("userId", jwt.getSubject());
    }
}