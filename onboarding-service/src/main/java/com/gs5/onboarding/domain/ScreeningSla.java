package com.gs5.onboarding.domain;

import com.gs5.onboarding.domain.shared.InvalidValueException;

import java.time.Duration;
import java.time.Instant;

/** How long compliance screening may take before the case is escalated to a human. */
public record ScreeningSla(Duration timeout) {

    public ScreeningSla {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new InvalidValueException("Screening timeout must be positive, got " + timeout);
        }
    }

    public Instant deadlineFor(Instant screeningRequestedAt) {
        return screeningRequestedAt.plus(timeout);
    }
}
