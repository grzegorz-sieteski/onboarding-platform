package com.example.onboarding.application;

import com.example.onboarding.domain.CustomerId;

public record BanOnboardingCommand(CustomerId customerId, String reason) {
}
