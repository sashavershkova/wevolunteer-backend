package com.wevolunteer.backend.repository;

import com.wevolunteer.backend.model.Organization;

import java.util.Optional;

public interface OrganizationRepository {

    Optional<Organization> findById(String organizationId);

    Organization save(Organization organization);

    Organization update(Organization organization);
}