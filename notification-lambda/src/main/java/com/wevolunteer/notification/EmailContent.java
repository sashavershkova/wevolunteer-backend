package com.wevolunteer.notification;

/** The rendered subject and body alternatives for one notification email. */
public record EmailContent(String subject, String plainTextBody, String htmlBody) {}
