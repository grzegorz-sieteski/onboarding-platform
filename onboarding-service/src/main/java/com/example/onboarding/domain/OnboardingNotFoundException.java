package com.example.onboarding.domain;

import com.example.onboarding.domain.shared.DomainException;

public class OnboardingNotFoundException extends DomainException {

    public OnboardingNotFoundException(CustomerId customerId) {
        super("No onboarding found for customer " + customerId);
    }
}
