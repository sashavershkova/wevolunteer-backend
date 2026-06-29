package com.wevolunteer.backend.service;

import com.wevolunteer.backend.model.Organization;
import com.wevolunteer.backend.repository.OrganizationRepository;
import org.springframework.stereotype.Service;
import com.wevolunteer.backend.dto.CreateOrganizationRequest;
import com.wevolunteer.backend.dto.UpdateOrganizationRequest;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    public OrganizationService(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    public Organization getById(String organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new RuntimeException("Organization not found: " + organizationId));
    }

    public Organization createOrganization(CreateOrganizationRequest request) {
        Organization organization = new Organization(
                request.organizationId(),
                request.name(),
                request.description(),
                request.email(),
                request.website()
        );

        return organizationRepository.save(organization);
    }

    public Organization updateOrganization(
            String organizationId,
            UpdateOrganizationRequest request) {

        Organization organization = new Organization(
                organizationId,
                request.name(),
                request.description(),
                request.email(),
                request.website()
        );

        return organizationRepository.update(organization);
    }
}