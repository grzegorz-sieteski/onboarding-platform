package com.gs5.onboarding.domain;

import com.gs5.onboarding.domain.OnboardingEvent.ApplicantVerified;
import com.gs5.onboarding.domain.OnboardingEvent.CustomerOnboarded;
import com.gs5.onboarding.domain.OnboardingEvent.ManualReviewApproved;
import com.gs5.onboarding.domain.OnboardingEvent.ManualReviewRejected;
import com.gs5.onboarding.domain.OnboardingEvent.ManualReviewRequested;
import com.gs5.onboarding.domain.OnboardingEvent.OnboardingBanned;
import com.gs5.onboarding.domain.OnboardingEvent.OnboardingRejected;
import com.gs5.onboarding.domain.OnboardingEvent.OnboardingStarted;
import com.gs5.onboarding.domain.shared.EventSourcedAggregate;
import com.gs5.onboarding.domain.shared.InvalidValueException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.gs5.onboarding.domain.OnboardingStatus.BANNED;
import static com.gs5.onboarding.domain.OnboardingStatus.MANUAL_REVIEW;
import static com.gs5.onboarding.domain.OnboardingStatus.ONBOARDED;
import static com.gs5.onboarding.domain.OnboardingStatus.PENDING_SCREENING;
import static com.gs5.onboarding.domain.OnboardingStatus.PENDING_VERIFICATION;
import static com.gs5.onboarding.domain.OnboardingStatus.REJECTED;

/**
 * Aggregate root: the onboarding process of a single customer.
 *
 * <pre>
 * start ─▶ PENDING_VERIFICATION ─┬─▶ REJECTED                                     (eligibility policy failed)
 *                                └─▶ PENDING_SCREENING ─┬───────────────▶ ONBOARDED (screening cleared)
 *                                                       └─▶ MANUAL_REVIEW ─┬─▶ ONBOARDED (approved / late clearance)
 *                                                          (deadline passed) └─▶ REJECTED  (rejected by officer)
 * any non-banned status ─────────────────────────────────────────────────▶ BANNED    (client banned)
 * </pre>
 * Invariants:
 * <ul>
 *     <li>a customer becomes {@code ONBOARDED} only after screening cleared them or an officer approved them,</li>
 *     <li>a screening that does not answer before its deadline is escalated to manual review - exactly once,</li>
 *     <li>a ban is final: it wins over any later clearance or approval. Verdicts come from different topics,
 *     so Kafka gives no ordering between them - the state machine makes the outcome order-independent,</li>
 *     <li>repeating a ban, a clearance or an escalation has no effect (idempotency for at-least-once delivery).</li>
 * </ul>
 */
public final class CustomerOnboarding extends EventSourcedAggregate<OnboardingEvent> {

    private CustomerId customerId;
    private Applicant applicant;
    private OnboardingStatus status;
    private String statusReason;
    private Instant screeningDueAt;

    private CustomerOnboarding() {
    }

    public static CustomerOnboarding start(CustomerId customerId, Applicant applicant, EligibilityPolicy policy,
                                           ScreeningSla screeningSla, Clock clock) {
        var onboarding = new CustomerOnboarding();
        Instant now = clock.instant();
        onboarding.raise(new OnboardingStarted(customerId.value(), applicant.name().firstName(),
                applicant.name().lastName(), applicant.email().value(), applicant.dateOfBirth(), now));
        onboarding.verify(policy, screeningSla, LocalDate.ofInstant(now, clock.getZone()), now);
        return onboarding;
    }

    public static CustomerOnboarding rehydrate(List<OnboardingEvent> history) {
        if (history.isEmpty()) {
            throw new IllegalArgumentException("Cannot rehydrate an onboarding from an empty history");
        }
        var onboarding = new CustomerOnboarding();
        onboarding.replay(history);
        return onboarding;
    }

    /** Late or duplicate verdicts for an already decided onboarding are ignored. */
    public void confirmScreeningCleared(Clock clock) {
        switch (status) {
            case PENDING_SCREENING, MANUAL_REVIEW -> raise(new CustomerOnboarded(customerId.value(), clock.instant()));
            case ONBOARDED, REJECTED, BANNED -> {
                // already decided
            }
            case PENDING_VERIFICATION -> throw new IllegalOnboardingTransitionException(customerId, status, "activate");
        }
    }

    /** Safe to call at any time - only an onboarding that is still waiting past its deadline gets escalated. */
    public void escalateIfScreeningOverdue(Clock clock) {
        Instant now = clock.instant();
        if (status != PENDING_SCREENING || now.isBefore(screeningDueAt)) {
            return;
        }
        raise(new ManualReviewRequested(customerId.value(),
                "Screening not completed by " + screeningDueAt, now));
    }

    public void approveManualReview(String reviewer, String comment, Clock clock) {
        requireStatus(MANUAL_REVIEW, "approve");
        Instant now = clock.instant();
        raise(new ManualReviewApproved(customerId.value(), requireText(reviewer, "Reviewer"), comment, now));
        raise(new CustomerOnboarded(customerId.value(), now));
    }

    public void rejectManualReview(String reviewer, String reason, Clock clock) {
        requireStatus(MANUAL_REVIEW, "reject");
        Instant now = clock.instant();
        String rejectionReason = requireText(reason, "Rejection reason");
        raise(new ManualReviewRejected(customerId.value(), requireText(reviewer, "Reviewer"), rejectionReason, now));
        raise(new OnboardingRejected(customerId.value(), "Manual review: " + rejectionReason, now));
    }

    public void ban(String reason, Clock clock) {
        if (status == BANNED) {
            return;
        }
        raise(new OnboardingBanned(customerId.value(), requireText(reason, "Ban reason"), clock.instant()));
    }

    private void verify(EligibilityPolicy policy, ScreeningSla screeningSla, LocalDate today, Instant now) {
        EligibilityDecision decision = policy.evaluate(applicant, today);
        if (decision.eligible()) {
            raise(new ApplicantVerified(customerId.value(), screeningSla.deadlineFor(now), now));
        } else {
            raise(new OnboardingRejected(customerId.value(), decision.rejectionReason(), now));
        }
    }

    private void requireStatus(OnboardingStatus required, String action) {
        if (status != required) {
            throw new IllegalOnboardingTransitionException(customerId, status, action);
        }
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new InvalidValueException(label + " must not be blank");
        }
        return value.strip();
    }

    @Override
    protected void apply(OnboardingEvent event) {
        switch (event) {
            case OnboardingStarted started -> {
                customerId = new CustomerId(started.customerId());
                applicant = new Applicant(new PersonName(started.firstName(), started.lastName()),
                        new EmailAddress(started.email()), started.dateOfBirth());
                status = PENDING_VERIFICATION;
            }
            case ApplicantVerified verified -> {
                screeningDueAt = verified.screeningDueAt();
                transitionTo(PENDING_SCREENING, null);
            }
            case ManualReviewRequested escalated -> transitionTo(MANUAL_REVIEW, escalated.reason());
            case ManualReviewApproved approved -> {
                // audit only - CustomerOnboarded follows
            }
            case ManualReviewRejected rejected -> {
                // audit only - OnboardingRejected follows
            }
            case CustomerOnboarded onboarded -> transitionTo(ONBOARDED, null);
            case OnboardingRejected rejected -> transitionTo(REJECTED, rejected.reason());
            case OnboardingBanned banned -> transitionTo(BANNED, banned.reason());
        }
    }

    private void transitionTo(OnboardingStatus newStatus, String reason) {
        status = newStatus;
        statusReason = reason;
    }

    public CustomerId customerId() {
        return customerId;
    }

    public Applicant applicant() {
        return applicant;
    }

    public OnboardingStatus status() {
        return status;
    }

    public Optional<String> statusReason() {
        return Optional.ofNullable(statusReason);
    }

    public Optional<Instant> screeningDueAt() {
        return Optional.ofNullable(screeningDueAt);
    }
}
