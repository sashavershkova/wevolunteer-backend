package com.wevolunteer.notification;

/**
 * Renders {@link EmailContent} for a {@link NotificationEvent}.
 *
 * <p>Generated content never includes {@code userId}, {@code opportunityId}, or
 * {@code organizationId} — those are internal identifiers, not something a volunteer should see
 * in their inbox.
 */
public class NotificationEmailContentFactory {

    public EmailContent create(NotificationEvent event) {
        return switch (event.eventType()) {
            case REGISTRATION_CREATED -> registrationCreated(event);
            case REGISTRATION_CANCELLED -> registrationCancelled(event);
            case REGISTRATION_CANCELLED_BY_ORGANIZATION -> registrationCancelledByOrganization(event);
        };
    }

    private EmailContent registrationCreated(NotificationEvent event) {
        String subject = "You're registered: " + event.opportunityTitle();

        String plainTextBody = "Hi " + event.volunteerName() + ",\n\n"
                + "You're confirmed for \"" + event.opportunityTitle() + "\" on "
                + event.opportunityDate() + " with " + event.organizationName() + ".\n\n"
                + "Thank you for volunteering!\n"
                + "The WeVolunteer Team";

        String htmlBody = "<p>Hi " + escapeHtml(event.volunteerName()) + ",</p>"
                + "<p>You're confirmed for <strong>" + escapeHtml(event.opportunityTitle())
                + "</strong> on " + escapeHtml(event.opportunityDate()) + " with "
                + escapeHtml(event.organizationName()) + ".</p>"
                + "<p>Thank you for volunteering!<br>The WeVolunteer Team</p>";

        return new EmailContent(subject, plainTextBody, htmlBody);
    }

    private EmailContent registrationCancelled(NotificationEvent event) {
        String subject = "Your registration has been cancelled: " + event.opportunityTitle();

        String plainTextBody = "Hi " + event.volunteerName() + ",\n\n"
                + "You've successfully cancelled your registration for \"" + event.opportunityTitle()
                + "\" on " + event.opportunityDate() + " with " + event.organizationName()
                + ". Your spot has been released for other volunteers.\n\n"
                + "Thank you for volunteering!\n"
                + "The WeVolunteer Team";

        String htmlBody = "<p>Hi " + escapeHtml(event.volunteerName()) + ",</p>"
                + "<p>You've successfully cancelled your registration for <strong>"
                + escapeHtml(event.opportunityTitle()) + "</strong> on "
                + escapeHtml(event.opportunityDate()) + " with " + escapeHtml(event.organizationName())
                + ". Your spot has been released for other volunteers.</p>"
                + "<p>Thank you for volunteering!<br>The WeVolunteer Team</p>";

        return new EmailContent(subject, plainTextBody, htmlBody);
    }

    private EmailContent registrationCancelledByOrganization(NotificationEvent event) {
        String subject = "This opportunity has been cancelled: " + event.opportunityTitle();

        String plainTextBody = "Hi " + event.volunteerName() + ",\n\n"
                + "We're sorry to let you know that \"" + event.opportunityTitle() + "\" on "
                + event.opportunityDate() + " with " + event.organizationName()
                + " has been cancelled by the organization. We know this may be disappointing, "
                + "and we're grateful for your willingness to help.\n\n"
                + "We hope to see you at another opportunity soon!\n"
                + "The WeVolunteer Team";

        String htmlBody = "<p>Hi " + escapeHtml(event.volunteerName()) + ",</p>"
                + "<p>We're sorry to let you know that <strong>" + escapeHtml(event.opportunityTitle())
                + "</strong> on " + escapeHtml(event.opportunityDate()) + " with "
                + escapeHtml(event.organizationName()) + " has been cancelled by the organization. "
                + "We know this may be disappointing, and we're grateful for your willingness to "
                + "help.</p>"
                + "<p>We hope to see you at another opportunity soon!<br>The WeVolunteer Team</p>";

        return new EmailContent(subject, plainTextBody, htmlBody);
    }

    private static String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
