package com.example.onboarding.domain;

import com.example.onboarding.domain.shared.InvalidValueException;

import java.util.regex.Pattern;

public record PersonName(String firstName, String lastName) {

    private static final Pattern NAME_PART = Pattern.compile("^\\p{L}[\\p{L} '\\-]{0,99}$");

    public PersonName {
        firstName = requireValidPart(firstName, "First name");
        lastName = requireValidPart(lastName, "Last name");
    }

    private static String requireValidPart(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new InvalidValueException(label + " must not be blank");
        }
        String stripped = value.strip();
        if (!NAME_PART.matcher(stripped).matches()) {
            throw new InvalidValueException(label + " contains illegal characters: " + value);
        }
        return stripped;
    }
}
