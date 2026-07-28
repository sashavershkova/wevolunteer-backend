package com.wevolunteer.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration for private profile-image storage in Amazon S3.
 *
 * <p>The bucket name is supplied by the environment (PROFILE_IMAGES_BUCKET) so that no
 * production resource name is compiled into the application. Startup fails fast when it is
 * missing, rather than allowing the application to boot and fail on the first upload.
 */
@ConfigurationProperties(prefix = "aws.s3")
public record S3ProfileImageProperties(
        String profileImagesBucket,
        Duration uploadUrlDuration,
        Duration downloadUrlDuration
) {

    private static final Duration DEFAULT_UPLOAD_URL_DURATION = Duration.ofMinutes(15);
    private static final Duration DEFAULT_DOWNLOAD_URL_DURATION = Duration.ofMinutes(60);
    private static final Duration MAX_URL_DURATION = Duration.ofDays(7);

    public S3ProfileImageProperties {
        if (profileImagesBucket == null || profileImagesBucket.isBlank()) {
            throw new IllegalStateException(
                    "aws.s3.profile-images-bucket is not configured. "
                            + "Set the PROFILE_IMAGES_BUCKET environment variable to the "
                            + "profile-image bucket name before starting the application.");
        }

        uploadUrlDuration = requirePositiveOrDefault(
                uploadUrlDuration, DEFAULT_UPLOAD_URL_DURATION, "aws.s3.upload-url-duration");

        downloadUrlDuration = requirePositiveOrDefault(
                downloadUrlDuration, DEFAULT_DOWNLOAD_URL_DURATION, "aws.s3.download-url-duration");
    }

    private static Duration requirePositiveOrDefault(
            Duration value, Duration fallback, String propertyName) {

        if (value == null) {
            return fallback;
        }

        if (value.isNegative() || value.isZero()) {
            throw new IllegalStateException(propertyName + " must be greater than zero.");
        }

        if (value.compareTo(MAX_URL_DURATION) > 0) {
            throw new IllegalStateException(
                    propertyName + " must not exceed 7 days, the maximum lifetime "
                            + "AWS SigV4 allows for a pre-signed URL.");
        }

        return value;
    }
}
