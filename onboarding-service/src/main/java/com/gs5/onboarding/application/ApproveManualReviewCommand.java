package com.gs5.onboarding.application;

import com.gs5.onboarding.domain.CustomerId;

public record ApproveManualReviewCommand(CustomerId customerId, String reviewer, String comment) {
}
