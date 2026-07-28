package com.wevolunteer.backend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uses ApplicationContextRunner so the S3 configuration is exercised in isolation:
 * no full application context, no network access, and no AWS calls.
 */
@DisplayName("S3Config")
class S3ConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(S3Config.class);

    @Test
    @DisplayName("registers an S3Client and an S3Presigner")
    void registersS3Beans() {
        contextRunner
                .withPropertyValues("aws.s3.profile-images-bucket=test-bucket")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(S3Client.class);
                    assertThat(context).hasSingleBean(S3Presigner.class);
                });
    }

    @Test
    @DisplayName("binds the bucket name and duration properties")
    void bindsProperties() {
        contextRunner
                .withPropertyValues(
                        "aws.s3.profile-images-bucket=bound-bucket",
                        "aws.s3.upload-url-duration=10m",
                        "aws.s3.download-url-duration=30m")
                .run(context -> {
                    S3ProfileImageProperties properties =
                            context.getBean(S3ProfileImageProperties.class);

                    assertThat(properties.profileImagesBucket()).isEqualTo("bound-bucket");
                    assertThat(properties.uploadUrlDuration()).isEqualTo(Duration.ofMinutes(10));
                    assertThat(properties.downloadUrlDuration()).isEqualTo(Duration.ofMinutes(30));
                });
    }

    @Test
    @DisplayName("applies the default durations when only the bucket is configured")
    void appliesDefaultDurations() {
        contextRunner
                .withPropertyValues("aws.s3.profile-images-bucket=test-bucket")
                .run(context -> {
                    S3ProfileImageProperties properties =
                            context.getBean(S3ProfileImageProperties.class);

                    assertThat(properties.uploadUrlDuration()).isEqualTo(Duration.ofMinutes(15));
                    assertThat(properties.downloadUrlDuration()).isEqualTo(Duration.ofMinutes(60));
                });
    }

    @Test
    @DisplayName("fails to start when the bucket name is missing")
    void failsWhenBucketMissing() {
        contextRunner.run(context ->
                assertThat(context).hasFailed());
    }
}
