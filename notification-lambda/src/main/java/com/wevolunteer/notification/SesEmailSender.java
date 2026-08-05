package com.wevolunteer.notification;

import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

/**
 * Sends {@link EmailContent} via Amazon SES.
 *
 * <p>The {@code SesV2Client} passed in is expected to be reused across warm Lambda invocations —
 * built once, outside the per-message code path, by whoever constructs this class.
 *
 * <p>SES SDK exceptions are never caught here: they propagate out of {@link #send}, so a failed
 * send fails the Lambda invocation and the SQS message is retried per the queue's redrive policy.
 */
public class SesEmailSender implements EmailSender {

    private static final String CHARSET = "UTF-8";

    private final SesV2Client sesClient;
    private final String fromEmail;

    public SesEmailSender(SesV2Client sesClient, String fromEmail) {
        if (fromEmail == null || fromEmail.isBlank()) {
            throw new IllegalStateException(
                    "SES_FROM_EMAIL is not configured. Set the SES_FROM_EMAIL environment "
                            + "variable to a verified SES sender address before starting the "
                            + "Lambda.");
        }
        this.sesClient = sesClient;
        this.fromEmail = fromEmail;
    }

    @Override
    public void send(NotificationEvent event, EmailContent content) {
        Message message = Message.builder()
                .subject(Content.builder().data(content.subject()).charset(CHARSET).build())
                .body(Body.builder()
                        .text(Content.builder().data(content.plainTextBody()).charset(CHARSET).build())
                        .html(Content.builder().data(content.htmlBody()).charset(CHARSET).build())
                        .build())
                .build();

        SendEmailRequest request = SendEmailRequest.builder()
                .fromEmailAddress(fromEmail)
                .destination(Destination.builder().toAddresses(event.volunteerEmail()).build())
                .content(software.amazon.awssdk.services.sesv2.model.EmailContent.builder()
                        .simple(message)
                        .build())
                .build();

        sesClient.sendEmail(request);
    }
}
