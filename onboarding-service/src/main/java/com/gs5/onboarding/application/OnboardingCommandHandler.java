package com.gs5.onboarding.application;

import com.gs5.onboarding.domain.Applicant;
import com.gs5.onboarding.domain.CustomerId;
import com.gs5.onboarding.domain.CustomerOnboarding;
import com.gs5.onboarding.domain.CustomerOnboardingRepository;
import com.gs5.onboarding.domain.EligibilityPolicy;
import com.gs5.onboarding.domain.OnboardingError;
import com.gs5.onboarding.domain.ScreeningSla;
import com.gs5.onboarding.domain.shared.Result;

import java.time.Clock;
import java.util.function.Function;

/**
 * Application service: orchestrates use cases, holds no business rules and knows no framework.
 * <p>
 * Every command runs in one {@link UnitOfWork}. A command that fails - refused by the domain, or a lost concurrency
 * race while saving - writes nothing.
 */
public class OnboardingCommandHandler {

    private final CustomerOnboardingRepository onboardings;
    private final UnitOfWork unitOfWork;
    private final EligibilityPolicy eligibilityPolicy;
    private final ScreeningSla screeningSla;
    private final Clock clock;

    public OnboardingCommandHandler(CustomerOnboardingRepository onboardings, UnitOfWork unitOfWork,
                                    EligibilityPolicy eligibilityPolicy, ScreeningSla screeningSla, Clock clock) {
        this.onboardings = onboardings;
        this.unitOfWork = unitOfWork;
        this.eligibilityPolicy = eligibilityPolicy;
        this.screeningSla = screeningSla;
        this.clock = clock;
    }

    public Result<CustomerId, OnboardingError> handle(StartOnboardingCommand command) {
        return unitOfWork.inTransaction(() ->
                Applicant.of(command.firstName(), command.lastName(), command.email(), command.dateOfBirth())
                        .map(applicant -> CustomerOnboarding.start(CustomerId.newId(), applicant, eligibilityPolicy,
                                screeningSla, clock))
                        .flatMap(onboardings::save)
                        .map(CustomerOnboarding::customerId));
    }

    public Result<CustomerId, OnboardingError> handle(ConfirmScreeningClearedCommand command) {
        return update(command.customerId(), onboarding -> onboarding.confirmScreeningCleared(clock));
    }

    public Result<CustomerId, OnboardingError> handle(EscalateOverdueScreeningCommand command) {
        return update(command.customerId(), onboarding -> onboarding.escalateIfScreeningOverdue(clock));
    }

    public Result<CustomerId, OnboardingError> handle(ApproveManualReviewCommand command) {
        return update(command.customerId(),
                onboarding -> onboarding.approveManualReview(command.reviewer(), command.comment(), clock));
    }

    public Result<CustomerId, OnboardingError> handle(RejectManualReviewCommand command) {
        return update(command.customerId(),
                onboarding -> onboarding.rejectManualReview(command.reviewer(), command.reason(), clock));
    }

    public Result<CustomerId, OnboardingError> handle(BanOnboardingCommand command) {
        return update(command.customerId(), onboarding -> onboarding.ban(command.reason(), clock));
    }

    private Result<CustomerId, OnboardingError> update(
            CustomerId customerId, Function<CustomerOnboarding, Result<CustomerOnboarding, OnboardingError>> change) {
        return unitOfWork.inTransaction(() -> onboardings.load(customerId)
                .flatMap(change)
                .flatMap(onboardings::save)
                .map(CustomerOnboarding::customerId));
    }
}
