package com.gs5.onboarding.application;

import com.gs5.onboarding.domain.Applicant;
import com.gs5.onboarding.domain.CustomerId;
import com.gs5.onboarding.domain.CustomerOnboarding;
import com.gs5.onboarding.domain.CustomerOnboardingRepository;
import com.gs5.onboarding.domain.EligibilityPolicy;
import com.gs5.onboarding.domain.OnboardingError;
import com.gs5.onboarding.domain.ScreeningSla;
import com.gs5.onboarding.domain.shared.Result;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.time.Clock;
import java.util.function.Function;

/**
 * Application service: orchestrates use cases, owns the transaction boundary, holds no business rules.
 * <p>
 * Every command answers with a {@link Result}. A command rejected by the domain writes nothing. A failed
 * {@code save} may have written part of its work, and a transaction only rolls back on its own for an exception,
 * so a failure is rolled back explicitly in {@link #persist(CustomerOnboarding)}.
 */
@Service
public class OnboardingCommandHandler {

    private final CustomerOnboardingRepository onboardings;
    private final EligibilityPolicy eligibilityPolicy;
    private final ScreeningSla screeningSla;
    private final Clock clock;

    public OnboardingCommandHandler(CustomerOnboardingRepository onboardings, EligibilityPolicy eligibilityPolicy,
                                    ScreeningSla screeningSla, Clock clock) {
        this.onboardings = onboardings;
        this.eligibilityPolicy = eligibilityPolicy;
        this.screeningSla = screeningSla;
        this.clock = clock;
    }

    @Transactional
    public Result<CustomerId, OnboardingError> handle(StartOnboardingCommand command) {
        return Applicant.of(command.firstName(), command.lastName(), command.email(), command.dateOfBirth())
                .map(applicant -> CustomerOnboarding.start(CustomerId.newId(), applicant, eligibilityPolicy,
                        screeningSla, clock))
                .flatMap(this::persist);
    }

    @Transactional
    public Result<CustomerId, OnboardingError> handle(ConfirmScreeningClearedCommand command) {
        return update(command.customerId(), onboarding -> onboarding.confirmScreeningCleared(clock));
    }

    @Transactional
    public Result<CustomerId, OnboardingError> handle(EscalateOverdueScreeningCommand command) {
        return update(command.customerId(), onboarding -> onboarding.escalateIfScreeningOverdue(clock));
    }

    @Transactional
    public Result<CustomerId, OnboardingError> handle(ApproveManualReviewCommand command) {
        return update(command.customerId(),
                onboarding -> onboarding.approveManualReview(command.reviewer(), command.comment(), clock));
    }

    @Transactional
    public Result<CustomerId, OnboardingError> handle(RejectManualReviewCommand command) {
        return update(command.customerId(),
                onboarding -> onboarding.rejectManualReview(command.reviewer(), command.reason(), clock));
    }

    @Transactional
    public Result<CustomerId, OnboardingError> handle(BanOnboardingCommand command) {
        return update(command.customerId(), onboarding -> onboarding.ban(command.reason(), clock));
    }

    private Result<CustomerId, OnboardingError> update(
            CustomerId customerId, Function<CustomerOnboarding, Result<CustomerOnboarding, OnboardingError>> change) {
        return onboardings.load(customerId)
                .flatMap(change)
                .flatMap(this::persist);
    }

    private Result<CustomerId, OnboardingError> persist(CustomerOnboarding onboarding) {
        return switch (onboardings.save(onboarding)) {
            case Result.Success(var saved) -> Result.success(saved.customerId());
            case Result.Failure(var error) -> {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                yield Result.failure(error);
            }
        };
    }
}
