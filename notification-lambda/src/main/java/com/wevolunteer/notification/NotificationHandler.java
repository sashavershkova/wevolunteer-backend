package com.wevolunteer.notification;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

import java.util.logging.Logger;

/**
 * Consumes {@link NotificationEvent}s delivered via an SQS queue subscribed to the backend's SNS
 * notification topic, and sends the corresponding notification email for each one.
 *
 * <p>The SNS-to-SQS subscription does not have raw message delivery enabled, so each SQS message
 * body is an SNS notification envelope whose {@code Message} field holds the event JSON as an
 * escaped string. A raw body (the event JSON with no envelope) is also accepted as a fallback,
 * but the SNS-wrapped shape is the primary, tested path.
 *
 * <p>Parsing failures are never caught and suppressed here: they propagate out of
 * {@link #handleRequest} as a {@link NotificationParsingException}, which fails the Lambda
 * invocation so the SQS message is redelivered per the queue's redrive policy. SES send failures
 * propagate the same way, uncaught.
 *
 * <p>{@code SesV2Client} is expensive to construct, so the no-argument constructor — invoked once
 * per Lambda execution environment — builds it once and it is reused across every warm
 * invocation, the same lifecycle {@code ObjectMapper} already has here.
 */
public class NotificationHandler implements RequestHandler<SQSEvent, Void> {

    private static final Logger log = Logger.getLogger(NotificationHandler.class.getName());

    private final ObjectMapper objectMapper;
    private final NotificationEmailContentFactory contentFactory;
    private final EmailSender emailSender;

    public NotificationHandler() {
        this(
                new ObjectMapper().registerModule(new JavaTimeModule()),
                new NotificationEmailContentFactory(),
                new SesEmailSender(buildSesClient(), System.getenv("SES_FROM_EMAIL")));
    }

    NotificationHandler(
            ObjectMapper objectMapper,
            NotificationEmailContentFactory contentFactory,
            EmailSender emailSender) {
        this.objectMapper = objectMapper;
        this.contentFactory = contentFactory;
        this.emailSender = emailSender;
    }

    private static SesV2Client buildSesClient() {
        String region = System.getenv().getOrDefault("AWS_REGION", "us-east-1");
        return SesV2Client.builder().region(Region.of(region)).build();
    }

    @Override
    public Void handleRequest(SQSEvent sqsEvent, Context context) {
        for (SQSEvent.SQSMessage message : sqsEvent.getRecords()) {
            NotificationEvent event = parse(message);
            EmailContent content = contentFactory.create(event);
            emailSender.send(event, content);
            log.info(() -> "Sent notification email eventType=" + event.eventType()
                    + " opportunityId=" + event.opportunityId()
                    + " userId=" + event.userId());
        }
        return null;
    }

    private NotificationEvent parse(SQSEvent.SQSMessage message) {
        String messageId = message.getMessageId();
        JsonNode envelope = parseSqsBody(message.getBody(), messageId);
        String notificationJson = extractNotificationJson(envelope, messageId);
        return parseNotificationEvent(notificationJson, messageId);
    }

    private JsonNode parseSqsBody(String body, String messageId) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new NotificationParsingException(
                    "SQS body parsing failed for messageId=" + messageId, e);
        }
    }

    private String extractNotificationJson(JsonNode envelope, String messageId) {
        if (envelope.has("Message")) {
            return envelope.get("Message").asText();
        }
        if (envelope.has("eventType")) {
            return envelope.toString();
        }
        throw new NotificationParsingException(
                "SNS Message extraction failed for messageId=" + messageId
                        + ": body is neither an SNS envelope with a Message field nor a raw "
                        + "notification event");
    }

    private NotificationEvent parseNotificationEvent(String json, String messageId) {
        try {
            return objectMapper.readValue(json, NotificationEvent.class);
        } catch (Exception e) {
            throw new NotificationParsingException(
                    "notification event parsing failed for messageId=" + messageId, e);
        }
    }
}
