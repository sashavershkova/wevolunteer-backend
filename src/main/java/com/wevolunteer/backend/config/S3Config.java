package com.wevolunteer.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Amazon S3 clients for profile-image storage.
 *
 * <p>Both beans rely on the default credentials provider chain: developer credentials locally,
 * and the ECS task role in deployed environments. Credentials are never constructed from
 * access-key strings in source code or environment variables.
 *
 * <p>The region is set explicitly, mirroring {@link DynamoDbConfig}. Relying on the SDK region
 * chain would be fragile in CI, where CodeBuild sets AWS_DEFAULT_REGION but the SDK looks for
 * AWS_REGION.
 */
@Configuration
@EnableConfigurationProperties(S3ProfileImageProperties.class)
public class S3Config {

    private final Region region;

    public S3Config(@Value("${aws.region:us-east-1}") String region) {
        this.region = Region.of(region);
    }

    /** Used for object metadata checks and deletions. */
    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(region)
                .build();
    }

    /** Used to generate short-lived upload and display URLs. */
    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(region)
                .build();
    }
}
