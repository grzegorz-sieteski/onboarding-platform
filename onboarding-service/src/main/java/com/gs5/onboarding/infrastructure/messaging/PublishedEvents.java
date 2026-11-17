package com.gs5.onboarding.infrastructure.messaging;

import java.time.LocalDate;

/**
 * Published language of the Onboarding context - the versioned contracts other contexts may rely on.
 * Kept separate from internal domain events so the domain can evolve without breaking consumers.
 */
final class PublishedEvents {

    static final String APPLICANT_VERIFIED = "ApplicantVerified";
    static final String CUSTOMER_ONBOARDED = "CustomerOnboarded";
    static final String ONBOARDING_BANNED = "OnboardingBanned";

    private PublishedEvents() {
    }

    /** Data verified, customer awaits compliance screening. */
    record ApplicantVerifiedV1(String customerId, String firstName, String lastName, LocalDate dateOfBirth) {
    }

    /** Customer cleared by screening and active. */
    record CustomerOnboardedV1(String customerId, String firstName, String lastName, LocalDate dateOfBirth) {
    }

    record OnboardingBannedV1(String customerId, String reason) {
    }
}
