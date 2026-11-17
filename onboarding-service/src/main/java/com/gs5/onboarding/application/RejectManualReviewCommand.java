package com.gs5.onboarding.application;

import com.gs5.onboarding.domain.CustomerId;

public record RejectManualReviewCommand(CustomerId customerId, String reviewer, String reason) {
}
