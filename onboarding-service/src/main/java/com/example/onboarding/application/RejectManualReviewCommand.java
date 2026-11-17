package com.example.onboarding.application;

import com.example.onboarding.domain.CustomerId;

public record RejectManualReviewCommand(CustomerId customerId, String reviewer, String reason) {
}
