package com.wevolunteer.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;

/**
 * Amazon SNS client used to publish notification events.
 *
 * <p>Relies on the default credentials provider chain: developer credentials locally, and the
 * ECS task role in deployed environments. Credentials are never constructed from access-key
 * strings in source code or environment variables.
 *
 * <p>The region is set explicitly, mirroring {@link S3Config}, rather than left to the SDK
 * region chain.
 */
@Configuration
@EnableConfigurationProperties(SnsProperties.class)
public class SnsConfig {

    private final Region region;

    public SnsConfig(@Value("${aws.region:us-east-1}") String region) {
        this.region = Region.of(region);
    }

    @Bean
    public SnsClient snsClient() {
        return SnsClient.builder()
                .region(region)
                .build();
    }
}
