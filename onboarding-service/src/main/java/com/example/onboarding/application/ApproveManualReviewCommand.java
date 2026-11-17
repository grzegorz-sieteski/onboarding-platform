package com.example.onboarding.application;

import com.example.onboarding.domain.CustomerId;

public record ApproveManualReviewCommand(CustomerId customerId, String reviewer, String comment) {
}
