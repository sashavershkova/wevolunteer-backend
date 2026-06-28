package com.wevolunteer.backend.model;

public record User(
        String userId,
        String name,
        String email,
        String role
) {}