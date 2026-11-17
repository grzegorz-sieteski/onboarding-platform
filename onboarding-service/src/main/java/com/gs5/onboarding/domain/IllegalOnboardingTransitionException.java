package com.gs5.onboarding.domain;

import com.gs5.onboarding.domain.shared.DomainException;

public class IllegalOnboardingTransitionException extends DomainException {

    public IllegalOnboardingTransitionException(CustomerId customerId, OnboardingStatus from, String action) {
        super("Cannot %s onboarding of customer %s in status %s".formatted(action, customerId, from));
    }
}
