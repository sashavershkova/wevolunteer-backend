package com.wevolunteer.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("SesEmailSender")
class SesEmailSenderTest {

    private static final String FROM_EMAIL = "notifications@wevolunteer.example";

    @Mock
    private SesV2Client sesClient;

    private SesEmailSender sender;

    @BeforeEach
    void setUp() {
        sender = new SesEmailSender(sesClient, FROM_EMAIL);
    }

    private static NotificationEvent sampleEvent() {
        return new NotificationEvent(
                NotificationEventType.REGISTRATION_CREATED,
                "user-1",
                "Jane Volunteer",
                "jane@example.com",
                "opp-1",
                "Food Bank Shift",
                "2026-08-10",
                "org-1",
                "Seattle Food Bank",
                Instant.parse("2026-08-04T12:00:00Z"));
    }

    @Test
    @DisplayName("sends to the volunteer's email with the configured From address and both content alternatives")
    void sendsExpectedRequest() {
        EmailContent content = new EmailContent("Subject line", "Plain text body", "<p>HTML body</p>");

        sender.send(sampleEvent(), content);

        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesClient).sendEmail(captor.capture());

        SendEmailRequest request = captor.getValue();
        assertEquals(FROM_EMAIL, request.fromEmailAddress());
        assertEquals(List.of("jane@example.com"), request.destination().toAddresses());
        assertEquals("Subject line", request.content().simple().subject().data());
        assertEquals("Plain text body", request.content().simple().body().text().data());
        assertEquals("<p>HTML body</p>", request.content().simple().body().html().data());
    }

    @Test
    @DisplayName("rejects a null From address at construction time")
    void rejectsNullFromAddress() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new SesEmailSender(sesClient, null));

        assertEquals(true, ex.getMessage().contains("SES_FROM_EMAIL"));
    }

    @Test
    @DisplayName("rejects a blank From address at construction time")
    void rejectsBlankFromAddress() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new SesEmailSender(sesClient, "   "));

        assertEquals(true, ex.getMessage().contains("SES_FROM_EMAIL"));
    }
}
