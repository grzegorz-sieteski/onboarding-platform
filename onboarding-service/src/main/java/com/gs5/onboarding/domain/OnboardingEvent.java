package com.gs5.onboarding.domain;

import com.gs5.onboarding.domain.shared.DomainEvent;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Events of the {@link CustomerOnboarding} aggregate. These are <b>internal</b> domain events stored in the event store;
 * what leaves the service is a separate, versioned published language (see infrastructure.messaging).
 */
public sealed interface OnboardingEvent extends DomainEvent {

    UUID customerId();

    record OnboardingStarted(UUID customerId, String firstName, String lastName, String email,
                             LocalDate dateOfBirth, Instant occurredAt) implements OnboardingEvent {
    }

    /** Submitted data satisfies the eligibility policy - the customer now awaits compliance screening. */
    record ApplicantVerified(UUID customerId, Instant screeningDueAt, Instant occurredAt) implements OnboardingEvent {
    }

    /** Screening did not answer in time - a compliance officer has to decide. */
    record ManualReviewRequested(UUID customerId, String reason, Instant occurredAt) implements OnboardingEvent {
    }

    /** Audit of the officer's decision; the resulting state change is the {@link CustomerOnboarded} that follows. */
    record ManualReviewApproved(UUID customerId, String reviewer, String comment, Instant occurredAt)
            implements OnboardingEvent {
    }

    /** Audit of the officer's decision; the resulting state change is the {@link OnboardingRejected} that follows. */
    record ManualReviewRejected(UUID customerId, String reviewer, String reason, Instant occurredAt)
            implements OnboardingEvent {
    }

    /** The customer is active - cleared by screening or approved in manual review. */
    record CustomerOnboarded(UUID customerId, Instant occurredAt) implements OnboardingEvent {
    }

    record OnboardingRejected(UUID customerId, String reason, Instant occurredAt) implements OnboardingEvent {
    }

    record OnboardingBanned(UUID customerId, String reason, Instant occurredAt) implements OnboardingEvent {
    }
}
