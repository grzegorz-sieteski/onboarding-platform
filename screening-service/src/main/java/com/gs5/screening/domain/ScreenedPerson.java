package com.gs5.screening.domain;

import com.gs5.screening.domain.ScreeningError.InvalidValue;
import com.gs5.screening.domain.shared.Result;

/** The Screening context's own model of a person - only what is needed to screen them. */
public record ScreenedPerson(String firstName, Surname surname) {

    /** Entry point for untrusted input. The constructor is for already valid values. */
    public static Result<ScreenedPerson, ScreeningError> of(String firstName, String lastName) {
        if (firstName == null || firstName.isBlank()) {
            return Result.failure(new InvalidValue("Screened person requires a first name"));
        }
        return Surname.of(lastName).map(surname -> new ScreenedPerson(firstName, surname));
    }
}
