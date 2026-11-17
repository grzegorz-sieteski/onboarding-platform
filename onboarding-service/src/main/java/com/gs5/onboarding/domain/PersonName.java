package com.gs5.onboarding.domain;

import com.gs5.onboarding.domain.OnboardingError.InvalidValue;
import com.gs5.onboarding.domain.shared.Result;

import java.util.regex.Pattern;

public record PersonName(String firstName, String lastName) {

    private static final Pattern NAME_PART = Pattern.compile("^\\p{L}[\\p{L} '\\-]{0,99}$");

    /** Entry point for untrusted text: validates and normalizes. The constructor is for already valid values. */
    public static Result<PersonName, OnboardingError> of(String firstName, String lastName) {
        return validPart(firstName, "First name").flatMap(first ->
                validPart(lastName, "Last name").map(last -> new PersonName(first, last)));
    }

    private static Result<String, OnboardingError> validPart(String text, String label) {
        if (text == null || text.isBlank()) {
            return Result.failure(new InvalidValue(label + " must not be blank"));
        }
        String stripped = text.strip();
        if (!NAME_PART.matcher(stripped).matches()) {
            return Result.failure(new InvalidValue(label + " contains illegal characters: " + text));
        }
        return Result.success(stripped);
    }
}
