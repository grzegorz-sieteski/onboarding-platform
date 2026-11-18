package com.gs5.onboarding.application;

import com.gs5.onboarding.domain.CustomerId;
import com.gs5.onboarding.domain.CustomerOnboarding;
import com.gs5.onboarding.domain.CustomerOnboardingRepository;
import com.gs5.onboarding.domain.EligibilityPolicy;
import com.gs5.onboarding.domain.OnboardingError;
import com.gs5.onboarding.domain.OnboardingError.ConcurrentModification;
import com.gs5.onboarding.domain.OnboardingError.IllegalTransition;
import com.gs5.onboarding.domain.OnboardingError.InvalidValue;
import com.gs5.onboarding.domain.OnboardingError.OnboardingNotFound;
import com.gs5.onboarding.domain.OnboardingEvent;
import com.gs5.onboarding.domain.OnboardingStatus;
import com.gs5.onboarding.domain.ScreeningSla;
import com.gs5.onboarding.domain.shared.Result;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/** The use cases run without Spring, without a database and without Kafka - plain objects and two fakes. */
class OnboardingCommandHandlerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate ADULT = LocalDate.parse("1990-05-01");

    private final InMemoryOnboardings onboardings = new InMemoryOnboardings();
    private final RecordingUnitOfWork unitOfWork = new RecordingUnitOfWork();
    private final OnboardingCommandHandler commands = new OnboardingCommandHandler(onboardings, unitOfWork,
            new EligibilityPolicy(), new ScreeningSla(Duration.ofMinutes(15)), CLOCK);

    @Test
    void startsAnOnboardingInOneUnitOfWork() {
        CustomerId id = valueOf(commands.handle(start("Jan", "jan@example.com")));

        assertThat(statusOf(id)).isEqualTo(OnboardingStatus.PENDING_SCREENING);
        assertThat(unitOfWork.committed).isEqualTo(1);
        assertThat(unitOfWork.rolledBack).isZero();
    }

    @Test
    void invalidInputIsRefusedAndNothingIsSaved() {
        Result<CustomerId, OnboardingError> result = commands.handle(start("Jan", "not-an-email"));

        assertThat(failureOf(result)).isInstanceOf(InvalidValue.class);
        assertThat(onboardings.saves).isZero();
        assertThat(unitOfWork.rolledBack).isEqualTo(1);
    }

    @Test
    void commandRefusedByTheDomainIsRolledBack() {
        CustomerId id = valueOf(commands.handle(start("Jan", "jan@example.com")));
        int savesBefore = onboardings.saves;

        Result<CustomerId, OnboardingError> result =
                commands.handle(new ApproveManualReviewCommand(id, "officer.anna", "ok"));

        assertThat(failureOf(result)).isEqualTo(new IllegalTransition(id, OnboardingStatus.PENDING_SCREENING, "approve"));
        assertThat(onboardings.saves).isEqualTo(savesBefore);
        assertThat(unitOfWork.rolledBack).isEqualTo(1);
    }

    @Test
    void lostRaceOnSaveIsAFailureAndIsRolledBack() {
        CustomerId id = valueOf(commands.handle(start("Jan", "jan@example.com")));
        onboardings.conflictOnNextSave = true;

        Result<CustomerId, OnboardingError> result = commands.handle(new BanOnboardingCommand(id, "Fraud"));

        assertThat(failureOf(result)).isEqualTo(new ConcurrentModification(id, 2));
        assertThat(statusOf(id)).isEqualTo(OnboardingStatus.PENDING_SCREENING);
        assertThat(unitOfWork.rolledBack).isEqualTo(1);
    }

    @Test
    void unknownCustomerIsNotFound() {
        CustomerId unknown = CustomerId.newId();

        assertThat(failureOf(commands.handle(new BanOnboardingCommand(unknown, "Fraud"))))
                .isEqualTo(new OnboardingNotFound(unknown));
    }

    private static StartOnboardingCommand start(String firstName, String email) {
        return new StartOnboardingCommand(firstName, "Nowak", email, ADULT);
    }

    private OnboardingStatus statusOf(CustomerId id) {
        return valueOf(onboardings.load(id)).status();
    }

    private static <T> T valueOf(Result<T, OnboardingError> result) {
        return switch (result) {
            case Result.Success<T, OnboardingError>(var value) -> value;
            case Result.Failure<T, OnboardingError>(var error) -> fail("expected a value but got " + error);
        };
    }

    private static <T> OnboardingError failureOf(Result<T, OnboardingError> result) {
        return switch (result) {
            case Result.Failure<T, OnboardingError>(var error) -> error;
            case Result.Success<T, OnboardingError>(var value) -> fail("expected a failure but got " + value);
        };
    }

    /** The contract of the real unit of work without a database: a failure is rolled back, a success committed. */
    private static final class RecordingUnitOfWork implements UnitOfWork {

        private int committed;
        private int rolledBack;

        @Override
        public <T, E> Result<T, E> inTransaction(Supplier<Result<T, E>> work) {
            Result<T, E> result = work.get();
            if (result instanceof Result.Failure<T, E>) {
                rolledBack++;
            } else {
                committed++;
            }
            return result;
        }

        @Override
        public <T> T readOnly(Supplier<T> query) {
            return query.get();
        }
    }

    /** An event-sourced repository kept in a map; can be told to lose the next optimistic concurrency race. */
    private static final class InMemoryOnboardings implements CustomerOnboardingRepository {

        private final Map<CustomerId, List<OnboardingEvent>> streams = new HashMap<>();
        private boolean conflictOnNextSave;
        private int saves;

        @Override
        public Result<CustomerOnboarding, OnboardingError> load(CustomerId customerId) {
            return Result.of(CustomerOnboarding.rehydrate(streams.getOrDefault(customerId, List.of())),
                    () -> new OnboardingNotFound(customerId));
        }

        @Override
        public Result<CustomerOnboarding, OnboardingError> save(CustomerOnboarding onboarding) {
            if (conflictOnNextSave) {
                conflictOnNextSave = false;
                return Result.failure(new ConcurrentModification(onboarding.customerId(), onboarding.version()));
            }
            streams.computeIfAbsent(onboarding.customerId(), _ -> new ArrayList<>())
                    .addAll(onboarding.uncommittedEvents());
            onboarding.markEventsAsCommitted();
            saves++;
            return Result.success(onboarding);
        }
    }
}
