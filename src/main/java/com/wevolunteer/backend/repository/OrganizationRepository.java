package com.wevolunteer.backend.repository;

import com.wevolunteer.backend.model.Organization;

import java.util.Optional;

public interface OrganizationRepository {

    Optional<Organization> findById(String organizationId);

    Organization save(Organization organization);

    Organization update(Organization organization);

    /**
     * Updates only the profile image key, leaving every other profile attribute untouched.
     *
     * @return the profile as stored after the update
     */
    Organization updateProfileImageKey(String organizationId, String profileImageKey);

    void deleteById(String organizationId);
}
