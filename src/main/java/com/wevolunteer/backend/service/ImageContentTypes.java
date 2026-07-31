package com.wevolunteer.backend.service;

import java.util.Map;

/**
 * The single definition of which images this application accepts, and how large they may be.
 *
 * <p>Shared by opportunity images and profile images so the rules cannot drift apart. These are
 * security-relevant decisions; two copies would eventually disagree.
 */
final class ImageContentTypes {

    /** Content types accepted for upload, mapped to the file extension used in the object key. */
    static final Map<String, String> ALLOWED = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");

    /** Listed explicitly so error messages have a stable order. */
    static final String ALLOWED_MESSAGE = "image/jpeg, image/png, image/webp";

    /**
     * Largest image accepted when attaching. A pre-signed PUT cannot enforce a size limit — the
     * signature covers the key and content type, not the body length — so the limit is applied
     * from the stored object's metadata, after the upload has happened.
     */
    static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;

    static final String UUID_REGEX =
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

    private ImageContentTypes() {
    }

    /**
     * Lower-cases the media type and drops any parameters, so that "IMAGE/JPEG" and
     * "image/jpeg; charset=utf-8" are both recognised.
     */
    static String normalize(String contentType) {
        if (contentType == null) {
            return "";
        }

        int parameterStart = contentType.indexOf(';');
        String mediaType = parameterStart >= 0
                ? contentType.substring(0, parameterStart)
                : contentType;

        return mediaType.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /** @return the file extension for an accepted content type */
    static String extensionForUpload(String contentType) {
        String extension = ALLOWED.get(normalize(contentType));

        if (extension == null) {
            throw new IllegalArgumentException(
                    "Unsupported image type. Allowed types are: " + ALLOWED_MESSAGE + ".");
        }

        return extension;
    }

    /** Checks the type S3 actually stored, which may differ from what was requested. */
    static void requireAllowedStoredType(String contentType) {
        if (!ALLOWED.containsKey(normalize(contentType))) {
            throw new IllegalArgumentException(
                    "The uploaded file is not a supported image. Allowed types are: "
                            + ALLOWED_MESSAGE + ".");
        }
    }

    static void requireWithinSizeLimit(Long contentLength) {
        if (contentLength != null && contentLength > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException(
                    "The uploaded image is larger than the 5 MB limit.");
        }
    }
}
