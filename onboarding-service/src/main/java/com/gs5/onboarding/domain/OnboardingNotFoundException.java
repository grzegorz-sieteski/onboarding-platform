package com.gs5.onboarding.domain;

import com.gs5.onboarding.domain.shared.DomainException;

public class OnboardingNotFoundException extends DomainException {

    public OnboardingNotFoundException(CustomerId customerId) {
        super("No onboarding found for customer " + customerId);
    }
}
