package com.wevolunteer.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("NotificationEmailContentFactory")
class NotificationEmailContentFactoryTest {

    private final NotificationEmailContentFactory factory = new NotificationEmailContentFactory();

    private static NotificationEvent event(
            NotificationEventType eventType,
            String volunteerName,
            String opportunityTitle,
            String opportunityDate,
            String organizationName) {
        return new NotificationEvent(
                eventType,
                "user-1",
                volunteerName,
                "jane@example.com",
                "opp-1",
                opportunityTitle,
                opportunityDate,
                "org-1",
                organizationName,
                Instant.parse("2026-08-04T12:00:00Z"));
    }

    private static NotificationEvent registrationCreatedEvent(
            String volunteerName, String opportunityTitle, String opportunityDate, String organizationName) {
        return event(NotificationEventType.REGISTRATION_CREATED, volunteerName, opportunityTitle,
                opportunityDate, organizationName);
    }

    private static NotificationEvent registrationCancelledEvent(
            String volunteerName, String opportunityTitle, String opportunityDate, String organizationName) {
        return event(NotificationEventType.REGISTRATION_CANCELLED, volunteerName, opportunityTitle,
                opportunityDate, organizationName);
    }

    private static NotificationEvent registrationCancelledByOrganizationEvent(
            String volunteerName, String opportunityTitle, String opportunityDate, String organizationName) {
        return event(NotificationEventType.REGISTRATION_CANCELLED_BY_ORGANIZATION, volunteerName,
                opportunityTitle, opportunityDate, organizationName);
    }

    @Test
    @DisplayName("builds a friendly subject and includes volunteer name, opportunity title, date, and organization")
    void buildsRegistrationCreatedContent() {
        NotificationEvent event = registrationCreatedEvent(
                "Jane Volunteer", "Food Bank Shift", "2026-08-10", "Seattle Food Bank");

        EmailContent content = factory.create(event);

        assertTrue(content.subject().contains("Food Bank Shift"));
        assertTrue(content.plainTextBody().contains("Jane Volunteer"));
        assertTrue(content.plainTextBody().contains("Food Bank Shift"));
        assertTrue(content.plainTextBody().contains("2026-08-10"));
        assertTrue(content.plainTextBody().contains("Seattle Food Bank"));
        assertTrue(content.htmlBody().contains("Jane Volunteer"));
        assertTrue(content.htmlBody().contains("Food Bank Shift"));
        assertTrue(content.htmlBody().contains("2026-08-10"));
        assertTrue(content.htmlBody().contains("Seattle Food Bank"));
    }

    @Test
    @DisplayName("never includes userId, opportunityId, or organizationId in generated content, for any event type")
    void neverIncludesInternalIdentifiers() {
        NotificationEvent[] events = {
                registrationCreatedEvent("Jane Volunteer", "Food Bank Shift", "2026-08-10", "Seattle Food Bank"),
                registrationCancelledEvent("Jane Volunteer", "Food Bank Shift", "2026-08-10", "Seattle Food Bank"),
                registrationCancelledByOrganizationEvent(
                        "Jane Volunteer", "Food Bank Shift", "2026-08-10", "Seattle Food Bank"),
        };

        for (NotificationEvent event : events) {
            EmailContent content = factory.create(event);

            assertFalse(content.subject().contains(event.userId()));
            assertFalse(content.plainTextBody().contains(event.userId()));
            assertFalse(content.plainTextBody().contains(event.opportunityId()));
            assertFalse(content.plainTextBody().contains(event.organizationId()));
            assertFalse(content.htmlBody().contains(event.userId()));
            assertFalse(content.htmlBody().contains(event.opportunityId()));
            assertFalse(content.htmlBody().contains(event.organizationId()));
        }
    }

    @Test
    @DisplayName("escapes HTML-significant characters in volunteer name, opportunity title, date, and organization name")
    void escapesHtmlInDynamicValues() {
        NotificationEvent event = registrationCreatedEvent(
                "Jane <script>alert('x')</script>",
                "Bake & Sell \"Charity\" Event",
                "2026-08-10 <tag>",
                "O'Brien's <Shelter>");

        EmailContent content = factory.create(event);

        assertFalse(content.htmlBody().contains("<script>"));
        assertTrue(content.htmlBody().contains("&lt;script&gt;"));
        assertTrue(content.htmlBody().contains("Bake &amp; Sell &quot;Charity&quot; Event"));
        assertTrue(content.htmlBody().contains("2026-08-10 &lt;tag&gt;"));
        assertTrue(content.htmlBody().contains("O&#39;Brien&#39;s &lt;Shelter&gt;"));
    }

    @Test
    @DisplayName("builds REGISTRATION_CANCELLED content explaining the spot was released")
    void buildsRegistrationCancelledContent() {
        NotificationEvent event = registrationCancelledEvent(
                "Jane Volunteer", "Food Bank Shift", "2026-08-10", "Seattle Food Bank");

        EmailContent content = factory.create(event);

        assertTrue(content.subject().toLowerCase().contains("cancelled"));
        assertTrue(content.subject().contains("Food Bank Shift"));
        assertTrue(content.plainTextBody().contains("Jane Volunteer"));
        assertTrue(content.plainTextBody().contains("Food Bank Shift"));
        assertTrue(content.plainTextBody().contains("2026-08-10"));
        assertTrue(content.plainTextBody().contains("Seattle Food Bank"));
        assertTrue(content.plainTextBody().toLowerCase().contains("released"));
        assertTrue(content.htmlBody().contains("Jane Volunteer"));
        assertTrue(content.htmlBody().contains("Food Bank Shift"));
        assertTrue(content.htmlBody().contains("2026-08-10"));
        assertTrue(content.htmlBody().contains("Seattle Food Bank"));
        assertTrue(content.htmlBody().toLowerCase().contains("released"));
    }

    @Test
    @DisplayName("escapes HTML-significant characters in REGISTRATION_CANCELLED content")
    void escapesHtmlInRegistrationCancelledContent() {
        NotificationEvent event = registrationCancelledEvent(
                "Jane <script>alert('x')</script>",
                "Bake & Sell \"Charity\" Event",
                "2026-08-10 <tag>",
                "O'Brien's <Shelter>");

        EmailContent content = factory.create(event);

        assertFalse(content.htmlBody().contains("<script>"));
        assertTrue(content.htmlBody().contains("&lt;script&gt;"));
        assertTrue(content.htmlBody().contains("Bake &amp; Sell &quot;Charity&quot; Event"));
        assertTrue(content.htmlBody().contains("2026-08-10 &lt;tag&gt;"));
        assertTrue(content.htmlBody().contains("O&#39;Brien&#39;s &lt;Shelter&gt;"));
    }

    @Test
    @DisplayName("builds REGISTRATION_CANCELLED_BY_ORGANIZATION content with empathetic wording")
    void buildsRegistrationCancelledByOrganizationContent() {
        NotificationEvent event = registrationCancelledByOrganizationEvent(
                "Jane Volunteer", "Food Bank Shift", "2026-08-10", "Seattle Food Bank");

        EmailContent content = factory.create(event);

        assertTrue(content.subject().toLowerCase().contains("cancelled"));
        assertTrue(content.subject().contains("Food Bank Shift"));
        assertTrue(content.plainTextBody().contains("Jane Volunteer"));
        assertTrue(content.plainTextBody().contains("Food Bank Shift"));
        assertTrue(content.plainTextBody().contains("2026-08-10"));
        assertTrue(content.plainTextBody().contains("Seattle Food Bank"));
        assertTrue(content.plainTextBody().toLowerCase().contains("sorry"));
        assertTrue(content.htmlBody().contains("Jane Volunteer"));
        assertTrue(content.htmlBody().contains("Food Bank Shift"));
        assertTrue(content.htmlBody().contains("2026-08-10"));
        assertTrue(content.htmlBody().contains("Seattle Food Bank"));
        assertTrue(content.htmlBody().toLowerCase().contains("sorry"));
    }

    @Test
    @DisplayName("escapes HTML-significant characters in REGISTRATION_CANCELLED_BY_ORGANIZATION content")
    void escapesHtmlInRegistrationCancelledByOrganizationContent() {
        NotificationEvent event = registrationCancelledByOrganizationEvent(
                "Jane <script>alert('x')</script>",
                "Bake & Sell \"Charity\" Event",
                "2026-08-10 <tag>",
                "O'Brien's <Shelter>");

        EmailContent content = factory.create(event);

        assertFalse(content.htmlBody().contains("<script>"));
        assertTrue(content.htmlBody().contains("&lt;script&gt;"));
        assertTrue(content.htmlBody().contains("Bake &amp; Sell &quot;Charity&quot; Event"));
        assertTrue(content.htmlBody().contains("2026-08-10 &lt;tag&gt;"));
        assertTrue(content.htmlBody().contains("O&#39;Brien&#39;s &lt;Shelter&gt;"));
    }
}
