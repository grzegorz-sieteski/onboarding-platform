package com.example.screening.domain;

import com.example.screening.domain.shared.InvalidValueException;

/** The Screening context's own model of a person - only what is needed to screen them. */
public record ScreenedPerson(String firstName, Surname surname) {

    public ScreenedPerson {
        if (firstName == null || firstName.isBlank() || surname == null) {
            throw new InvalidValueException("Screened person requires first name and surname");
        }
    }
}
