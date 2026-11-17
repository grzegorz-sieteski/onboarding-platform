package com.gs5.onboarding.infrastructure.web;

import com.gs5.onboarding.application.StartOnboardingCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

record StartOnboardingRequest(@NotBlank String firstName,
                              @NotBlank String lastName,
                              @NotBlank String email,
                              @NotNull LocalDate dateOfBirth) {

    StartOnboardingCommand toCommand() {
        return new StartOnboardingCommand(firstName, lastName, email, dateOfBirth);
    }
}
