package com.gs5.onboarding.domain;

import com.gs5.onboarding.domain.OnboardingError.InvalidValue;
import com.gs5.onboarding.domain.shared.Result;

import java.util.Locale;
import java.util.regex.Pattern;

public record EmailAddress(String value) {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /** Entry point for untrusted text: validates and normalizes. The constructor is for already valid values. */
    public static Result<EmailAddress, OnboardingError> of(String text) {
        if (text == null || !EMAIL.matcher(text.strip()).matches()) {
            return Result.failure(new InvalidValue("Invalid e-mail address: " + text));
        }
        return Result.success(new EmailAddress(text.strip().toLowerCase(Locale.ROOT)));
    }
}
