package com.wevolunteer.backend.dto;

/**
 * Temporary permission to upload exactly one object.
 *
 * @param objectKey        the key the client sends back when creating the opportunity
 * @param uploadUrl        pre-signed S3 URL accepting a single PUT
 * @param expiresInSeconds lifetime of {@code uploadUrl}
 */
public record OpportunityImageUploadUrlResponse(
        String objectKey,
        String uploadUrl,
        long expiresInSeconds
) {}
