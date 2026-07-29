package com.wevolunteer.backend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("S3ProfileImageProperties")
class S3ProfileImagePropertiesTest {

    private static final String BUCKET = "wevolunteer-profile-images-test";

    @Nested
    @DisplayName("bucket name")
    class BucketName {

        @Test
        @DisplayName("is kept as supplied")
        void keepsSuppliedBucket() {
            S3ProfileImageProperties properties =
                    new S3ProfileImageProperties(BUCKET, null, null);

            assertThat(properties.profileImagesBucket()).isEqualTo(BUCKET);
        }

        @Test
        @DisplayName("fails fast when null so the application cannot start misconfigured")
        void rejectsNullBucket() {
            assertThatThrownBy(() -> new S3ProfileImageProperties(null, null, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("PROFILE_IMAGES_BUCKET");
        }

        @Test
        @DisplayName("fails fast when blank")
        void rejectsBlankBucket() {
            assertThatThrownBy(() -> new S3ProfileImageProperties("   ", null, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("PROFILE_IMAGES_BUCKET");
        }
    }

    @Nested
    @DisplayName("durations")
    class Durations {

        @Test
        @DisplayName("default to 15 minutes for upload and 60 minutes for display")
        void appliesDefaults() {
            S3ProfileImageProperties properties =
                    new S3ProfileImageProperties(BUCKET, null, null);

            assertThat(properties.uploadUrlDuration()).isEqualTo(Duration.ofMinutes(15));
            assertThat(properties.downloadUrlDuration()).isEqualTo(Duration.ofMinutes(60));
        }

        @Test
        @DisplayName("keep configured values when supplied")
        void keepsConfiguredValues() {
            S3ProfileImageProperties properties = new S3ProfileImageProperties(
                    BUCKET, Duration.ofMinutes(5), Duration.ofHours(2));

            assertThat(properties.uploadUrlDuration()).isEqualTo(Duration.ofMinutes(5));
            assertThat(properties.downloadUrlDuration()).isEqualTo(Duration.ofHours(2));
        }

        @Test
        @DisplayName("reject zero, which would produce an already-expired URL")
        void rejectsZero() {
            assertThatThrownBy(() ->
                    new S3ProfileImageProperties(BUCKET, Duration.ZERO, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("greater than zero");
        }

        @Test
        @DisplayName("reject negative values")
        void rejectsNegative() {
            assertThatThrownBy(() ->
                    new S3ProfileImageProperties(BUCKET, Duration.ofMinutes(-1), null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("greater than zero");
        }

        @Test
        @DisplayName("reject values beyond the 7 day SigV4 maximum")
        void rejectsBeyondSigV4Maximum() {
            assertThatThrownBy(() ->
                    new S3ProfileImageProperties(BUCKET, null, Duration.ofDays(8)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("7 days");
        }
    }
}
