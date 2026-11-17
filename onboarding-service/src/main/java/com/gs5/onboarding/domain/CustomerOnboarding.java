package com.gs5.onboarding.domain;

import com.gs5.onboarding.domain.EligibilityDecision.Eligible;
import com.gs5.onboarding.domain.EligibilityDecision.Ineligible;
import com.gs5.onboarding.domain.OnboardingError.IllegalTransition;
import com.gs5.onboarding.domain.OnboardingError.InvalidValue;
import com.gs5.onboarding.domain.OnboardingEvent.ApplicantVerified;
import com.gs5.onboarding.domain.OnboardingEvent.CustomerOnboarded;
import com.gs5.onboarding.domain.OnboardingEvent.ManualReviewApproved;
import com.gs5.onboarding.domain.OnboardingEvent.ManualReviewRejected;
import com.gs5.onboarding.domain.OnboardingEvent.ManualReviewRequested;
import com.gs5.onboarding.domain.OnboardingEvent.OnboardingBanned;
import com.gs5.onboarding.domain.OnboardingEvent.OnboardingRejected;
import com.gs5.onboarding.domain.OnboardingEvent.OnboardingStarted;
import com.gs5.onboarding.domain.shared.EventSourcedAggregate;
import com.gs5.onboarding.domain.shared.Result;

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
 * Commands return a {@link Result}. A rejected command raises no event and leaves the aggregate untouched, so a
 * failure never needs to be undone.
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

    /** An empty history means there is no such onboarding. */
    public static Optional<CustomerOnboarding> rehydrate(List<OnboardingEvent> history) {
        if (history.isEmpty()) {
            return Optional.empty();
        }
        var onboarding = new CustomerOnboarding();
        onboarding.replay(history);
        return Optional.of(onboarding);
    }

    /** Late or duplicate verdicts for an already decided onboarding are ignored. */
    public Result<CustomerOnboarding, OnboardingError> confirmScreeningCleared(Clock clock) {
        return switch (status) {
            case PENDING_SCREENING, MANUAL_REVIEW -> {
                raise(new CustomerOnboarded(customerId.value(), clock.instant()));
                yield accepted();
            }
            case ONBOARDED, REJECTED, BANNED -> accepted();
            case PENDING_VERIFICATION -> illegalTransition("activate");
        };
    }

    /** Safe to call at any time - only an onboarding that is still waiting past its deadline gets escalated. */
    public Result<CustomerOnboarding, OnboardingError> escalateIfScreeningOverdue(Clock clock) {
        Instant now = clock.instant();
        if (status == PENDING_SCREENING && !now.isBefore(screeningDueAt)) {
            raise(new ManualReviewRequested(customerId.value(), "Screening not completed by " + screeningDueAt, now));
        }
        return accepted();
    }

    public Result<CustomerOnboarding, OnboardingError> approveManualReview(String reviewer, String comment,
                                                                           Clock clock) {
        return requireStatus(MANUAL_REVIEW, "approve")
                .flatMap(_ -> requireText(reviewer, "Reviewer"))
                .map(validReviewer -> {
                    Instant now = clock.instant();
                    raise(new ManualReviewApproved(customerId.value(), validReviewer, comment, now));
                    raise(new CustomerOnboarded(customerId.value(), now));
                    return this;
                });
    }

    public Result<CustomerOnboarding, OnboardingError> rejectManualReview(String reviewer, String reason,
                                                                          Clock clock) {
        return requireStatus(MANUAL_REVIEW, "reject")
                .flatMap(_ -> requireText(reviewer, "Reviewer"))
                .flatMap(validReviewer -> requireText(reason, "Rejection reason")
                        .map(validReason -> {
                            Instant now = clock.instant();
                            raise(new ManualReviewRejected(customerId.value(), validReviewer, validReason, now));
                            raise(new OnboardingRejected(customerId.value(), "Manual review: " + validReason, now));
                            return this;
                        }));
    }

    public Result<CustomerOnboarding, OnboardingError> ban(String reason, Clock clock) {
        if (status == BANNED) {
            return accepted();
        }
        return requireText(reason, "Ban reason").map(validReason -> {
            raise(new OnboardingBanned(customerId.value(), validReason, clock.instant()));
            return this;
        });
    }

    private void verify(EligibilityPolicy policy, ScreeningSla screeningSla, LocalDate today, Instant now) {
        switch (policy.evaluate(applicant, today)) {
            case Eligible() -> raise(new ApplicantVerified(customerId.value(), screeningSla.deadlineFor(now), now));
            case Ineligible(var reason) -> raise(new OnboardingRejected(customerId.value(), reason, now));
        }
    }

    private Result<CustomerOnboarding, OnboardingError> accepted() {
        return Result.success(this);
    }

    private Result<CustomerOnboarding, OnboardingError> illegalTransition(String action) {
        return Result.failure(new IllegalTransition(customerId, status, action));
    }

    private Result<CustomerOnboarding, OnboardingError> requireStatus(OnboardingStatus required, String action) {
        return status == required ? accepted() : illegalTransition(action);
    }

    private static Result<String, OnboardingError> requireText(String text, String label) {
        if (text == null || text.isBlank()) {
            return Result.failure(new InvalidValue(label + " must not be blank"));
        }
        return Result.success(text.strip());
    }

    /** Replays trusted history: events were validated when they were raised, so no validation happens here. */
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
            case ManualReviewApproved _, ManualReviewRejected _ -> {
                // audit only - the state change is the CustomerOnboarded / OnboardingRejected that follows
            }
            case CustomerOnboarded _ -> transitionTo(ONBOARDED, null);
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
