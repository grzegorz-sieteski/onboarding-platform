package com.gs5.onboarding.application;

import java.time.LocalDate;

public record StartOnboardingCommand(String firstName, String lastName, String email, LocalDate dateOfBirth) {
}
