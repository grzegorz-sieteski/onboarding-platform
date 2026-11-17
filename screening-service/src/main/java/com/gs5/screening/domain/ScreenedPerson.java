package com.gs5.screening.domain;

import com.gs5.screening.domain.shared.InvalidValueException;

/** The Screening context's own model of a person - only what is needed to screen them. */
public record ScreenedPerson(String firstName, Surname surname) {

    public ScreenedPerson {
        if (firstName == null || firstName.isBlank() || surname == null) {
            throw new InvalidValueException("Screened person requires first name and surname");
        }
    }
}
