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
            case WAITLIST_JOINED -> waitlistJoined(event);
            case WAITLIST_LEFT -> waitlistLeft(event);
            case WAITLIST_CANCELLED_BY_ORGANIZATION -> waitlistCancelledByOrganization(event);
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

    private EmailContent waitlistJoined(NotificationEvent event) {
        String subject = "You're on the waitlist: " + event.opportunityTitle();

        String plainTextBody = "Hi " + event.volunteerName() + ",\n\n"
                + "You're on the waitlist for \"" + event.opportunityTitle() + "\" on "
                + event.opportunityDate() + " with " + event.organizationName()
                + ". We'll email you right away if a spot opens up.\n\n"
                + "Thank you for volunteering!\n"
                + "The WeVolunteer Team";

        String htmlBody = "<p>Hi " + escapeHtml(event.volunteerName()) + ",</p>"
                + "<p>You're on the waitlist for <strong>" + escapeHtml(event.opportunityTitle())
                + "</strong> on " + escapeHtml(event.opportunityDate()) + " with "
                + escapeHtml(event.organizationName())
                + ". We'll email you right away if a spot opens up.</p>"
                + "<p>Thank you for volunteering!<br>The WeVolunteer Team</p>";

        return new EmailContent(subject, plainTextBody, htmlBody);
    }

    private EmailContent waitlistLeft(NotificationEvent event) {
        String subject = "You've left the waitlist: " + event.opportunityTitle();

        String plainTextBody = "Hi " + event.volunteerName() + ",\n\n"
                + "You've been removed from the waitlist for \"" + event.opportunityTitle() + "\" on "
                + event.opportunityDate() + " with " + event.organizationName()
                + ". If you change your mind, you're welcome to join the waitlist again anytime.\n\n"
                + "Thank you for volunteering!\n"
                + "The WeVolunteer Team";

        String htmlBody = "<p>Hi " + escapeHtml(event.volunteerName()) + ",</p>"
                + "<p>You've been removed from the waitlist for <strong>"
                + escapeHtml(event.opportunityTitle()) + "</strong> on "
                + escapeHtml(event.opportunityDate()) + " with "
                + escapeHtml(event.organizationName())
                + ". If you change your mind, you're welcome to join the waitlist again anytime.</p>"
                + "<p>Thank you for volunteering!<br>The WeVolunteer Team</p>";

        return new EmailContent(subject, plainTextBody, htmlBody);
    }

    private EmailContent waitlistCancelledByOrganization(NotificationEvent event) {
        String subject = "This opportunity has been cancelled: " + event.opportunityTitle();

        String plainTextBody = "Hi " + event.volunteerName() + ",\n\n"
                + "We're sorry to let you know that \"" + event.opportunityTitle() + "\" on "
                + event.opportunityDate() + " with " + event.organizationName()
                + ", which you were on the waitlist for, has been cancelled by the organization. "
                + "We know this may be disappointing, and we're grateful for your willingness to help.\n\n"
                + "We hope to see you at another opportunity soon!\n"
                + "The WeVolunteer Team";

        String htmlBody = "<p>Hi " + escapeHtml(event.volunteerName()) + ",</p>"
                + "<p>We're sorry to let you know that <strong>" + escapeHtml(event.opportunityTitle())
                + "</strong> on " + escapeHtml(event.opportunityDate()) + " with "
                + escapeHtml(event.organizationName())
                + ", which you were on the waitlist for, has been cancelled by the organization. "
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
