package com.wevolunteer.backend.dto;

/**
 * Temporary permission to upload exactly one profile image.
 *
 * @param objectKey        the key the client sends back to confirm the upload
 * @param uploadUrl        pre-signed S3 URL accepting a single PUT
 * @param expiresInSeconds lifetime of {@code uploadUrl}
 */
public record ProfileImageUploadUrlResponse(
        String objectKey,
        String uploadUrl,
        long expiresInSeconds
) {}
