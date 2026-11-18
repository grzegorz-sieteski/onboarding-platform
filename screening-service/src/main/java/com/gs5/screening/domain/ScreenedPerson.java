package com.gs5.screening.domain;

import com.gs5.screening.domain.ScreeningError.InvalidInput;
import com.gs5.screening.domain.ScreeningError.InvalidValue;
import com.gs5.screening.domain.shared.Result;
import com.gs5.screening.domain.shared.Validation;

/** The Screening context's own model of a person - only what is needed to screen them. */
public record ScreenedPerson(String firstName, Surname surname) {

    /** Entry point for untrusted input. The constructor is for already valid values. */
    public static Result<ScreenedPerson, ScreeningError> of(String firstName, String lastName) {
        var validation = Validation.of(InvalidInput::new);
        var validFirstName = validation.check(validFirstName(firstName));
        var surname = validation.check(Surname.of(lastName));
        return validation.build(() -> new ScreenedPerson(validFirstName.value(), surname.value()));
    }

    private static Result<String, ScreeningError> validFirstName(String firstName) {
        if (firstName == null || firstName.isBlank()) {
            return Result.failure(new InvalidValue("Screened person requires a first name"));
        }
        return Result.success(firstName);
    }
}
