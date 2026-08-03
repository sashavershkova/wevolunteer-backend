package com.wevolunteer.backend.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wevolunteer.backend.config.SnsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.core.exception.SdkException;

/**
 * Publishes {@link NotificationEvent}s to the configured SNS topic as JSON.
 *
 * <p>Best-effort: a failure to serialize or publish an event is logged and swallowed rather than
 * propagated, so a caller's own outcome (e.g. a successful DynamoDB write) never appears to fail
 * merely because notification delivery did not succeed. Retries are left to SNS/SQS/Lambda
 * downstream, not implemented here.
 *
 * <p>The volunteer email is never included in log output, only in the message body delivered to
 * SNS.
 */
@Service
public class SnsNotificationPublisher implements NotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(SnsNotificationPublisher.class);

    private final SnsClient snsClient;
    private final ObjectMapper objectMapper;
    private final String topicArn;

    public SnsNotificationPublisher(
            SnsClient snsClient,
            ObjectMapper objectMapper,
            SnsProperties snsProperties) {

        this.snsClient = snsClient;
        this.objectMapper = objectMapper;
        this.topicArn = snsProperties.topicArn();
    }

    @Override
    public void publish(NotificationEvent event) {
        String message;
        try {
            message = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error(
                    "Failed to serialize notification event type={} opportunityId={} userId={}",
                    event.eventType(), event.opportunityId(), event.userId(), e);
            return;
        }

        try {
            snsClient.publish(PublishRequest.builder()
                    .topicArn(topicArn)
                    .message(message)
                    .build());
        } catch (SdkException e) {
            log.error(
                    "Failed to publish notification event type={} opportunityId={} userId={}",
                    event.eventType(), event.opportunityId(), event.userId(), e);
        }
    }
}
