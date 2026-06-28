package com.wevolunteer.backend.model;

public record Organization(
        String organizationId,
        String name,
        String description,
        String email,
        String website
) {}