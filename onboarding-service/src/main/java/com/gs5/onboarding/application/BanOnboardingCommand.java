package com.gs5.onboarding.application;

import com.gs5.onboarding.domain.CustomerId;

public record BanOnboardingCommand(CustomerId customerId, String reason) {
}
