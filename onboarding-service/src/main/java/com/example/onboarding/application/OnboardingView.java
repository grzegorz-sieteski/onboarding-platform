package com.example.onboarding.application;

import com.example.onboarding.domain.CustomerOnboarding;

import java.time.Instant;

public record OnboardingView(String customerId, String firstName, String lastName, String status,
                             String statusReason, Instant screeningDueAt, long version) {

    static OnboardingView of(CustomerOnboarding onboarding) {
        return new OnboardingView(
                onboarding.customerId().toString(),
                onboarding.applicant().name().firstName(),
                onboarding.applicant().name().lastName(),
                onboarding.status().name(),
                onboarding.statusReason().orElse(null),
                onboarding.screeningDueAt().orElse(null),
                onboarding.version());
    }
}
