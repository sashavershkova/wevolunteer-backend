package com.wevolunteer.notification;

/**
 * Thrown when an SQS message cannot be parsed into a {@link NotificationEvent}, at any of the
 * three parsing stages (SQS body parsing, SNS Message extraction, notification event parsing).
 *
 * <p>Always propagates out of the handler rather than being caught and swallowed, so the SQS
 * invocation fails and the message becomes eligible for redelivery.
 */
public class NotificationParsingException extends RuntimeException {

    public NotificationParsingException(String message) {
        super(message);
    }

    public NotificationParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
