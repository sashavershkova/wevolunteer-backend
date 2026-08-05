package com.wevolunteer.backend.service;

import com.wevolunteer.backend.model.Opportunity;

import java.time.LocalDate;

/**
 * Single source of truth for whether an opportunity's event date has already passed, so
 * {@link RegistrationService} and {@link WaitlistService} apply the same rule instead of
 * duplicating slightly different comparisons. An opportunity remains eligible for registration
 * and waitlisting throughout its event date and only becomes expired starting the next calendar
 * day, evaluated against the JVM's default time zone.
 */
final class OpportunityDatePolicy {

    private OpportunityDatePolicy() {
    }

    static boolean isPast(Opportunity opportunity) {
        return isPast(opportunity.date());
    }

    static boolean isPast(String opportunityDate) {
        return LocalDate.parse(opportunityDate).isBefore(LocalDate.now());
    }
}
