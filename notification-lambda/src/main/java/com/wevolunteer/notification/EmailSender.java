package com.wevolunteer.notification;

/**
 * Delivers an {@link EmailContent} to the volunteer named in a {@link NotificationEvent}.
 *
 * <p>Shaped around the event and rendered content, never an AWS SDK type, so the transport (SES
 * today) can change without touching {@link NotificationHandler} or content generation.
 */
public interface EmailSender {

    void send(NotificationEvent event, EmailContent content);
}
