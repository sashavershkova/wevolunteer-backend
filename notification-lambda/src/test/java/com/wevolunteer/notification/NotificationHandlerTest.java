package com.wevolunteer.notification;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    private final NotificationHandler handler = new NotificationHandler();

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

    @Test
    @DisplayName("parses a valid SNS-wrapped SQS message")
    void parsesSnsWrappedMessage() {
        SQSEvent event = eventWithBody(
                "msg-1", snsEnvelope("sns-msg-1", escapedNotificationEventJson()));

        assertDoesNotThrow(() -> handler.handleRequest(event, null));
    }

    @Test
    @DisplayName("parses a valid raw SQS message as a fallback")
    void parsesRawMessageAsFallback() {
        SQSEvent event = eventWithBody("msg-2", NOTIFICATION_EVENT_JSON);

        assertDoesNotThrow(() -> handler.handleRequest(event, null));
    }

    @Test
    @DisplayName("throws when the SQS body is not valid JSON")
    void throwsOnMalformedSnsEnvelope() {
        SQSEvent event = eventWithBody("msg-3", "{not-valid-json");

        NotificationParsingException ex = assertThrows(NotificationParsingException.class,
                () -> handler.handleRequest(event, null));

        assertTrue(ex.getMessage().contains("SQS body parsing"));
        assertTrue(ex.getMessage().contains("msg-3"));
    }

    @Test
    @DisplayName("throws when the SNS envelope is missing the Message field")
    void throwsWhenSnsEnvelopeMissingMessage() {
        SQSEvent event = eventWithBody("msg-4", snsEnvelope("sns-msg-4", null));

        NotificationParsingException ex = assertThrows(NotificationParsingException.class,
                () -> handler.handleRequest(event, null));

        assertTrue(ex.getMessage().contains("SNS Message extraction"));
        assertTrue(ex.getMessage().contains("msg-4"));
    }

    @Test
    @DisplayName("throws when the inner notification event JSON is malformed")
    void throwsOnMalformedInnerEvent() {
        SQSEvent event = eventWithBody("msg-5", snsEnvelope("sns-msg-5", "{not-valid-json"));

        NotificationParsingException ex = assertThrows(NotificationParsingException.class,
                () -> handler.handleRequest(event, null));

        assertTrue(ex.getMessage().contains("notification event parsing"));
        assertTrue(ex.getMessage().contains("msg-5"));
    }
}
