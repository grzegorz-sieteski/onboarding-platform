package com.example.onboarding.domain;

public record EligibilityDecision(boolean eligible, String rejectionReason) {

    public static EligibilityDecision approved() {
        return new EligibilityDecision(true, null);
    }

    public static EligibilityDecision rejected(String reason) {
        return new EligibilityDecision(false, reason);
    }
}
