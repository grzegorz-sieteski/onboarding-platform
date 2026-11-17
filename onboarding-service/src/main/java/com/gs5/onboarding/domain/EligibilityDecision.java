package com.gs5.onboarding.domain;

/** Verdict of the {@link EligibilityPolicy} - either eligible, or rejected with a reason. */
public sealed interface EligibilityDecision {

    record Eligible() implements EligibilityDecision {
    }

    record Ineligible(String reason) implements EligibilityDecision {
    }
}
