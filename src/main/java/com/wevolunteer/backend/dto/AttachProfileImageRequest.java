package com.wevolunteer.backend.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Confirms which uploaded object should become the caller's profile image.
 *
 * <p>Sent after the browser has completed its PUT. Generating an upload URL does not prove the
 * upload finished, so nothing is stored until this call arrives.
 */
public record AttachProfileImageRequest(
        @NotBlank String objectKey
) {}
