package com.gs5.onboarding.application;

import java.time.Instant;

public record ManualReviewItem(String customerId, String firstName, String lastName, String reason,
                               Instant requestedAt) {
}
