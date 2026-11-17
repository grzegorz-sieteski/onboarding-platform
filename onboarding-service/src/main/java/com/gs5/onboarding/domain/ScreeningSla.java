package com.gs5.onboarding.domain;

import com.gs5.onboarding.domain.OnboardingError.InvalidValue;
import com.gs5.onboarding.domain.shared.Result;

import java.time.Duration;
import java.time.Instant;

/** How long compliance screening may take before the case is escalated to a human. */
public record ScreeningSla(Duration timeout) {

    /** Entry point for configuration values. The constructor is for already valid values. */
    public static Result<ScreeningSla, OnboardingError> of(Duration timeout) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            return Result.failure(new InvalidValue("Screening timeout must be positive, got " + timeout));
        }
        return Result.success(new ScreeningSla(timeout));
    }

    public Instant deadlineFor(Instant screeningRequestedAt) {
        return screeningRequestedAt.plus(timeout);
    }
}
