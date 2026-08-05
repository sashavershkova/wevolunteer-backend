package com.wevolunteer.notification;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationHandler")
class NotificationHandlerTest {

    private static final String NOTIFICATION_EVENT_JSON = """
            {
              "eventType": "REGISTRATION_CREATED",
              "userId": "user-1",
              "volunteerName": "Jane Volunteer",
              "volunteerEmail": "jane@example.com",
              "opportunityId": "opp-1",
              "opportunityTitle": "Food Bank Volunteer Shift",
              "opportunityDate": "2026-08-10",
              "organizationId": "org-1",
              "organizationName": "Seattle Food Bank",
              "timestamp": "2026-08-04T12:00:00Z"
            }
            """;

    @Mock
    private EmailSender emailSender;

    private NotificationHandler handler;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        handler = new NotificationHandler(objectMapper, new NotificationEmailContentFactory(), emailSender);
    }

    private static SQSEvent eventWithBody(String messageId, String body) {
        SQSEvent.SQSMessage message = new SQSEvent.SQSMessage();
        message.setMessageId(messageId);
        message.setBody(body);

        SQSEvent event = new SQSEvent();
        event.setRecords(List.of(message));
        return event;
    }

    private static String snsEnvelope(String messageId, String messageField) {
        StringBuilder json = new StringBuilder("{")
                .append("\"Type\":\"Notification\",")
                .append("\"MessageId\":\"").append(messageId).append("\",")
                .append("\"TopicArn\":\"arn:aws:sns:us-east-1:110441303427:wevolunteer-notifications\",");
        if (messageField != null) {
            json.append("\"Message\":\"").append(messageField).append("\",");
        }
        json.append("\"Timestamp\":\"2026-08-04T12:00:01.000Z\"")
                .append("}");
        return json.toString();
    }

    private static String escapedNotificationEventJson() {
        return NOTIFICATION_EVENT_JSON.replace("\"", "\\\"").replace("\n", "");
    }

    private static String escapedNotificationEventJson(String eventType) {
        return NOTIFICATION_EVENT_JSON
                .replace("REGISTRATION_CREATED", eventType)
                .replace("\"", "\\\"")
                .replace("\n", "");
    }

    @Test
    @DisplayName("parses a valid SNS-wrapped SQS message and sends the notification email")
    void parsesSnsWrappedMessageAndSendsEmail() {
        SQSEvent event = eventWithBody(
                "msg-1", snsEnvelope("sns-msg-1", escapedNotificationEventJson()));

        assertDoesNotThrow(() -> handler.handleRequest(event, null));

        verify(emailSender, times(1)).send(any(), any());
    }

    @Test
    @DisplayName("parses a valid raw SQS message as a fallback and sends the notification email")
    void parsesRawMessageAsFallbackAndSendsEmail() {
        SQSEvent event = eventWithBody("msg-2", NOTIFICATION_EVENT_JSON);

        assertDoesNotThrow(() -> handler.handleRequest(event, null));

        verify(emailSender, times(1)).send(any(), any());
    }

    @Test
    @DisplayName("parses a valid SNS-wrapped REGISTRATION_CANCELLED message and sends the notification email")
    void parsesRegistrationCancelledMessageAndSendsEmail() {
        SQSEvent event = eventWithBody(
                "msg-7",
                snsEnvelope("sns-msg-7", escapedNotificationEventJson("REGISTRATION_CANCELLED")));

        assertDoesNotThrow(() -> handler.handleRequest(event, null));

        verify(emailSender, times(1)).send(any(), any());
    }

    @Test
    @DisplayName("parses a valid SNS-wrapped REGISTRATION_CANCELLED_BY_ORGANIZATION message and sends the notification email")
    void parsesRegistrationCancelledByOrganizationMessageAndSendsEmail() {
        SQSEvent event = eventWithBody(
                "msg-8",
                snsEnvelope("sns-msg-8",
                        escapedNotificationEventJson("REGISTRATION_CANCELLED_BY_ORGANIZATION")));

        assertDoesNotThrow(() -> handler.handleRequest(event, null));

        verify(emailSender, times(1)).send(any(), any());
    }

    @Test
    @DisplayName("throws when the SQS body is not valid JSON, and never attempts to send an email")
    void throwsOnMalformedSnsEnvelope() {
        SQSEvent event = eventWithBody("msg-3", "{not-valid-json");

        NotificationParsingException ex = assertThrows(NotificationParsingException.class,
                () -> handler.handleRequest(event, null));

        assertTrue(ex.getMessage().contains("SQS body parsing"));
        assertTrue(ex.getMessage().contains("msg-3"));
        verify(emailSender, never()).send(any(), any());
    }

    @Test
    @DisplayName("throws when the SNS envelope is missing the Message field, and never attempts to send an email")
    void throwsWhenSnsEnvelopeMissingMessage() {
        SQSEvent event = eventWithBody("msg-4", snsEnvelope("sns-msg-4", null));

        NotificationParsingException ex = assertThrows(NotificationParsingException.class,
                () -> handler.handleRequest(event, null));

        assertTrue(ex.getMessage().contains("SNS Message extraction"));
        assertTrue(ex.getMessage().contains("msg-4"));
        verify(emailSender, never()).send(any(), any());
    }

    @Test
    @DisplayName("throws when the inner notification event JSON is malformed, and never attempts to send an email")
    void throwsOnMalformedInnerEvent() {
        SQSEvent event = eventWithBody("msg-5", snsEnvelope("sns-msg-5", "{not-valid-json"));

        NotificationParsingException ex = assertThrows(NotificationParsingException.class,
                () -> handler.handleRequest(event, null));

        assertTrue(ex.getMessage().contains("notification event parsing"));
        assertTrue(ex.getMessage().contains("msg-5"));
        verify(emailSender, never()).send(any(), any());
    }

    @Test
    @DisplayName("propagates an email-sending failure so the SQS message is retried")
    void propagatesEmailSendingFailure() {
        SQSEvent event = eventWithBody(
                "msg-6", snsEnvelope("sns-msg-6", escapedNotificationEventJson()));

        doThrow(new RuntimeException("SES is unavailable")).when(emailSender).send(any(), any());

        assertThrows(RuntimeException.class, () -> handler.handleRequest(event, null));
    }
}
