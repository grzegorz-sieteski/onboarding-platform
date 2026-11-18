package com.gs5.onboarding.infrastructure.web;

import com.gs5.onboarding.application.StartOnboardingCommand;

import java.time.LocalDate;

/** Request body; deliberately not validated here - the domain validates it when the command is handled. */
record StartOnboardingRequest(String firstName, String lastName, String email, LocalDate dateOfBirth) {

    StartOnboardingCommand toCommand() {
        return new StartOnboardingCommand(firstName, lastName, email, dateOfBirth);
    }
}
