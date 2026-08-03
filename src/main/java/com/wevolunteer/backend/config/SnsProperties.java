package com.wevolunteer.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the SNS notification topic.
 *
 * <p>The topic ARN is supplied by the environment (SNS_TOPIC_ARN) so that no environment- or
 * account-specific resource identifier is compiled into the application, mirroring
 * {@link S3ProfileImageProperties}. Startup fails fast when it is missing, rather than allowing
 * the application to boot and fail on the first publish attempt.
 */
@ConfigurationProperties(prefix = "aws.sns")
public record SnsProperties(String topicArn) {

    public SnsProperties {
        if (topicArn == null || topicArn.isBlank()) {
            throw new IllegalStateException(
                    "aws.sns.topic-arn is not configured. Set the SNS_TOPIC_ARN environment "
                            + "variable to the notification topic ARN before starting the "
                            + "application.");
        }
    }
}
