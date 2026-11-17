package com.gs5.onboarding.domain;

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
import com.gs5.onboarding.domain.shared.Result;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class CustomerOnboardingTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneOffset.UTC);
    private static final Clock AFTER_DEADLINE = Clock.offset(CLOCK, Duration.ofMinutes(16));
    private static final EligibilityPolicy POLICY = new EligibilityPolicy();
    private static final ScreeningSla SLA = new ScreeningSla(Duration.ofMinutes(15));
    private static final LocalDate ADULT = LocalDate.parse("1990-05-01");
    private static final LocalDate MINOR = LocalDate.parse("2010-01-01");

    @Test
    void verifiedAdultAwaitsScreening() {
        CustomerOnboarding onboarding = startFor(ADULT);

        assertThat(onboarding.uncommittedEvents())
                .hasExactlyElementsOfTypes(OnboardingStarted.class, ApplicantVerified.class);
        assertThat(onboarding.status()).isEqualTo(OnboardingStatus.PENDING_SCREENING);
        assertThat(onboarding.screeningDueAt()).hasValue(Instant.parse("2026-11-17T10:15:00Z"));
    }

    @Test
    void rejectsAMinor() {
        CustomerOnboarding onboarding = startFor(MINOR);

        assertThat(onboarding.uncommittedEvents())
                .hasExactlyElementsOfTypes(OnboardingStarted.class, OnboardingRejected.class);
        assertThat(onboarding.status()).isEqualTo(OnboardingStatus.REJECTED);
        assertThat(onboarding.statusReason()).hasValueSatisfying(reason -> assertThat(reason).contains("18"));
    }

    @Test
    void activatesCustomerOnlyAfterScreeningCleared() {
        CustomerOnboarding onboarding = committed(startFor(ADULT));

        Result<CustomerOnboarding, OnboardingError> result = onboarding.confirmScreeningCleared(CLOCK);

        assertThat(valueOf(result)).isSameAs(onboarding);
        assertThat(onboarding.uncommittedEvents()).singleElement().isInstanceOf(CustomerOnboarded.class);
        assertThat(onboarding.status()).isEqualTo(OnboardingStatus.ONBOARDED);
    }

    @Test
    void clearanceIsIdempotent() {
        CustomerOnboarding onboarding = committed(startFor(ADULT));
        onboarding.confirmScreeningCleared(CLOCK);
        committed(onboarding);

        Result<CustomerOnboarding, OnboardingError> result = onboarding.confirmScreeningCleared(CLOCK);

        assertThat(result.<Boolean>fold(_ -> true, _ -> false)).isTrue();
        assertThat(onboarding.uncommittedEvents()).isEmpty();
    }

    @Test
    void clearanceDoesNotReviveRejectedOnboarding() {
        CustomerOnboarding onboarding = committed(startFor(MINOR));

        onboarding.confirmScreeningCleared(CLOCK);

        assertThat(onboarding.uncommittedEvents()).isEmpty();
        assertThat(onboarding.status()).isEqualTo(OnboardingStatus.REJECTED);
    }

    @Test
    void doesNotEscalateBeforeDeadline() {
        CustomerOnboarding onboarding = committed(startFor(ADULT));

        onboarding.escalateIfScreeningOverdue(Clock.offset(CLOCK, Duration.ofMinutes(14)));

        assertThat(onboarding.uncommittedEvents()).isEmpty();
        assertThat(onboarding.status()).isEqualTo(OnboardingStatus.PENDING_SCREENING);
    }

    @Test
    void escalatesToManualReviewOnceDeadlinePassed() {
        CustomerOnboarding onboarding = committed(startFor(ADULT));

        onboarding.escalateIfScreeningOverdue(AFTER_DEADLINE);

        assertThat(onboarding.uncommittedEvents()).singleElement().isInstanceOf(ManualReviewRequested.class);
        assertThat(onboarding.status()).isEqualTo(OnboardingStatus.MANUAL_REVIEW);
    }

    @Test
    void escalationIsIdempotent() {
        CustomerOnboarding onboarding = escalated();

        onboarding.escalateIfScreeningOverdue(AFTER_DEADLINE);

        assertThat(onboarding.uncommittedEvents()).isEmpty();
    }

    @Test
    void doesNotEscalateDecidedOnboarding() {
        CustomerOnboarding onboarding = committed(startFor(ADULT));
        onboarding.confirmScreeningCleared(CLOCK);
        committed(onboarding);

        onboarding.escalateIfScreeningOverdue(AFTER_DEADLINE);

        assertThat(onboarding.uncommittedEvents()).isEmpty();
        assertThat(onboarding.status()).isEqualTo(OnboardingStatus.ONBOARDED);
    }

    @Test
    void officerApprovesManualReview() {
        CustomerOnboarding onboarding = escalated();

        Result<CustomerOnboarding, OnboardingError> result =
                onboarding.approveManualReview("officer.anna", "Documents verified by phone", AFTER_DEADLINE);

        assertThat(valueOf(result)).isSameAs(onboarding);
        assertThat(onboarding.uncommittedEvents())
                .hasExactlyElementsOfTypes(ManualReviewApproved.class, CustomerOnboarded.class);
        assertThat(onboarding.status()).isEqualTo(OnboardingStatus.ONBOARDED);
    }

    @Test
    void officerRejectsManualReview() {
        CustomerOnboarding onboarding = escalated();

        onboarding.rejectManualReview("officer.anna", "Identity could not be confirmed", AFTER_DEADLINE);

        assertThat(onboarding.uncommittedEvents())
                .hasExactlyElementsOfTypes(ManualReviewRejected.class, OnboardingRejected.class);
        assertThat(onboarding.status()).isEqualTo(OnboardingStatus.REJECTED);
        assertThat(onboarding.statusReason()).hasValue("Manual review: Identity could not be confirmed");
    }

    @Test
    void cannotDecideManualReviewThatWasNotRequested() {
        CustomerOnboarding onboarding = committed(startFor(ADULT));

        Result<CustomerOnboarding, OnboardingError> result = onboarding.approveManualReview("officer.anna", null, CLOCK);

        assertThat(failureOf(result)).isEqualTo(
                new IllegalTransition(onboarding.customerId(), OnboardingStatus.PENDING_SCREENING, "approve"));
        assertThat(onboarding.uncommittedEvents()).isEmpty();
    }

    @Test
    void rejectedCommandRaisesNoEventAndLeavesTheOnboardingUntouched() {
        CustomerOnboarding onboarding = escalated();

        assertThat(failureOf(onboarding.approveManualReview("  ", "ok", AFTER_DEADLINE)))
                .isInstanceOf(InvalidValue.class);
        assertThat(failureOf(onboarding.rejectManualReview("officer.anna", null, AFTER_DEADLINE)))
                .isInstanceOf(InvalidValue.class);

        assertThat(onboarding.uncommittedEvents()).isEmpty();
        assertThat(onboarding.status()).isEqualTo(OnboardingStatus.MANUAL_REVIEW);
    }

    @Test
    void banRequiresAReason() {
        CustomerOnboarding onboarding = committed(startFor(ADULT));

        assertThat(failureOf(onboarding.ban(" ", CLOCK))).isInstanceOf(InvalidValue.class);

        assertThat(onboarding.uncommittedEvents()).isEmpty();
        assertThat(onboarding.status()).isEqualTo(OnboardingStatus.PENDING_SCREENING);
    }

    @Test
    void lateClearanceResolvesManualReview() {
        CustomerOnboarding onboarding = escalated();

        onboarding.confirmScreeningCleared(AFTER_DEADLINE);

        assertThat(onboarding.uncommittedEvents()).singleElement().isInstanceOf(CustomerOnboarded.class);
        assertThat(onboarding.status()).isEqualTo(OnboardingStatus.ONBOARDED);
    }

    @Test
    void lateBanWinsOverManualReview() {
        CustomerOnboarding onboarding = escalated();

        onboarding.ban("Surname on blacklist", AFTER_DEADLINE);

        assertThat(onboarding.status()).isEqualTo(OnboardingStatus.BANNED);
        assertThat(failureOf(onboarding.approveManualReview("officer.anna", null, AFTER_DEADLINE)))
                .isInstanceOf(IllegalTransition.class);
    }

    @Test
    void bansCustomerAwaitingScreening() {
        CustomerOnboarding onboarding = committed(startFor(ADULT));

        onboarding.ban("Surname on blacklist", CLOCK);

        assertThat(onboarding.uncommittedEvents()).singleElement().isInstanceOf(OnboardingBanned.class);
        assertThat(onboarding.status()).isEqualTo(OnboardingStatus.BANNED);
        assertThat(onboarding.statusReason()).hasValue("Surname on blacklist");
    }

    @Test
    void bansAlreadyActiveCustomer() {
        CustomerOnboarding onboarding = committed(startFor(ADULT));
        onboarding.confirmScreeningCleared(CLOCK);
        committed(onboarding);

        onboarding.ban("Manual ban: fraud investigation", CLOCK);

        assertThat(onboarding.status()).isEqualTo(OnboardingStatus.BANNED);
    }

    @Test
    void banWinsOverClearanceArrivingLater() {
        CustomerOnboarding onboarding = committed(startFor(ADULT));
        onboarding.ban("Manual ban: fraud investigation", CLOCK);
        committed(onboarding);

        onboarding.confirmScreeningCleared(CLOCK);

        assertThat(onboarding.uncommittedEvents()).isEmpty();
        assertThat(onboarding.status()).isEqualTo(OnboardingStatus.BANNED);
    }

    @Test
    void banningTwiceIsIdempotent() {
        CustomerOnboarding onboarding = committed(startFor(ADULT));
        onboarding.ban("Surname on blacklist", CLOCK);
        committed(onboarding);

        onboarding.ban("Surname on blacklist", CLOCK);

        assertThat(onboarding.uncommittedEvents()).isEmpty();
    }

    @Test
    void rehydratesStateAndVersionFromHistory() {
        UUID id = UUID.randomUUID();
        List<OnboardingEvent> history = List.of(
                new OnboardingStarted(id, "Jan", "Nowak", "jan@example.com", ADULT, CLOCK.instant()),
                new ApplicantVerified(id, SLA.deadlineFor(CLOCK.instant()), CLOCK.instant()),
                new CustomerOnboarded(id, CLOCK.instant()));

        assertThat(CustomerOnboarding.rehydrate(history)).hasValueSatisfying(onboarding -> {
            assertThat(onboarding.status()).isEqualTo(OnboardingStatus.ONBOARDED);
            assertThat(onboarding.version()).isEqualTo(3);
            assertThat(onboarding.uncommittedEvents()).isEmpty();
        });
    }

    @Test
    void emptyHistoryMeansThereIsNoOnboarding() {
        assertThat(CustomerOnboarding.rehydrate(List.of())).isEmpty();
    }

    private static CustomerOnboarding startFor(LocalDate dateOfBirth) {
        Applicant applicant = valueOf(Applicant.of("Jan", "Nowak", "Jan@Example.com", dateOfBirth));
        return CustomerOnboarding.start(CustomerId.newId(), applicant, POLICY, SLA, CLOCK);
    }

    private static CustomerOnboarding escalated() {
        CustomerOnboarding onboarding = committed(startFor(ADULT));
        onboarding.escalateIfScreeningOverdue(AFTER_DEADLINE);
        return committed(onboarding);
    }

    private static CustomerOnboarding committed(CustomerOnboarding onboarding) {
        onboarding.markEventsAsCommitted();
        return onboarding;
    }

    static <T> T valueOf(Result<T, OnboardingError> result) {
        return switch (result) {
            case Result.Success<T, OnboardingError>(var value) -> value;
            case Result.Failure<T, OnboardingError>(var error) -> fail("expected a value but got " + error);
        };
    }

    static <T> OnboardingError failureOf(Result<T, OnboardingError> result) {
        return switch (result) {
            case Result.Failure<T, OnboardingError>(var error) -> error;
            case Result.Success<T, OnboardingError>(var value) -> fail("expected a failure but got " + value);
        };
    }
}
