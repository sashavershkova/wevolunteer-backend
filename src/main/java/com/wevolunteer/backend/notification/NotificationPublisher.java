package com.wevolunteer.backend.notification;

/**
 * Publishes notification events for downstream delivery (email, etc.).
 *
 * <p>Business services depend on this interface, never on any AWS SDK type, so the transport
 * (SNS today) can change without touching business logic.
 */
public interface NotificationPublisher {

    void publish(NotificationEvent event);
}
