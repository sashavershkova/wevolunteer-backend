package com.wevolunteer.backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Confirms which successfully uploaded object should become an opportunity's image.
 *
 * <p>Sent after the browser has completed its PUT to the pre-signed URL. Generating an upload URL
 * does not prove the upload finished, so nothing is stored until this call arrives.
 */
public record AttachOpportunityImageRequest(
        @NotBlank String objectKey
) {}
