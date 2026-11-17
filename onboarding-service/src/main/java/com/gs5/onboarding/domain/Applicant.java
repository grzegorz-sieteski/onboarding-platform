package com.gs5.onboarding.domain;

import com.gs5.onboarding.domain.shared.InvalidValueException;

import java.time.LocalDate;

/** Data submitted by a person who wants to become a customer. */
public record Applicant(PersonName name, EmailAddress email, LocalDate dateOfBirth) {

    public Applicant {
        if (name == null || email == null || dateOfBirth == null) {
            throw new InvalidValueException("Applicant requires name, e-mail and date of birth");
        }
    }
}
