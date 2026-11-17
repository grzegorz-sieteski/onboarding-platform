package com.example.onboarding.application;

import com.example.onboarding.domain.Applicant;
import com.example.onboarding.domain.CustomerId;
import com.example.onboarding.domain.CustomerOnboarding;
import com.example.onboarding.domain.CustomerOnboardingRepository;
import com.example.onboarding.domain.EligibilityPolicy;
import com.example.onboarding.domain.EmailAddress;
import com.example.onboarding.domain.OnboardingNotFoundException;
import com.example.onboarding.domain.PersonName;
import com.example.onboarding.domain.ScreeningSla;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/** Application service: orchestrates use cases, owns the transaction boundary, holds no business rules. */
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
    public CustomerId handle(StartOnboardingCommand command) {
        var applicant = new Applicant(
                new PersonName(command.firstName(), command.lastName()),
                new EmailAddress(command.email()),
                command.dateOfBirth());
        var onboarding = CustomerOnboarding.start(CustomerId.newId(), applicant, eligibilityPolicy, screeningSla,
                clock);
        onboardings.save(onboarding);
        return onboarding.customerId();
    }

    @Transactional
    public void handle(ConfirmScreeningClearedCommand command) {
        CustomerOnboarding onboarding = load(command.customerId());
        onboarding.confirmScreeningCleared(clock);
        onboardings.save(onboarding);
    }

    @Transactional
    public void handle(EscalateOverdueScreeningCommand command) {
        CustomerOnboarding onboarding = load(command.customerId());
        onboarding.escalateIfScreeningOverdue(clock);
        onboardings.save(onboarding);
    }

    @Transactional
    public void handle(ApproveManualReviewCommand command) {
        CustomerOnboarding onboarding = load(command.customerId());
        onboarding.approveManualReview(command.reviewer(), command.comment(), clock);
        onboardings.save(onboarding);
    }

    @Transactional
    public void handle(RejectManualReviewCommand command) {
        CustomerOnboarding onboarding = load(command.customerId());
        onboarding.rejectManualReview(command.reviewer(), command.reason(), clock);
        onboardings.save(onboarding);
    }

    @Transactional
    public void handle(BanOnboardingCommand command) {
        CustomerOnboarding onboarding = load(command.customerId());
        onboarding.ban(command.reason(), clock);
        onboardings.save(onboarding);
    }

    private CustomerOnboarding load(CustomerId customerId) {
        return onboardings.findById(customerId)
                .orElseThrow(() -> new OnboardingNotFoundException(customerId));
    }
}
