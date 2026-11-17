package com.example.onboarding.domain;

import com.example.onboarding.domain.shared.InvalidValueException;

import java.util.Locale;
import java.util.regex.Pattern;

public record EmailAddress(String value) {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public EmailAddress {
        if (value == null || !EMAIL.matcher(value.strip()).matches()) {
            throw new InvalidValueException("Invalid e-mail address: " + value);
        }
        value = value.strip().toLowerCase(Locale.ROOT);
    }
}
