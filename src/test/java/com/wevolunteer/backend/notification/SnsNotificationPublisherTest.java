package com.wevolunteer.backend.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.wevolunteer.backend.config.SnsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Uses a {@link Jackson2ObjectMapperBuilder} with WRITE_DATES_AS_TIMESTAMPS disabled, the same
 * customization Spring Boot's JacksonAutoConfiguration applies to the ObjectMapper bean this
 * class receives at runtime, rather than a plain {@code new ObjectMapper()} that would serialize
 * LocalDate/Instant as arrays and epoch numbers.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SnsNotificationPublisher")
class SnsNotificationPublisherTest {

    private static final String TOPIC_ARN = "arn:test";

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Mock
    private SnsClient snsClient;

    private SnsNotificationPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new SnsNotificationPublisher(snsClient, objectMapper, new SnsProperties(TOPIC_ARN));
    }

    private NotificationEvent sampleEvent() {
        return new NotificationEvent(
                NotificationEventType.REGISTRATION_CREATED,
                "user-1",
                "Jane Volunteer",
                "jane@example.com",
                "opp-1",
                "Beach Cleanup",
                "2026-08-15",
                "org-1",
                "Green Earth",
                Instant.parse("2026-08-03T12:00:00Z"));
    }

    @Nested
    @DisplayName("publish")
    class Publish {

        @Test
        @DisplayName("sends the serialized event to the configured topic ARN")
        void publishesToConfiguredTopic() {
            when(snsClient.publish(any(PublishRequest.class)))
                    .thenReturn(PublishResponse.builder().messageId("msg-1").build());

            publisher.publish(sampleEvent());

            ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
            verify(snsClient).publish(captor.capture());

            PublishRequest request = captor.getValue();
            assertThat(request.topicArn()).isEqualTo(TOPIC_ARN);
            assertThat(request.message())
                    .contains("\"eventType\":\"REGISTRATION_CREATED\"")
                    .contains("\"userId\":\"user-1\"")
                    .contains("\"volunteerName\":\"Jane Volunteer\"")
                    .contains("\"volunteerEmail\":\"jane@example.com\"")
                    .contains("\"opportunityId\":\"opp-1\"")
                    .contains("\"opportunityTitle\":\"Beach Cleanup\"")
                    .contains("\"opportunityDate\":\"2026-08-15\"")
                    .contains("\"organizationId\":\"org-1\"")
                    .contains("\"organizationName\":\"Green Earth\"")
                    .contains("\"timestamp\":\"2026-08-03T12:00:00Z\"");
        }

        @Test
        @DisplayName("does not propagate an SNS client failure")
        void doesNotPropagateSnsFailure() {
            when(snsClient.publish(any(PublishRequest.class)))
                    .thenThrow(SdkException.create("throttled", null));

            assertThatCode(() -> publisher.publish(sampleEvent())).doesNotThrowAnyException();
        }
    }
}
