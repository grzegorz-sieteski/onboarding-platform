package com.gs5.onboarding.domain;

import com.gs5.onboarding.domain.OnboardingError.InvalidInput;
import com.gs5.onboarding.domain.OnboardingError.InvalidValue;
import com.gs5.onboarding.domain.shared.Result;
import com.gs5.onboarding.domain.shared.Validation;

import java.time.LocalDate;

/** Data submitted by a person who wants to become a customer. */
public record Applicant(PersonName name, EmailAddress email, LocalDate dateOfBirth) {

    /** Entry point for untrusted input. The constructor is for already valid values. */
    public static Result<Applicant, OnboardingError> of(String firstName, String lastName, String email,
                                                        LocalDate dateOfBirth) {
        var validation = Validation.of(InvalidInput::new);
        var name = validation.check(PersonName.of(firstName, lastName));
        var address = validation.check(EmailAddress.of(email));
        var born = validation.check(validDateOfBirth(dateOfBirth));
        return validation.build(() -> new Applicant(name.value(), address.value(), born.value()));
    }

    private static Result<LocalDate, OnboardingError> validDateOfBirth(LocalDate dateOfBirth) {
        if (dateOfBirth == null) {
            return Result.failure(new InvalidValue("Date of birth must not be null"));
        }
        return Result.success(dateOfBirth);
    }
}
